package com.felix.aigate.runtime;

import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-T08 Runtime Invoke 端到端集成测试：真实穿越
 * Controller -> Security(Bearer API Key) -> Testcontainers MySQL -> ModelProxyService ->
 * RestClient -> 本地 JDK HttpServer Mock Provider，证明 Happy Path 返回真实（非硬编码）模型响应，
 * 并验证 Credential 有/无决定上游 Authorization、参数校验与业务配置异常均不出站。
 *
 * <p>不访问任何真实模型服务；Mock Provider 只在测试 JVM 内监听随机端口。
 */
class RuntimeInvokeIntegrationTest extends IntegrationTestBase {

    private static final String PROVIDER_RESPONSE = """
            {"model":"gpt-compatible-model",
             "id":"chatcmpl-123","created":1700000000,
             "usage":{"prompt_tokens":9,"completion_tokens":12,"total_tokens":21},
             "choices":[{"index":0,"message":{"role":"assistant","content":"HashMap is a hash table."},"finish_reason":"stop"}]}
            """;

    private static HttpServer mockProvider;
    private static String providerBaseUrl;

    private static final java.util.concurrent.atomic.AtomicInteger requestCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> lastPath =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<String> lastContentType =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<String> lastAuthorization =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<String> lastBody =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeAll
    static void startMockProvider() throws IOException {
        mockProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockProvider.createContext("/", RuntimeInvokeIntegrationTest::handle);
        mockProvider.start();
        providerBaseUrl = "http://localhost:" + mockProvider.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopMockProvider() {
        mockProvider.stop(0);
    }

    @BeforeEach
    void resetMockProviderState() {
        requestCount.set(0);
        lastPath.set(null);
        lastContentType.set(null);
        lastAuthorization.set(null);
        lastBody.set(null);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastPath.set(exchange.getRequestURI().getPath());
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] response = PROVIDER_RESPONSE.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.getResponseBody().close();
    }

    @Test
    @DisplayName("ACTIVE API Key + 默认 Deployment -> 真实出站 Mock Provider -> 200 InvokeResponse")
    void happyPathShouldReturnRealProviderResponse() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-happy", "invoke-happy-model", "gpt-compatible-model", true, null);

        String body = """
                {"messages":[{"role":"user","content":"Explain HashMap."}],"temperature":0.7,"maxTokens":500}
                """;

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("HashMap is a hash table."))
                .andExpect(jsonPath("$.model").value("gpt-compatible-model"))
                .andExpect(jsonPath("$.finishReason").value("stop"))
                // 不得泄漏 usage/id 等上游额外字段
                .andExpect(jsonPath("$.usage").doesNotExist())
                .andExpect(jsonPath("$.cost").doesNotExist());

        // 证明真实出站：命中 /v1/chat/completions，请求体使用 Deployment 的 remoteModelName。
        assertEquals(1, requestCount.get());
        assertEquals("/v1/chat/completions", lastPath.get());
        assertTrue(lastContentType.get().contains("application/json"));
        // 无 Credential -> 上游不携带 Authorization，且绝不转发客户端 AIGate API Key。
        assertNull(lastAuthorization.get());

        JsonNode sent = jsonMapper.readTree(lastBody.get());
        assertEquals("gpt-compatible-model", sent.get("model").asString());
        assertEquals("Explain HashMap.", sent.get("messages").get(0).get("content").asString());
        assertEquals("user", sent.get("messages").get(0).get("role").asString());
        assertEquals(500, sent.get("max_tokens").asInt());
        assertTrue(sent.get("temperature").asDouble() > 0.69 && sent.get("temperature").asDouble() < 0.71);
    }

    @Test
    @DisplayName("配置 Credential 的 Deployment -> 上游收到独立 Provider Bearer，非客户端 API Key")
    void credentialShouldBecomeProviderBearerHeader() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-cred", "invoke-cred-model", "gpt-compatible-model", true, "sk-provider-secret");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isOk());

        assertEquals(1, requestCount.get());
        assertEquals("Bearer sk-provider-secret", lastAuthorization.get());
        // 客户端 AIGate API Key 绝不转发给 Provider。
        assertTrue(!lastAuthorization.get().contains(apiKey));
    }

    @Test
    @DisplayName("无 API Key 调用 /v1/invoke -> 401 UNAUTHORIZED，且不出站")
    void missingApiKeyShouldReturn401AndNotCallUpstream() throws Exception {

        mockMvc.perform(post("/v1/invoke")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("参数校验失败 -> 400 统一错误，且不出站")
    void invalidRequestsShouldReturn400AndNotCallUpstream() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-invalid", "invoke-invalid-model", "gpt-compatible-model", true, null);

        String[] invalidBodies = {
                // messages 为空
                """
                        {"messages":[]}
                        """,
                // message.content 空白
                """
                        {"messages":[{"role":"user","content":"   "}]}
                        """,
                // temperature 越界
                """
                        {"messages":[{"role":"user","content":"hi"}],"temperature":2.5}
                        """,
                // maxTokens <= 0
                """
                        {"messages":[{"role":"user","content":"hi"}],"maxTokens":0}
                        """,
        };

        for (String invalid : invalidBodies) {
            mockMvc.perform(post("/v1/invoke")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                            .contentType(APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        // 非法 role -> Jackson 无法反序列化 -> 400 INVALID_REQUEST（仍属统一 400 错误响应）
        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"wizard","content":"hi"}]}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("Application 未绑定默认 Deployment -> 失败且绝不出站")
    void missingDefaultDeploymentShouldNotCallUpstream() throws Exception {

        long teamId = createTeam("invoke-nodefault-team");
        long appId = createApplication("invoke-nodefault-app", teamId, null);
        String apiKey = createApiKey(appId, "invoke-nodefault-key");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                // P2-T08 未做正式错误码映射（属 P2-T09），此处只要求明确失败、非 2xx
                .andExpect(status().is5xxServerError());

        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("默认 Deployment disabled -> 失败且绝不出站")
    void disabledDeploymentShouldNotCallUpstream() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-disabled", "invoke-disabled-model", "gpt-compatible-model", false, null);

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().is5xxServerError());

        assertEquals(0, requestCount.get());
    }

    // ---- helpers ----

    /** 建立 Provider/Model/Deployment/Team/Application/ApiKey 全链路，返回可用 API Key 明文。 */
    private String setupApplicationWithKeyAndDeployment(
            String prefix, String modelName, String remoteModelName,
            boolean enabled, String credential) throws Exception {

        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(modelName);
        long deploymentId = createDeployment(
                prefix + "-deployment", providerId, modelId, remoteModelName, enabled, credential);

        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);

        return createApiKey(appId, prefix + "-key");
    }

    private long createProvider(String name) throws Exception {

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName(name);
        request.setType(ProviderType.OPENAI_COMPATIBLE);

        String body = mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createModel(String name) throws Exception {

        CreateModelRequest request = new CreateModelRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createDeployment(String name, long providerId, long modelId,
                                  String remoteModelName, boolean enabled, String credential) throws Exception {

        CreateModelDeploymentRequest request = new CreateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl(providerBaseUrl);
        request.setRemoteModelName(remoteModelName);
        request.setEnabled(enabled);
        request.setCredential(credential);

        String body = mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createTeam(String name) throws Exception {

        CreateTeamRequest request = new CreateTeamRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createApplication(String name, long teamId, Long defaultDeploymentId) throws Exception {

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(defaultDeploymentId);

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private String createApiKey(long applicationId, String name) throws Exception {

        CreateApplicationApiKeyRequest request = new CreateApplicationApiKeyRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/applications/" + applicationId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("apiKey").asText();
    }
}

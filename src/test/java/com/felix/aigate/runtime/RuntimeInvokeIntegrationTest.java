package com.felix.aigate.runtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.application.dto.request.UpdateApplicationRequest;
import com.felix.aigate.common.exception.GlobalExceptionHandler;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.deployment.dto.request.UpdateModelDeploymentRequest;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-T10 Runtime Invoke 端到端集成测试：真实穿越
 * Controller -> Security(Bearer API Key) -> Testcontainers MySQL -> ModelProxyService ->
 * RestClient -> 本地 JDK HttpServer Mock Provider，覆盖：
 * API Key 生命周期（ACTIVE/REVOKED/新建 Key，经真实管理 API 操作）；
 * 默认 Deployment 动态变更（PUT 切换到新 Endpoint / 禁用 / 解绑 -> 409 零出站）；
 * 成功链路不回归；Provider 上游状态码（400/401/429/500）映射为 AIGate 语义（502/503）而非透传；
 * HTTP 200 但结构非法 -> 502 PROVIDER_INVALID_RESPONSE；连接失败 -> 502 PROVIDER_CONNECTION_ERROR；
 * 客户端认证 401 与参数校验 400 不受影响；对外响应与应用日志均不泄露敏感标记。
 * 读取超时的全链路 E2E 在 RuntimeInvokeTimeoutIntegrationTest 单独验证（小超时上下文）。
 * 不访问任何真实模型服务。
 */
class RuntimeInvokeIntegrationTest extends IntegrationTestBase {

    private static final String VALID_PROVIDER_BODY = """
            {"model":"gpt-compatible-model",
             "id":"chatcmpl-123","created":1700000000,
             "usage":{"prompt_tokens":9,"completion_tokens":12,"total_tokens":21},
             "choices":[{"index":0,"message":{"role":"assistant","content":"HashMap is a hash table."},"finish_reason":"stop"}]}
            """;

    /** 最小合法 Runtime 调用体；参数校验专用测试单独使用非法体。 */
    private static final String INVOKE_BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    private static HttpServer mockProvider;
    private static String providerBaseUrl;
    private static String providerBaseUrlB;

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
    private static final java.util.concurrent.atomic.AtomicReference<String> responseStatus =
            new java.util.concurrent.atomic.AtomicReference<>("200");
    private static final java.util.concurrent.atomic.AtomicReference<String> responseBody =
            new java.util.concurrent.atomic.AtomicReference<>(VALID_PROVIDER_BODY);
    /** 仅挂在 /v1b/* 上：Deployment B 命中即证明出站目标真实切换，而非仅模型名变化。 */
    private static final java.util.concurrent.atomic.AtomicInteger providerBHits =
            new java.util.concurrent.atomic.AtomicInteger();

    @BeforeAll
    static void startMockProvider() throws IOException {
        mockProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockProvider.createContext("/", RuntimeInvokeIntegrationTest::handle);
        // Deployment B 专用上下文：只有 endpoint 指向 /v1b 的请求才会计入 providerBHits。
        mockProvider.createContext("/v1b", exchange -> {
            providerBHits.incrementAndGet();
            handle(exchange);
        });
        mockProvider.start();
        providerBaseUrl = "http://localhost:" + mockProvider.getAddress().getPort() + "/v1";
        providerBaseUrlB = "http://localhost:" + mockProvider.getAddress().getPort() + "/v1b";
    }

    @AfterAll
    static void stopMockProvider() {
        mockProvider.stop(0);
    }

    @BeforeEach
    void resetMockProviderState() {
        requestCount.set(0);
        providerBHits.set(0);
        lastPath.set(null);
        lastContentType.set(null);
        lastAuthorization.set(null);
        lastBody.set(null);
        responseStatus.set("200");
        responseBody.set(VALID_PROVIDER_BODY);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastPath.set(exchange.getRequestURI().getPath());
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(Integer.parseInt(responseStatus.get()), response.length);
        exchange.getResponseBody().write(response);
        exchange.getResponseBody().close();
    }

    // ---- 成功回归 ----

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
                .andExpect(jsonPath("$.usage").doesNotExist())
                .andExpect(jsonPath("$.cost").doesNotExist());

        assertEquals(1, requestCount.get());
        assertEquals("/v1/chat/completions", lastPath.get());
        assertTrue(lastContentType.get().contains("application/json"));
        assertNull(lastAuthorization.get());

        JsonNode sent = jsonMapper.readTree(lastBody.get());
        assertEquals("gpt-compatible-model", sent.get("model").asString());
        assertEquals(500, sent.get("max_tokens").asInt());
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
        assertTrue(!lastAuthorization.get().contains(apiKey));
    }

    // ---- 客户端认证与参数校验：不受 Provider 映射影响 ----

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
                """
                        {"messages":[]}
                        """,
                """
                        {"messages":[{"role":"user","content":"   "}]}
                        """,
                """
                        {"messages":[{"role":"user","content":"hi"}],"temperature":2.5}
                        """,
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

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"wizard","content":"hi"}]}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, requestCount.get());
    }

    // ---- 上游 Provider 状态码 -> AIGate 语义映射（非透传）----

    @Test
    @DisplayName("Provider 401 -> 客户端 502 PROVIDER_AUTH_FAILED（绝不与 AIGate 认证 401 混淆）")
    void providerAuthFailureMapsToBadGateway() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-authfail", "invoke-authfail-model", "gpt-compatible-model", true, "sk-provider-secret");

        responseStatus.set("401");
        responseBody.set("""
                {"error":{"message":"private-provider-url-marker sensitive-upstream-body-marker test-provider-secret"}}
                """);

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"))
                .andReturn().getResponse().getContentAsString();

        // 一次调用只发一次请求，不重试。
        assertEquals(1, requestCount.get());
        assertNoSensitiveLeak(content);
    }

    @Test
    @DisplayName("Provider 429 -> 503 PROVIDER_RATE_LIMITED（不被误认为 AIGate 自身配额错误）")
    void providerRateLimitedMapsToServiceUnavailable() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-ratelimit", "invoke-ratelimit-model", "gpt-compatible-model", true, null);

        responseStatus.set("429");
        responseBody.set("{\"error\":\"sensitive-upstream-body-marker\"}");

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_RATE_LIMITED"))
                .andReturn().getResponse().getContentAsString();

        // 绝不因 429 自动重试。
        assertEquals(1, requestCount.get());
        assertNoSensitiveLeak(content);
    }

    @Test
    @DisplayName("Provider 500 -> 502 PROVIDER_UNAVAILABLE")
    void providerServerErrorMapsToBadGateway() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-servererr", "invoke-servererr-model", "gpt-compatible-model", true, null);

        responseStatus.set("500");
        responseBody.set("{\"error\":\"sensitive-upstream-body-marker\"}");

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, requestCount.get());
        assertNoSensitiveLeak(content);
    }

    // ---- 上游 200 但结构非法 ----

    @Test
    @DisplayName("Provider HTTP 200 但 choices 为空 -> 502 PROVIDER_INVALID_RESPONSE，不返回 500/假成功")
    void providerInvalidStructureMapsToBadGateway() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-invalidresp", "invoke-invalidresp-model", "gpt-compatible-model", true, null);

        responseStatus.set("200");
        responseBody.set("{\"model\":\"gpt-compatible-model\",\"choices\":[]}");

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_INVALID_RESPONSE"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, requestCount.get());
        assertTrue(!content.contains("INTERNAL_SERVER_ERROR"));
    }

    // ---- 连接失败 ----

    @Test
    @DisplayName("默认 Deployment 指向无监听端口 -> 502 PROVIDER_CONNECTION_ERROR，且未触达 Mock Provider")
    void connectionRefusedMapsToConnectionError() throws Exception {

        String deadBaseUrl;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadBaseUrl = "http://localhost:" + probe.getLocalPort() + "/v1";
        }

        long providerId = createProvider("invoke-connrefused-provider");
        long modelId = createModel("invoke-connrefused-model");
        long deploymentId = createDeployment(
                "invoke-connrefused-deployment", providerId, modelId, "gpt-compatible-model", true, null, deadBaseUrl);
        long teamId = createTeam("invoke-connrefused-team");
        long appId = createApplication("invoke-connrefused-app", teamId, deploymentId);
        String apiKey = createApiKey(appId, "invoke-connrefused-key");

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_CONNECTION_ERROR"))
                .andReturn().getResponse().getContentAsString();

        // 目标端口无监听：绝不应打到共享 Mock Provider。
        assertEquals(0, requestCount.get());
        // 内部 URL（deadBaseUrl）不得出现在对外响应里。
        assertTrue(!content.contains(deadBaseUrl));
    }

    // ---- 本地配置错误：409 且零出站 ----

    @Test
    @DisplayName("Application 未绑定默认 Deployment -> 409 MODEL_DEPLOYMENT_NOT_CONFIGURED，绝不出站")
    void missingDefaultDeploymentShouldReturn409AndNotCallUpstream() throws Exception {

        long teamId = createTeam("invoke-nodefault-team");
        long appId = createApplication("invoke-nodefault-app", teamId, null);
        String apiKey = createApiKey(appId, "invoke-nodefault-key");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_NOT_CONFIGURED"));

        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("默认 Deployment disabled -> 409 MODEL_DEPLOYMENT_DISABLED，绝不出站")
    void disabledDeploymentShouldReturn409AndNotCallUpstream() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-disabled", "invoke-disabled-model", "gpt-compatible-model", false, null);

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_DISABLED"));

        assertEquals(0, requestCount.get());
    }

    // ---- API Key 生命周期 E2E（全部经真实管理 API 操作，不直接伪造数据库状态）----

    @Test
    @DisplayName("撤销 Key 后 Runtime 调用 -> 401 且零出站")
    void revokedApiKeyShouldReturn401AndNotCallUpstream() throws Exception {

        String prefix = "invoke-revoke";
        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(prefix + "-model");
        long deploymentId = createDeployment(prefix + "-deployment", providerId, modelId,
                "gpt-compatible-model", true, null, providerBaseUrl);
        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);

        JsonNode createdKey = createApiKeyReturningJson(appId, prefix + "-key");
        String apiKey = createdKey.get("apiKey").asText();
        long apiKeyRowId = createdKey.get("id").asLong();

        // 先证明 Key 可用。
        invokeOk(apiKey);
        assertEquals(1, requestCount.get());

        // 真实管理 API 撤销。
        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys/" + apiKeyRowId + "/revoke")))
                .andExpect(status().isOk());

        requestCount.set(0);
        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 认证失败绝不能产生出站请求。
        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("新建 Key 可用；项目语义为并存不自动轮换，旧 ACTIVE Key 仍可用")
    void newlyCreatedApiKeyWorksAndExistingActiveKeyIsNotAutoRevoked() throws Exception {

        String prefix = "invoke-twokeys";
        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(prefix + "-model");
        long deploymentId = createDeployment(prefix + "-deployment", providerId, modelId,
                "gpt-compatible-model", true, null, providerBaseUrl);
        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);

        String firstKey = createApiKey(appId, prefix + "-key-1");
        String secondKey = createApiKey(appId, prefix + "-key-2");
        assertTrue(!firstKey.equals(secondKey));

        // 项目 Key 生命周期：创建新 Key 不自动撤销旧 Key，两者均为 ACTIVE 并可完成模型调用。
        invokeOk(firstKey);
        invokeOk(secondKey);
        assertEquals(2, requestCount.get());
    }

    // ---- 默认 Deployment 动态变更 E2E（真实管理 API PUT）----

    @Test
    @DisplayName("PUT 切换默认 Deployment -> 真实切换到新 Endpoint 与新 remoteModelName")
    void switchingDefaultDeploymentChangesActualEndpoint() throws Exception {

        String prefix = "invoke-switch";
        long providerA = createProvider(prefix + "-provider-a");
        long modelA = createModel(prefix + "-logical-a");
        long deploymentA = createDeployment(prefix + "-deployment-a", providerA, modelA,
                "model-a", true, null, providerBaseUrl);
        long providerB = createProvider(prefix + "-provider-b");
        long modelB = createModel(prefix + "-logical-b");
        long deploymentB = createDeployment(prefix + "-deployment-b", providerB, modelB,
                "model-b", true, null, providerBaseUrlB);

        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentA);
        String apiKey = createApiKey(appId, prefix + "-key");

        // 首次：命中 Deployment A（path 前缀 /v1，B 专用计数器不动）。
        mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isOk());
        assertEquals(1, requestCount.get());
        assertEquals(0, providerBHits.get());
        assertEquals("model-a", jsonMapper.readTree(lastBody.get()).get("model").asString());

        // 管理 API 切换默认 Deployment。
        updateApplicationDefaultDeployment(appId, prefix + "-app", teamId, deploymentB);

        // 第二次：必须出站到一个此前从未被命中的目标上下文（/v1b），证明切换的是真实 Endpoint。
        requestCount.set(0);
        mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isOk());
        assertEquals(1, providerBHits.get());
        assertEquals("model-b", jsonMapper.readTree(lastBody.get()).get("model").asString());
    }

    @Test
    @DisplayName("PUT 解绑默认 Deployment -> 409 且零出站")
    void unbindingDefaultDeploymentViaPutShouldReturn409ZeroEgress() throws Exception {

        String prefix = "invoke-unbind";
        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(prefix + "-model");
        long deploymentId = createDeployment(prefix + "-deployment", providerId, modelId,
                "gpt-compatible-model", true, null, providerBaseUrl);
        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);
        String apiKey = createApiKey(appId, prefix + "-key");

        invokeOk(apiKey);

        updateApplicationDefaultDeployment(appId, prefix + "-app", teamId, null);

        requestCount.set(0);
        mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_NOT_CONFIGURED"));
        assertEquals(0, requestCount.get());
    }

    @Test
    @DisplayName("PUT 禁用默认 Deployment -> 409 且零出站")
    void disablingDefaultDeploymentViaPutShouldReturn409ZeroEgress() throws Exception {

        String prefix = "invoke-disable";
        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(prefix + "-model");
        long deploymentId = createDeployment(prefix + "-deployment", providerId, modelId,
                "gpt-compatible-model", true, null, providerBaseUrl);
        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);
        String apiKey = createApiKey(appId, prefix + "-key");

        invokeOk(apiKey);

        disableDeployment(deploymentId, prefix + "-deployment", providerId, modelId,
                "gpt-compatible-model", providerBaseUrl);

        requestCount.set(0);
        mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_DISABLED"));
        assertEquals(0, requestCount.get());
    }

    // ---- Provider 错误补充与日志防泄露 ----

    @Test
    @DisplayName("Provider 400 -> 502 PROVIDER_BAD_REQUEST，不泄露上游错误 Body")
    void providerBadRequestMapsToBadGateway() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-badreq", "invoke-badreq-model", "gpt-compatible-model", true, null);

        responseStatus.set("400");
        responseBody.set("{\"error\":\"sensitive-upstream-body-marker\"}");

        String content = mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_BAD_REQUEST"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, requestCount.get());
        assertNoSensitiveLeak(content);
    }

    @Test
    @DisplayName("Provider 失败时应用日志只含安全错误码，不含任何敏感标记/Key/URL")
    void errorLogsDoNotContainSensitiveData() throws Exception {

        String apiKey = setupApplicationWithKeyAndDeployment(
                "invoke-logsafe", "invoke-logsafe-model", "gpt-compatible-model", true, "sk-provider-secret");

        responseStatus.set("500");
        responseBody.set("""
                {"error":{"message":"sensitive-upstream-body-marker private-provider-url-marker test-provider-secret"}}
                """);

        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            mockMvc.perform(invokeRequest(apiKey))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
        } finally {
            handlerLogger.detachAppender(appender);
            appender.stop();
        }

        assertFalse(appender.list.isEmpty());
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN && event.getFormattedMessage().contains("PROVIDER_UNAVAILABLE")));
        // 错误日志逐条检查：不含上游 Body / 内部 URL / Provider Credential / 客户端 API Key。
        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            assertTrue(message != null);
            assertFalse(message.contains("sensitive-upstream-body-marker"));
            assertFalse(message.contains("private-provider-url-marker"));
            assertFalse(message.contains("test-provider-secret"));
            assertFalse(message.contains("sk-provider-secret"));
            assertFalse(message.contains(apiKey));
            assertFalse(message.contains(providerBaseUrl));
        }
    }

    // ---- helpers ----

    /** 构一个带 Bearer API Key 的合法 /v1/invoke 请求。 */
    private MockHttpServletRequestBuilder invokeRequest(String apiKey) {
        return post("/v1/invoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(APPLICATION_JSON)
                .content(INVOKE_BODY);
    }

    /** 断言调用 200（证明 Key 当前可用）。 */
    private void invokeOk(String apiKey) throws Exception {
        mockMvc.perform(invokeRequest(apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("HashMap is a hash table."));
    }

    /** 创建 Key 并返回完整响应 JSON（含明文 apiKey 与行 id）。 */
    private JsonNode createApiKeyReturningJson(long applicationId, String name) throws Exception {

        CreateApplicationApiKeyRequest request = new CreateApplicationApiKeyRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/applications/" + applicationId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body);
    }

    /** 通过管理 API PUT 修改 Application 的默认 Deployment（null = 解绑）。 */
    private void updateApplicationDefaultDeployment(long appId, String name, long teamId, Long deploymentId)
            throws Exception {

        UpdateApplicationRequest request = new UpdateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(deploymentId);

        mockMvc.perform(authed(put("/api/applications/" + appId))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());
    }

    /** 通过管理 API PUT 将 Deployment 置为 enabled=false（全量更新，需回传其他必填字段）。 */
    private void disableDeployment(long deploymentId, String name, long providerId, long modelId,
                                   String remoteModelName, String endpointUrl) throws Exception {

        UpdateModelDeploymentRequest request = new UpdateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl(endpointUrl);
        request.setRemoteModelName(remoteModelName);
        request.setEnabled(false);

        mockMvc.perform(authed(put("/api/model-deployments/" + deploymentId))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());
    }

    /** 对外错误响应不得包含上游敏感标记或 Provider 内部 URL。 */
    private void assertNoSensitiveLeak(String responseContent) {
        assertTrue(!responseContent.contains("sensitive-upstream-body-marker"));
        assertTrue(!responseContent.contains("private-provider-url-marker"));
        assertTrue(!responseContent.contains("test-provider-secret"));
        assertTrue(!responseContent.contains("sk-provider-secret"));
        assertTrue(!responseContent.contains(providerBaseUrl));
    }

    private String setupApplicationWithKeyAndDeployment(
            String prefix, String modelName, String remoteModelName,
            boolean enabled, String credential) throws Exception {

        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(modelName);
        long deploymentId = createDeployment(
                prefix + "-deployment", providerId, modelId, remoteModelName, enabled, credential, providerBaseUrl);

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
                                  String remoteModelName, boolean enabled, String credential,
                                  String endpointUrl) throws Exception {

        CreateModelDeploymentRequest request = new CreateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl(endpointUrl);
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

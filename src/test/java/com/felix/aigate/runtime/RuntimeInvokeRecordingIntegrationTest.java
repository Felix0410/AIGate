package com.felix.aigate.runtime;

import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.support.IntegrationTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-T03 Successful Invocation Recording 集成测试。
 *
 * <p>验证每次成功 Invoke 自动写入 SUCCESS 记录，以及失败/认证错误场景不产生记录。
 * 复用 IntegrationTestBase / Testcontainers MySQL / MockMvc / 本地 Mock Provider。
 */
class RuntimeInvokeRecordingIntegrationTest extends IntegrationTestBase {

    private static final String VALID_PROVIDER_BODY = """
            {"model":"gpt-compatible-model",
             "id":"chatcmpl-123","created":1700000000,
             "usage":{"prompt_tokens":9,"completion_tokens":12,"total_tokens":21},
             "choices":[{"index":0,"message":{"role":"assistant","content":"HashMap is a hash table."},"finish_reason":"stop"}]}
            """;

    private static final String INVOKE_BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    private static HttpServer mockProvider;
    private static String providerBaseUrl;

    private static final AtomicInteger requestCount = new AtomicInteger();
    private static final AtomicReference<String> responseStatus = new AtomicReference<>("200");
    private static final AtomicReference<String> responseBody = new AtomicReference<>(VALID_PROVIDER_BODY);

    @Autowired
    private InvocationRecordMapper invocationRecordMapper;

    @BeforeAll
    static void startMockProvider() throws IOException {
        mockProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockProvider.createContext("/", RuntimeInvokeRecordingIntegrationTest::handle);
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
        responseStatus.set("200");
        responseBody.set(VALID_PROVIDER_BODY);
    }

    @AfterEach
    void tearDown() {
        responseStatus.set("200");
        responseBody.set(VALID_PROVIDER_BODY);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(Integer.parseInt(responseStatus.get()), response.length);
        exchange.getResponseBody().write(response);
        exchange.getResponseBody().close();
    }

    // ---- Test A: 真实成功调用写一条 SUCCESS 记录 ----

    @Test
    @DisplayName("成功 Invoke 后写入一条 SUCCESS 记录，含 applicationId/durationMs/createdAt")
    void successfulInvocationShouldWriteOneSuccessRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-success", "rec-success-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("HashMap is a hash table."));

        assertEquals(1, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));

        assertEquals(1, records.size());

        InvocationRecord record = records.get(0);
        assertEquals(appId, record.getApplicationId());
        assertEquals(InvocationStatus.SUCCESS, record.getStatus());
        assertNull(record.getErrorCode());
        assertTrue(record.getDurationMs() >= 0);
        assertNotNull(record.getCreatedAt());
    }

    // ---- Test B: 连续两次调用产生两条不同记录 ----

    @Test
    @DisplayName("连续两次成功调用应产生两条不同 ID 的 SUCCESS 记录，Provider 收到两次请求")
    void consecutiveInvocationsShouldCreateTwoDistinctRecords() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-twice", "rec-twice-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isOk());

        assertEquals(2, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId)
                        .orderByDesc(InvocationRecord::getId));

        assertEquals(2, records.size());

        InvocationRecord second = records.get(0);
        InvocationRecord first = records.get(1);

        assertTrue(second.getId() > first.getId());
        assertEquals(InvocationStatus.SUCCESS, first.getStatus());
        assertEquals(InvocationStatus.SUCCESS, second.getStatus());
    }

    // ---- Test D: 保留已有失败和认证行为，不产生 SUCCESS 记录 ----

    @Test
    @DisplayName("无效 API Key -> 401，零 SUCCESS 记录")
    void invalidApiKeyShouldNotCreateSuccessRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-unauth", "rec-unauth-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer aig_live_fake.invalid")
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));

        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("非法 InvokeRequest -> 400，零 SUCCESS 记录")
    void invalidRequestShouldNotCreateSuccessRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-invalid", "rec-invalid-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertEquals(0, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));

        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("Provider 500 -> 原 P2 行为不变，零 FAILED 记录")
    void providerErrorShouldNotCreateFailedRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-proverr", "rec-proverr-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("500");
        responseBody.set("{\"error\":\"internal\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));

        assertEquals(1, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));

        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("Provider 429 -> 原 P2 行为不变，零 FAILED 记录")
    void providerRateLimitedShouldNotCreateFailedRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-ratelimit", "rec-ratelimit-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("429");
        responseBody.set("{\"error\":\"too many requests\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_RATE_LIMITED"));

        assertEquals(1, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));

        assertTrue(records.isEmpty());
    }

    // ---- helpers ----

    private Object[] setupApplicationWithKeyAndDeployment(
            String prefix, String modelName, String remoteModelName,
            boolean enabled, String credential) throws Exception {

        long providerId = createProvider(prefix + "-provider");
        long modelId = createModel(modelName);
        long deploymentId = createDeployment(
                prefix + "-deployment", providerId, modelId, remoteModelName, enabled, credential);

        long teamId = createTeam(prefix + "-team");
        long appId = createApplication(prefix + "-app", teamId, deploymentId);

        String apiKey = createApiKey(appId, prefix + "-key");

        return new Object[]{appId, apiKey};
    }

    private long createProvider(String name) throws Exception {

        com.felix.aigate.provider.dto.request.CreateProviderRequest request =
                new com.felix.aigate.provider.dto.request.CreateProviderRequest();
        request.setName(name);
        request.setType(com.felix.aigate.provider.entity.ProviderType.OPENAI_COMPATIBLE);

        String body = mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createModel(String name) throws Exception {

        com.felix.aigate.model.dto.request.CreateModelRequest request =
                new com.felix.aigate.model.dto.request.CreateModelRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createDeployment(String name, long providerId, long modelId,
                                  String remoteModelName, boolean enabled, String credential)
            throws Exception {

        com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest request =
                new com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest();
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

        com.felix.aigate.team.dto.request.CreateTeamRequest request =
                new com.felix.aigate.team.dto.request.CreateTeamRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createApplication(String name, long teamId, Long defaultDeploymentId) throws Exception {

        com.felix.aigate.application.dto.request.CreateApplicationRequest request =
                new com.felix.aigate.application.dto.request.CreateApplicationRequest();
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

        com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest request =
                new com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/applications/" + applicationId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("apiKey").asText();
    }
}
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-T05 Invocation Record Completion 集成测试。
 *
 * <p>覆盖 SUCCESS/FAILED 双路径记录、管理查询完整 E2E 闭环、原有认证/校验零记录。
 */
class RuntimeInvokeRecordingIntegrationTest extends IntegrationTestBase {

    private static final String VALID_PROVIDER_BODY = """
            {"model":"gpt-compatible-model",
             "id":"chatcmpl-123","created":1700000000,
             "usage":{"prompt_tokens":9,"completion_tokens":12,"total_tokens":21},
             "choices":[{"index":0,"message":{"role":"assistant","content":"HashMap is a hash table."},"finish_reason":"stop"}]}
            """;

    private static final String INVALID_PROVIDER_JSON = """
            {"choices":[]}
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

    // ============ SUCCESS 记录回归 ============

    @Test
    @DisplayName("成功 Invoke 后写入一条 SUCCESS 记录")
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

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());

        InvocationRecord record = records.get(0);
        assertEquals(appId, record.getApplicationId());
        assertEquals(InvocationStatus.SUCCESS, record.getStatus());
        assertNull(record.getErrorCode());
        assertTrue(record.getDurationMs() >= 0);
        assertNotNull(record.getCreatedAt());
    }

    @Test
    @DisplayName("连续两次成功调用产生两条不同 ID 的 SUCCESS 记录")
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

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(2, records.size());
        assertTrue(records.get(1).getId() > records.get(0).getId());
        assertEquals(InvocationStatus.SUCCESS, records.get(0).getStatus());
        assertEquals(InvocationStatus.SUCCESS, records.get(1).getStatus());
    }

    // ============ FAILED 记录 ============

    @Test
    @DisplayName("Provider 400 -> FAILED/PROVIDER_BAD_REQUEST")
    void providerBadRequestShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-p400", "rec-p400-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("400");
        responseBody.set("{\"error\":\"bad request\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_BAD_REQUEST"));

        assertEquals(1, requestCount.get());

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());

        InvocationRecord record = records.get(0);
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_BAD_REQUEST", record.getErrorCode());
        assertTrue(record.getDurationMs() >= 0);
        assertNotNull(record.getCreatedAt());
    }

    @Test
    @DisplayName("Provider 401 -> FAILED/PROVIDER_AUTH_FAILED")
    void providerAuthFailedShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-p401", "rec-p401-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("401");
        responseBody.set("{\"error\":\"unauthorized\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"));

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        InvocationRecord record = records.get(0);
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_AUTH_FAILED", record.getErrorCode());
    }

    @Test
    @DisplayName("Provider 403 -> FAILED/PROVIDER_AUTH_FAILED")
    void provider403ForbiddenShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-p403", "rec-p403-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("403");
        responseBody.set("{\"error\":\"forbidden\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"));

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        assertEquals(InvocationStatus.FAILED, records.get(0).getStatus());
        assertEquals("PROVIDER_AUTH_FAILED", records.get(0).getErrorCode());
    }

    @Test
    @DisplayName("Provider 429 -> FAILED/PROVIDER_RATE_LIMITED")
    void providerRateLimitedShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-429", "rec-429-model", "gpt-compatible-model", true, null);
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

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        InvocationRecord record = records.get(0);
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_RATE_LIMITED", record.getErrorCode());
    }

    @Test
    @DisplayName("Provider 500 -> FAILED/PROVIDER_UNAVAILABLE")
    void providerUnavailableShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-p500", "rec-p500-model", "gpt-compatible-model", true, null);
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

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        InvocationRecord record = records.get(0);
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", record.getErrorCode());
    }

    @Test
    @DisplayName("Provider 返回非法 JSON -> FAILED/PROVIDER_INVALID_RESPONSE")
    void providerInvalidJsonShouldRecordFailed() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-invjson", "rec-invjson-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        responseStatus.set("200");
        responseBody.set(INVALID_PROVIDER_JSON);

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_INVALID_RESPONSE"));

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        InvocationRecord record = records.get(0);
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_INVALID_RESPONSE", record.getErrorCode());
    }

    @Test
    @DisplayName("Application 无默认 Deployment -> FAILED/MODEL_DEPLOYMENT_NOT_CONFIGURED，零出站")
    void missingDefaultDeploymentShouldRecordFailedNoOutbound() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-nodepl", "rec-nodepl-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        clearDefaultDeploymentForApp(appId);

        int countBefore = requestCount.get();

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_NOT_CONFIGURED"));

        assertEquals(countBefore, requestCount.get());

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        assertEquals(InvocationStatus.FAILED, records.get(0).getStatus());
        assertEquals("MODEL_DEPLOYMENT_NOT_CONFIGURED", records.get(0).getErrorCode());
    }

    @Test
    @DisplayName("Deployment disabled -> FAILED/MODEL_DEPLOYMENT_DISABLED，零出站")
    void disabledDeploymentShouldRecordFailedNoOutbound() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-disabled", "rec-disabled-model", "gpt-compatible-model", false, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        int countBefore = requestCount.get();

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_DISABLED"));

        assertEquals(countBefore, requestCount.get());

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(1, records.size());
        assertEquals(InvocationStatus.FAILED, records.get(0).getStatus());
        assertEquals("MODEL_DEPLOYMENT_DISABLED", records.get(0).getErrorCode());
    }

    // ============ 认证/校验仍不产生记录 ============

    @Test
    @DisplayName("无效 API Key -> 401，零记录")
    void invalidApiKeyShouldNotCreateRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-unauth", "rec-unauth-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer aig_live_fake.invalid")
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertTrue(getRecordsForApp(appId).isEmpty());
    }

    @Test
    @DisplayName("非法 InvokeRequest -> 400，零记录")
    void invalidRequestShouldNotCreateRecord() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-inv", "rec-inv-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertTrue(getRecordsForApp(appId).isEmpty());
    }

    // ============ 管理查询完整闭环 ============

    @Test
    @DisplayName("管理查询完整 E2E：SUCCESS + FAILED + 排序 + 隔离 + DTO")
    void managementQueryFullE2E() throws Exception {

        Object[] setup = setupApplicationWithKeyAndDeployment(
                "rec-e2e", "rec-e2e-model", "gpt-compatible-model", true, null);
        long appId = (Long) setup[0];
        String apiKey = (String) setup[1];

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isOk());

        responseStatus.set("500");
        responseBody.set("{\"error\":\"internal\"}");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content(INVOKE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));

        List<InvocationRecord> records = getRecordsForApp(appId);
        assertEquals(2, records.size());

        InvocationRecord second = records.get(1);
        InvocationRecord first = records.get(0);

        assertEquals(InvocationStatus.SUCCESS, first.getStatus());
        assertEquals(InvocationStatus.FAILED, second.getStatus());

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].applicationId").value(appId))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].applicationId").value(appId))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorCode").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].errorCode").isEmpty());
    }

    // ============ helpers ============

    private List<InvocationRecord> getRecordsForApp(long appId) {
        return invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId));
    }

    private void clearDefaultDeploymentForApp(long applicationId) throws Exception {

        com.felix.aigate.application.dto.request.UpdateApplicationRequest request =
                new com.felix.aigate.application.dto.request.UpdateApplicationRequest();
        request.setName("rec-nodepl-app");

        String body = mockMvc.perform(authed(get("/api/applications/" + applicationId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long teamId = jsonMapper.readTree(body).get("teamId").asLong();

        request.setTeamId(teamId);
        request.setDefaultDeploymentId(null);

        mockMvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/applications/" + applicationId))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());
    }

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
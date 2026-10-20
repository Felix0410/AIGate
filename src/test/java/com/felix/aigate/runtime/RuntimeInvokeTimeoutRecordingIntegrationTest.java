package com.felix.aigate.runtime;

import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-T06 Provider 超时记录集成测试。
 *
 * <p>独立 Spring 上下文把 Provider 出站 read timeout 压到 1 秒，本地 Mock Provider 固定延迟
 * 3 秒，两者差距明确，不存在"服务端准备响应与客户端超时竞争"的竞态；替代原
 * RuntimeInvokeRecordingIntegrationTest 中 60000ms 延迟对 60s 默认超时的不稳定测试。
 *
 * <p>保持真实 Runtime HTTP 调用链（MockMvc -> Security -> DB -> ModelProxyService ->
 * RestClient -> Mock Provider），不 Mock ModelProxyService 或 ProviderHttpClient，
 * 验证超时映射为 504 PROVIDER_TIMEOUT，且 MySQL 恰好产生一条 FAILED/PROVIDER_TIMEOUT 记录、
 * 上游只收到一次请求（无重试）。
 */
@TestPropertySource(properties = {
        "aigate.provider.connect-timeout-seconds=1",
        "aigate.provider.read-timeout-seconds=1"
})
class RuntimeInvokeTimeoutRecordingIntegrationTest extends IntegrationTestBase {

    /** 固定延迟 3 秒，远大于 1 秒 read timeout，超时必然先触发。 */
    private static final long PROVIDER_DELAY_MS = 3000L;

    private static HttpServer mockProvider;
    private static ExecutorService providerExecutor;
    private static String providerBaseUrl;

    private static final AtomicInteger requestCount = new AtomicInteger();

    @Autowired
    private InvocationRecordMapper invocationRecordMapper;

    @BeforeAll
    static void startMockProvider() throws IOException {
        mockProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        providerExecutor = Executors.newCachedThreadPool();
        mockProvider.setExecutor(providerExecutor);
        mockProvider.createContext("/", RuntimeInvokeTimeoutRecordingIntegrationTest::handleSlowly);
        mockProvider.start();
        providerBaseUrl = "http://localhost:" + mockProvider.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopMockProvider() {
        mockProvider.stop(0);
        // 客户端早已超时断开，handler 线程可能仍在 sleep：关闭池并中断，确保后台线程退出，
        // 不遗留后台请求影响其他测试。
        providerExecutor.shutdownNow();
        try {
            providerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** 先计数，再固定延迟到超过 read timeout；客户端断开后写回失败被安全吞掉。 */
    private static void handleSlowly(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        try {
            Thread.sleep(PROVIDER_DELAY_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            byte[] response = """
                    {"model":"gpt-compatible-model","choices":[{"index":0,
                    "message":{"role":"assistant","content":"too late"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (IOException expectedAfterClientTimeout) {
            // 客户端已因超时断开：此处写入失败是预期内的，不外抛、不打印堆栈。
        } finally {
            exchange.close();
        }
    }

    @Test
    @DisplayName("Provider 超时 -> 504 PROVIDER_TIMEOUT，恰好一条 FAILED/PROVIDER_TIMEOUT 记录，无重试")
    void providerTimeoutShouldRecordFailed() throws Exception {

        TimeoutSetup setup = setupApplicationWithKeyAndSlowDeployment("timeout-rec");

        mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + setup.apiKey())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"));

        // 无重试：1 秒 read timeout 下，上游至多收到一次请求。
        assertEquals(1, requestCount.get());

        List<InvocationRecord> records = invocationRecordMapper.selectList(
                new LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, setup.appId()));
        assertEquals(1, records.size());

        InvocationRecord record = records.get(0);
        assertEquals(setup.appId(), record.getApplicationId());
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_TIMEOUT", record.getErrorCode());
        assertNotNull(record.getDurationMs());
        // 超时在 read timeout 附近触发，耗时应远小于 Mock 延迟，佐证未等待完整响应。
        assertTrue(record.getDurationMs() >= 0 && record.getDurationMs() < PROVIDER_DELAY_MS,
                "durationMs " + record.getDurationMs() + " should be under " + PROVIDER_DELAY_MS);
        assertNotNull(record.getCreatedAt());

        cleanupTimeoutSetup(setup);
    }

    // ---- helpers ----

    private record TimeoutSetup(long appId, String apiKey, long teamId) {
    }

    private TimeoutSetup setupApplicationWithKeyAndSlowDeployment(String prefix) throws Exception {

        long providerId = idOf(createViaBasic(post("/api/providers"), providerRequest(prefix + "-provider")));
        long modelId = idOf(createViaBasic(post("/api/models"), modelRequest(prefix + "-model")));
        long deploymentId = idOf(createViaBasic(post("/api/model-deployments"),
                deploymentRequest(prefix + "-deployment", providerId, modelId)));
        long teamId = idOf(createViaBasic(post("/api/teams"), teamRequest(prefix + "-team")));
        long appId = idOf(createViaBasic(post("/api/applications"),
                applicationRequest(prefix + "-app", teamId, deploymentId)));

        CreateApplicationApiKeyRequest keyRequest = new CreateApplicationApiKeyRequest();
        keyRequest.setName(prefix + "-key");
        String apiKey = createViaBasic(post("/api/applications/" + appId + "/api-keys"), keyRequest)
                .get("apiKey").asText();

        return new TimeoutSetup(appId, apiKey, teamId);
    }

    /** 只清理当前测试创建的资源：先删 Application（引用 Team），再删 Team。 */
    private void cleanupTimeoutSetup(TimeoutSetup setup) throws Exception {

        mockMvc.perform(authed(delete("/api/applications/" + setup.appId())))
                .andExpect(status().isOk());
        mockMvc.perform(authed(delete("/api/teams/" + setup.teamId())))
                .andExpect(status().isOk());
    }

    private CreateProviderRequest providerRequest(String name) {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setName(name);
        request.setType(ProviderType.OPENAI_COMPATIBLE);
        return request;
    }

    private CreateModelRequest modelRequest(String name) {
        CreateModelRequest request = new CreateModelRequest();
        request.setName(name);
        return request;
    }

    private CreateModelDeploymentRequest deploymentRequest(String name, long providerId, long modelId) {
        CreateModelDeploymentRequest request = new CreateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl(providerBaseUrl);
        request.setRemoteModelName("gpt-compatible-model");
        request.setEnabled(true);
        return request;
    }

    private CreateTeamRequest teamRequest(String name) {
        CreateTeamRequest request = new CreateTeamRequest();
        request.setName(name);
        return request;
    }

    private CreateApplicationRequest applicationRequest(String name, long teamId, long deploymentId) {
        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(deploymentId);
        return request;
    }

    private JsonNode createViaBasic(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                    Object requestBody) throws Exception {
        String body = mockMvc.perform(authed(builder.contentType(APPLICATION_JSON).content(json(requestBody))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    private long idOf(JsonNode node) {
        return node.get("id").asLong();
    }
}

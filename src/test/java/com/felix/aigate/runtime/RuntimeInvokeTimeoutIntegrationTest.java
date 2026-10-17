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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-T10 读取超时端到端测试：独立 Spring 上下文把 Provider 出站 read timeout 压到 1 秒，
 * 本地 Mock Provider 故意延迟 3 秒响应，证明真实全链路
 * POST /v1/invoke -> Security -> DB -> ModelProxyService -> RestClient -> Mock Provider
 * 在读取超时时映射为 504 PROVIDER_TIMEOUT，且一次调用只发出一次上游请求（无重试）。
 *
 * <p>放在独立类是因为需要极小的超时配置，避免拖慢共享上下文里的其它端到端测试；
 * 不访问任何真实模型服务。
 */
@TestPropertySource(properties = {
        "aigate.provider.connect-timeout-seconds=1",
        "aigate.provider.read-timeout-seconds=1"
})
class RuntimeInvokeTimeoutIntegrationTest extends IntegrationTestBase {

    private static HttpServer slowProvider;
    private static ExecutorService providerExecutor;
    private static String providerBaseUrl;

    private static final AtomicInteger requestCount = new AtomicInteger();

    @BeforeAll
    static void startSlowProvider() throws IOException {
        slowProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        providerExecutor = Executors.newCachedThreadPool();
        slowProvider.setExecutor(providerExecutor);
        slowProvider.createContext("/", RuntimeInvokeTimeoutIntegrationTest::handleSlowly);
        slowProvider.start();
        providerBaseUrl = "http://localhost:" + slowProvider.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopSlowProvider() {
        slowProvider.stop(0);
        // 客户端早已超时断开，handler 线程可能仍在 sleep：关闭池并中断，确保后台线程退出。
        providerExecutor.shutdownNow();
        try {
            providerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** 先计数，再延迟到超过 read timeout；客户端断开后写回失败被安全吞掉，不污染测试日志。 */
    private static void handleSlowly(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            byte[] response = """
                    {"model":"model-timeout","choices":[{"index":0,
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
    @DisplayName("读取超时 -> 504 PROVIDER_TIMEOUT，一次调用只发出一次上游请求")
    void readTimeoutMapsToGatewayTimeout() throws Exception {

        String apiKey = setupApplicationBoundToSlowProvider();
        requestCount.set(0);

        String content = mockMvc.perform(post("/v1/invoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"))
                .andReturn().getResponse().getContentAsString();

        // 无重试：1 秒 read timeout 下，客户端至多发出一次请求。
        assertEquals(1, requestCount.get());
        // 不泄露：响应不含 Mock 内容 / 内部 URL。
        assertTrue(!content.contains("too late"));
        assertTrue(!content.contains(providerBaseUrl));
    }

    // ---- helpers ----

    private String setupApplicationBoundToSlowProvider() throws Exception {

        String prefix = "invoke-timeout";

        CreateProviderRequest provider = new CreateProviderRequest();
        provider.setName(prefix + "-provider");
        provider.setType(ProviderType.OPENAI_COMPATIBLE);
        long providerId = idOf(createViaBasic(post("/api/providers"), provider));

        CreateModelRequest model = new CreateModelRequest();
        model.setName(prefix + "-model");
        long modelId = idOf(createViaBasic(post("/api/models"), model));

        CreateModelDeploymentRequest deployment = new CreateModelDeploymentRequest();
        deployment.setName(prefix + "-deployment");
        deployment.setProviderId(providerId);
        deployment.setModelId(modelId);
        deployment.setEndpointUrl(providerBaseUrl);
        deployment.setRemoteModelName("model-timeout");
        deployment.setEnabled(true);
        long deploymentId = idOf(createViaBasic(post("/api/model-deployments"), deployment));

        CreateTeamRequest team = new CreateTeamRequest();
        team.setName(prefix + "-team");
        long teamId = idOf(createViaBasic(post("/api/teams"), team));

        CreateApplicationRequest application = new CreateApplicationRequest();
        application.setName(prefix + "-app");
        application.setTeamId(teamId);
        application.setDefaultDeploymentId(deploymentId);
        long appId = idOf(createViaBasic(post("/api/applications"), application));

        CreateApplicationApiKeyRequest keyRequest = new CreateApplicationApiKeyRequest();
        keyRequest.setName(prefix + "-key");
        return createViaBasic(post("/api/applications/" + appId + "/api-keys"), keyRequest)
                .get("apiKey").asText();
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

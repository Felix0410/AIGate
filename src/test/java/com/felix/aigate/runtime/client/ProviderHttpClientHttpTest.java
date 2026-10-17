package com.felix.aigate.runtime.client;

import com.felix.aigate.config.RestClientConfig;
import com.felix.aigate.provider.adapter.OpenAiCompatibleProviderAdapter;
import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.MessageRole;
import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.service.ModelInvocationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProviderHttpClient 真实出站 + 分类单元测试：用 JDK {@link HttpServer}（随机端口）驱动真实 HTTP，
 * 覆盖状态码分类、连接失败/读超时分类、请求体契约、Credential 决定 Authorization、以及敏感信息不泄露。
 * 不连数据库、不访问真实模型；分类逻辑（classifyResponseStatus / classifyNetworkFailure）另以合成异常链直接验证。
 */
class ProviderHttpClientHttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SUCCESS_BODY = """
            {"model":"remote-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"stop"}]}
            """;

    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static String baseUrl;

    private static final AtomicInteger requestCount = new AtomicInteger();
    private static final AtomicReference<String> lastMethod = new AtomicReference<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastContentType = new AtomicReference<>();
    private static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> lastBody = new AtomicReference<>();
    private static final AtomicReference<String> responseStatus = new AtomicReference<>("200");
    private static final AtomicReference<String> responseBody = new AtomicReference<>(SUCCESS_BODY);
    private static final AtomicLong responseDelayMs = new AtomicLong(0);

    private final OpenAiCompatibleProviderAdapter adapter = new OpenAiCompatibleProviderAdapter(JSON);
    private final ProviderHttpClient client =
            new ProviderHttpClient(new RestClientConfig().providerRestClient(3, 60));
    // 短超时客户端：用于真实读超时场景（connect 1s / read 1s）。
    private final ProviderHttpClient shortTimeoutClient =
            new ProviderHttpClient(new RestClientConfig().providerRestClient(1, 1));

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ProviderHttpClientHttpTest::handle);
        // 独立线程池：避免读超时场景下睡眠中的 handler 阻塞后续请求；保存引用以便 @AfterAll 显式回收。
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        // 读超时场景下 handler 线程可能仍在 sleep：中断并等待退出，避免后台线程泄漏影响其它测试。
        serverExecutor.shutdownNow();
        try {
            serverExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void resetState() {
        requestCount.set(0);
        lastMethod.set(null);
        lastPath.set(null);
        lastContentType.set(null);
        lastAuthorization.set(null);
        lastBody.set(null);
        responseStatus.set("200");
        responseBody.set(SUCCESS_BODY);
        responseDelayMs.set(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().getPath());
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        long delay = responseDelayMs.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }

        try {
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(responseStatus.get()), response.length);
            exchange.getResponseBody().write(response);
        } catch (IOException clientGone) {
            // 延迟场景下客户端可能已超时断开：此时写回失败是预期内的，吞掉异常、不打印堆栈污染测试日志。
        } finally {
            exchange.close();
        }
    }

    // ---- 已有契约测试 ----

    @Test
    @DisplayName("有 Credential：真实 POST 到 /v1/chat/completions，携带独立 Provider Bearer，请求体符合契约")
    void shouldPostWithProviderCredential() {

        String body = client.postChatCompletions(completionsUrl(), buildProviderRequest(), "sk-provider-secret");

        assertEquals(JSON.readTree(SUCCESS_BODY), JSON.readTree(body));
        assertEquals("POST", lastMethod.get());
        assertEquals("/v1/chat/completions", lastPath.get());
        assertTrue(lastContentType.get().contains("application/json"));
        assertEquals("Bearer sk-provider-secret", lastAuthorization.get());

        JsonNode sent = JSON.readTree(lastBody.get());
        assertEquals("remote-model", sent.get("model").asString());
        assertEquals("Explain HashMap.", sent.get("messages").get(0).get("content").asString());
        assertEquals("user", sent.get("messages").get(0).get("role").asString());
        assertEquals(500, sent.get("max_tokens").asInt());
        assertFalse(sent.has("maxTokens"));
    }

    @Test
    @DisplayName("无 Credential：不附加 Authorization 头")
    void shouldOmitAuthorizationWhenNoCredential() {

        client.postChatCompletions(completionsUrl(), buildProviderRequest(), null);

        assertNull(lastAuthorization.get());
    }

    // ---- A. 真实上游状态码 -> 分类 ----

    @Test
    @DisplayName("上游 400/401/403/429/500/503/404 -> 各自安全分类，且一次调用只发一次请求")
    void upstreamStatusCodesAreClassified() {

        record Case(int status, ProxyErrorType expected) {
        }
        List<Case> cases = List.of(
                new Case(400, ProxyErrorType.PROVIDER_BAD_REQUEST),
                new Case(401, ProxyErrorType.PROVIDER_AUTH_FAILED),
                new Case(403, ProxyErrorType.PROVIDER_AUTH_FAILED),
                new Case(429, ProxyErrorType.PROVIDER_RATE_LIMITED),
                new Case(500, ProxyErrorType.PROVIDER_UNAVAILABLE),
                new Case(503, ProxyErrorType.PROVIDER_UNAVAILABLE),
                new Case(404, ProxyErrorType.PROVIDER_UNAVAILABLE),
                new Case(418, ProxyErrorType.PROVIDER_UNAVAILABLE)
        );

        for (Case testCase : cases) {
            requestCount.set(0);
            responseStatus.set(String.valueOf(testCase.status()));
            // 上游错误 Body 带敏感标记，必须不进入异常/客户端响应。
            responseBody.set("{\"error\":\"sensitive-upstream-body-marker test-provider-secret\"}");

            ModelInvocationException exception = assertThrows(ModelInvocationException.class,
                    () -> client.postChatCompletions(completionsUrl(), buildProviderRequest(), "sk-provider-secret"));

            assertEquals(testCase.expected(), exception.getErrorType(), "status=" + testCase.status());
            // 无自动重试：每个失败状态只发出一次请求。
            assertEquals(1, requestCount.get(), "status=" + testCase.status());
            // 异常不带 cause，且不泄露上游响应体/凭证/URL 标记。
            assertNull(exception.getCause());
            assertFalse(exception.getMessage().contains("sensitive-upstream-body-marker"));
            assertFalse(exception.getMessage().contains("test-provider-secret"));
            assertFalse(exception.getMessage().contains(baseUrl));
        }
    }

    // ---- B. 分类逻辑（合成异常链）----

    @Test
    @DisplayName("classifyResponseStatus 按约定映射，其余非成功状态安全归为不可用")
    void classifyResponseStatusMapping() {
        assertEquals(ProxyErrorType.PROVIDER_BAD_REQUEST, ProviderHttpClient.classifyResponseStatus(400));
        assertEquals(ProxyErrorType.PROVIDER_AUTH_FAILED, ProviderHttpClient.classifyResponseStatus(401));
        assertEquals(ProxyErrorType.PROVIDER_AUTH_FAILED, ProviderHttpClient.classifyResponseStatus(403));
        assertEquals(ProxyErrorType.PROVIDER_RATE_LIMITED, ProviderHttpClient.classifyResponseStatus(429));
        assertEquals(ProxyErrorType.PROVIDER_UNAVAILABLE, ProviderHttpClient.classifyResponseStatus(500));
        assertEquals(ProxyErrorType.PROVIDER_UNAVAILABLE, ProviderHttpClient.classifyResponseStatus(502));
        assertEquals(ProxyErrorType.PROVIDER_UNAVAILABLE, ProviderHttpClient.classifyResponseStatus(404));
    }

    @Test
    @DisplayName("classifyNetworkFailure：超时优先于连接错误，无法识别的安全归为连接故障")
    void classifyNetworkFailureMapping() {
        // 读超时（SocketTimeoutException）
        assertEquals(ProxyErrorType.PROVIDER_TIMEOUT, ProviderHttpClient.classifyNetworkFailure(
                new ResourceAccessException("io error", new SocketTimeoutException("read timed out"))));
        // 深层嵌套的超时仍被识别
        assertEquals(ProxyErrorType.PROVIDER_TIMEOUT, ProviderHttpClient.classifyNetworkFailure(
                new ResourceAccessException("outer", new IOException("mid", new SocketTimeoutException()))));
        // 连接拒绝
        assertEquals(ProxyErrorType.PROVIDER_CONNECTION_ERROR, ProviderHttpClient.classifyNetworkFailure(
                new ResourceAccessException("connect", new ConnectException("Connection refused"))));
        // 无法准确识别的网络异常 -> 安全归为连接故障
        assertEquals(ProxyErrorType.PROVIDER_CONNECTION_ERROR, ProviderHttpClient.classifyNetworkFailure(
                new ResourceAccessException("unknown", new IOException("boom"))));
    }

    // ---- B. 真实本地 HTTP 超时 / 连接失败 ----

    @Test
    @DisplayName("真实读超时：上游延迟超过 readTimeout -> PROVIDER_TIMEOUT，且只发出一次请求")
    void realReadTimeoutIsClassifiedAsTimeout() {
        responseDelayMs.set(3000);

        ModelInvocationException exception = assertThrows(ModelInvocationException.class,
                () -> shortTimeoutClient.postChatCompletions(completionsUrl(), buildProviderRequest(), null));

        assertEquals(ProxyErrorType.PROVIDER_TIMEOUT, exception.getErrorType());
        // 超时不代表上游一定没执行：这里只保证 AIGate 不重试（发出且仅发出一次请求）。
        assertEquals(1, requestCount.get());
    }

    @Test
    @DisplayName("真实连接被拒：目标端口无监听 -> PROVIDER_CONNECTION_ERROR，且不触碰 Mock Provider")
    void realConnectionRefusedIsClassifiedAsConnectionError() throws IOException {

        String deadUrl;
        try (ServerSocket probe = new ServerSocket(0)) {
            // 关闭后该端口无监听：localhost 上立即返回 connection refused，无 DNS/外网依赖。
            deadUrl = "http://localhost:" + probe.getLocalPort() + "/v1/chat/completions";
        }

        ModelInvocationException exception = assertThrows(ModelInvocationException.class,
                () -> client.postChatCompletions(deadUrl, buildProviderRequest(), null));

        assertEquals(ProxyErrorType.PROVIDER_CONNECTION_ERROR, exception.getErrorType());
        assertNull(exception.getCause());
        assertEquals(0, requestCount.get());
    }

    // ---- 辅助：验证真实 JDK 超时异常类型确实落入超时分支 ----

    @Test
    @DisplayName("HttpTimeoutException 属于超时语义（JdkClientHttpRequestFactory 读超时的真实类型）")
    void httpTimeoutExceptionIsTimeoutSubclass() {
        // 直接断言类型层级，保证分类依赖的语义稳定：HttpTimeoutException 不是 ConnectException，
        // 因此不会被误判为连接故障；真实超时分支由 realReadTimeoutIsClassifiedAsTimeout 端到端验证。
        assertTrue(IOException.class.isAssignableFrom(HttpTimeoutException.class));
        assertFalse(ConnectException.class.isAssignableFrom(HttpTimeoutException.class));
    }

    // ---- helpers ----

    private String completionsUrl() {
        return baseUrl + "/v1/chat/completions";
    }

    private Object buildProviderRequest() {
        InvokeMessage message = new InvokeMessage();
        message.setRole(MessageRole.USER);
        message.setContent("Explain HashMap.");
        InvokeRequest request = new InvokeRequest();
        request.setMessages(List.of(message));
        request.setTemperature(0.7);
        request.setMaxTokens(500);
        return adapter.buildRequest(request, "remote-model");
    }
}

package com.felix.aigate.runtime.client;

import com.felix.aigate.config.RestClientConfig;
import com.felix.aigate.provider.adapter.OpenAiCompatibleProviderAdapter;
import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.MessageRole;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProviderHttpClient 真实出站 HTTP 测试：用 JDK {@link HttpServer} 起本地 Mock Provider（随机端口），
 * 证明 RestClient 真的 POST 到 /v1/chat/completions、Content-Type 正确、请求体符合 P2-T07 契约，
 * 并按 Credential 有无决定 Authorization 头。不连数据库、不访问真实模型。
 */
class ProviderHttpClientHttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SUCCESS_BODY = """
            {"model":"remote-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"stop"}]}
            """;

    private static HttpServer server;
    private static String baseUrl;

    private static final AtomicInteger requestCount = new AtomicInteger();
    private static final AtomicReference<String> lastMethod = new AtomicReference<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastContentType = new AtomicReference<>();
    private static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> lastBody = new AtomicReference<>();

    private final OpenAiCompatibleProviderAdapter adapter = new OpenAiCompatibleProviderAdapter(JSON);
    private final ProviderHttpClient client =
            new ProviderHttpClient(new RestClientConfig().providerRestClient(3, 60));

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ProviderHttpClientHttpTest::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().getPath());
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] response = SUCCESS_BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.getResponseBody().close();
    }

    @Test
    @DisplayName("有 Credential：真实 POST 到 /v1/chat/completions，携带独立 Provider Bearer，请求体符合契约")
    void shouldPostWithProviderCredential() {

        String url = baseUrl + "/v1/chat/completions";
        String body = client.postChatCompletions(url, buildProviderRequest(), "sk-provider-secret");

        // 上游返回的原始 JSON 原样带回。
        assertEquals(JSON.readTree(SUCCESS_BODY), JSON.readTree(body));

        assertEquals("POST", lastMethod.get());
        assertEquals("/v1/chat/completions", lastPath.get());
        assertTrue(lastContentType.get().contains("application/json"));
        assertEquals("Bearer sk-provider-secret", lastAuthorization.get());

        JsonNode sent = JSON.readTree(lastBody.get());
        assertEquals("remote-model", sent.get("model").asString());
        assertEquals("Explain HashMap.", sent.get("messages").get(0).get("content").asString());
        assertEquals("user", sent.get("messages").get(0).get("role").asString());
        assertEquals(0.7, sent.get("temperature").asDouble());
        assertEquals(500, sent.get("max_tokens").asInt());
        assertFalse(sent.has("maxTokens"));
    }

    @Test
    @DisplayName("无 Credential：不附加 Authorization 头")
    void shouldOmitAuthorizationWhenNoCredential() {

        String url = baseUrl + "/v1/chat/completions";
        client.postChatCompletions(url, buildProviderRequest(), null);

        assertNull(lastAuthorization.get());
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

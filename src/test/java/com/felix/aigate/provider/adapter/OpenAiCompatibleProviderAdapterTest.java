package com.felix.aigate.provider.adapter;

import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.dto.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OpenAI-Compatible 协议适配器单元测试：请求映射、角色/顺序、可选字段省略、
 * remoteModelName 决定 model、响应解析与各类无效响应。不启 Spring、不连数据库、不发 HTTP。
 */
class OpenAiCompatibleProviderAdapterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private OpenAiCompatibleProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiCompatibleProviderAdapter(jsonMapper);
    }

    @Test
    @DisplayName("正常请求转换：model/messages/temperature/max_tokens 完全一致")
    void shouldBuildChatCompletionsRequest() {

        InvokeRequest request = request(
                List.of(message(MessageRole.USER, "Explain HashMap.")), 0.7, 500);

        JsonNode json = serializeRequest(request, "gpt-compatible-model");

        assertEquals("gpt-compatible-model", json.get("model").asString());
        assertEquals(0.7, json.get("temperature").asDouble());
        assertEquals(500, json.get("max_tokens").asInt());
        assertEquals("Explain HashMap.", json.get("messages").get(0).get("content").asString());

        // maxTokens 必须映射为 max_tokens，且不透出原始驼峰名。
        assertFalse(json.has("maxTokens"));
        // 不引入本阶段不需要的字段。
        assertFalse(json.has("stream"));
        assertFalse(json.has("tools"));
        assertFalse(json.has("response_format"));
    }

    @Test
    @DisplayName("三种角色映射为小写，消息顺序与内容保持不变")
    void shouldMapRolesToLowercaseAndKeepOrder() {

        InvokeRequest request = request(
                List.of(
                        message(MessageRole.SYSTEM, "sys"),
                        message(MessageRole.USER, "usr"),
                        message(MessageRole.ASSISTANT, "ast")
                ),
                null, null);

        JsonNode messages = serializeRequest(request, "m").get("messages");

        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).get("role").asString());
        assertEquals("usr", messages.get(1).get("content").asString());
        assertEquals("assistant", messages.get(2).get("role").asString());
        assertEquals("ast", messages.get(2).get("content").asString());
    }

    @Test
    @DisplayName("temperature / maxTokens 为 null 时 JSON 省略且不生成默认值")
    void shouldOmitOptionalFieldsWhenNull() {

        InvokeRequest request = request(
                List.of(message(MessageRole.USER, "Hello")), null, null);

        JsonNode json = serializeRequest(request, "m");

        assertFalse(json.has("temperature"));
        assertFalse(json.has("max_tokens"));
    }

    @Test
    @DisplayName("model 只由 remoteModelName 参数决定")
    void modelShouldComeFromRemoteModelNameOnly() {

        InvokeRequest request = request(
                List.of(message(MessageRole.USER, "Hello")), null, null);

        JsonNode json = serializeRequest(request, "remote-test-model");

        assertEquals("remote-test-model", json.get("model").asString());
    }

    @Test
    @DisplayName("正常 Provider 响应解析为 InvokeResponse")
    void shouldParseValidResponse() {

        String body = """
                {
                  "model": "gpt-compatible-model",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "HashMap is a hash table."}, "finish_reason": "stop"}
                  ]
                }
                """;

        InvokeResponse response = adapter.parseResponse(body);

        assertEquals("HashMap is a hash table.", response.getContent());
        assertEquals("gpt-compatible-model", response.getModel());
        assertEquals("stop", response.getFinishReason());
    }

    @Test
    @DisplayName("多个 choices 时只取第一个")
    void shouldOnlyReadFirstChoice() {

        String body = """
                {
                  "model": "m",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "first"}, "finish_reason": "stop"},
                    {"index": 1, "message": {"role": "assistant", "content": "second"}, "finish_reason": "length"}
                  ]
                }
                """;

        assertEquals("first", adapter.parseResponse(body).getContent());
    }

    @Test
    @DisplayName("上游额外字段不影响解析且不泄漏到 InvokeResponse")
    void shouldIgnoreExtraUpstreamFields() {

        String body = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "m",
                  "usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3},
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}
                  ]
                }
                """;

        InvokeResponse response = adapter.parseResponse(body);

        assertEquals("ok", response.getContent());

        String responseJson = jsonMapper.writeValueAsString(response);
        assertFalse(responseJson.contains("usage"));
        assertFalse(responseJson.contains("prompt_tokens"));
        assertFalse(responseJson.contains("chatcmpl"));
    }

    @Test
    @DisplayName("content 允许为空字符串")
    void shouldAllowEmptyContent() {

        String body = """
                {
                  "model": "m",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": ""}, "finish_reason": "stop"}]
                }
                """;

        assertEquals("", adapter.parseResponse(body).getContent());
    }

    @Test
    @DisplayName("finish_reason 缺失时允许为 null")
    void shouldAllowMissingFinishReason() {

        String body = """
                {
                  "model": "m",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"}}]
                }
                """;

        assertNull(adapter.parseResponse(body).getFinishReason());
    }

    @Test
    @DisplayName("无效 JSON -> InvalidProviderResponseException")
    void shouldRejectInvalidJson() {

        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse("not a json"));
    }

    @Test
    @DisplayName("根节点不是对象 -> 失败")
    void shouldRejectNonObjectRoot() {

        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse("[1,2,3]"));
    }

    @Test
    @DisplayName("空 choices -> 失败")
    void shouldRejectEmptyChoices() {

        String body = """
                {"model": "m", "choices": []}
                """;

        assertThrows(InvalidProviderResponseException.class, () -> adapter.parseResponse(body));
    }

    @Test
    @DisplayName("缺失必要结构一律失败，不伪造默认结果")
    void shouldRejectMissingRequiredStructures() {

        // 缺失 choices
        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse("{\"model\": \"m\"}"));

        // 缺失 message
        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse("{\"model\": \"m\", \"choices\": [{\"index\": 0}]}"));

        // 缺失 content
        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse(
                        "{\"model\": \"m\", \"choices\": [{\"message\": {\"role\": \"assistant\"}}]}"));

        // content 非字符串
        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse(
                        "{\"model\": \"m\", \"choices\": [{\"message\": {\"content\": 42}}]}"));

        // 缺失 model
        assertThrows(InvalidProviderResponseException.class,
                () -> adapter.parseResponse(
                        "{\"choices\": [{\"message\": {\"content\": \"ok\"}, \"finish_reason\": \"stop\"}]}"));
    }

    @Test
    @DisplayName("finish_reason 类型错误 -> 失败，不静默转文本")
    void shouldRejectWrongFinishReasonType() {

        String body = """
                {
                  "model": "m",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": 123}]
                }
                """;

        assertThrows(InvalidProviderResponseException.class, () -> adapter.parseResponse(body));
    }

    // ---- helpers ----

    private InvokeMessage message(MessageRole role, String content) {
        InvokeMessage message = new InvokeMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private InvokeRequest request(List<InvokeMessage> messages, Double temperature, Integer maxTokens) {
        InvokeRequest request = new InvokeRequest();
        request.setMessages(new ArrayList<>(messages));
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);
        return request;
    }

    private JsonNode serializeRequest(InvokeRequest request, String remoteModelName) {
        Object providerRequest = adapter.buildRequest(request, remoteModelName);
        return jsonMapper.readTree(jsonMapper.writeValueAsString(providerRequest));
    }
}

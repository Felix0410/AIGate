package com.felix.aigate.runtime;

import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.dto.MessageRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-T06 统一模型契约测试：只用 Jackson 3 + Jakarta Validator 验证 DTO 的序列化/反序列化与校验语义，
 * 不涉及 HTTP、数据库或模型调用。
 */
class InvokeContractTest {

    private static JsonMapper jsonMapper;
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        jsonMapper = JsonMapper.builder().build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("合法请求可反序列化，system/user/assistant 三种角色映射为枚举")
    void shouldDeserializeAllThreeRoles() {

        String json = """
                {
                  "messages": [
                    {"role": "system", "content": "You are a coding assistant."},
                    {"role": "user", "content": "Explain Java HashMap."},
                    {"role": "assistant", "content": "Sure."}
                  ],
                  "temperature": 0.7,
                  "maxTokens": 500
                }
                """;

        InvokeRequest request = jsonMapper.readValue(json, InvokeRequest.class);

        assertEquals(3, request.getMessages().size());
        assertEquals(MessageRole.SYSTEM, request.getMessages().get(0).getRole());
        assertEquals(MessageRole.USER, request.getMessages().get(1).getRole());
        assertEquals(MessageRole.ASSISTANT, request.getMessages().get(2).getRole());
        assertEquals(Double.valueOf(0.7), request.getTemperature());
        assertEquals(Integer.valueOf(500), request.getMaxTokens());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("可选字段缺失仍合法")
    void optionalFieldsCanBeAbsent() {

        String json = """
                {"messages": [{"role": "user", "content": "Hello"}]}
                """;

        InvokeRequest request = jsonMapper.readValue(json, InvokeRequest.class);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("枚举序列化为小写 JSON，绝不输出大写名")
    void enumShouldSerializeToLowercase() {

        String user = jsonMapper.writeValueAsString(MessageRole.USER);
        String assistant = jsonMapper.writeValueAsString(MessageRole.ASSISTANT);

        assertEquals("\"user\"", user);
        assertEquals("\"assistant\"", assistant);
        assertFalse(user.contains("USER"));
    }

    @Test
    @DisplayName("未知 role 被反序列化拒绝")
    void unknownRoleShouldBeRejected() {

        String json = """
                {"messages": [{"role": "invalid", "content": "Hello"}]}
                """;

        assertThrows(JacksonException.class,
                () -> jsonMapper.readValue(json, InvokeRequest.class));
    }

    @Test
    @DisplayName("messages 缺失或为空 -> 校验错误")
    void missingOrEmptyMessagesShouldFailValidation() {

        InvokeRequest absent = jsonMapper.readValue("{}", InvokeRequest.class);
        InvokeRequest empty = jsonMapper.readValue("{\"messages\": []}", InvokeRequest.class);

        assertTrue(hasViolationOn(absent, "messages"));
        assertTrue(hasViolationOn(empty, "messages"));
    }

    @Test
    @DisplayName("role 缺失 -> 嵌套校验失败")
    void missingRoleShouldFailValidation() {

        InvokeRequest request = jsonMapper.readValue(
                "{\"messages\": [{\"content\": \"Hello\"}]}", InvokeRequest.class);

        assertTrue(hasViolationOn(request, "messages[0].role"));
    }

    @Test
    @DisplayName("content 为 null/空串/空白 -> 嵌套 @Valid 生效")
    void blankContentShouldFailValidation() {

        for (String content : Arrays.asList((String) null, "", "   ")) {
            InvokeRequest request = new InvokeRequest();
            request.setMessages(List.of(message(MessageRole.USER, content)));

            assertTrue(
                    hasViolationOn(request, "messages[0].content"),
                    "content=[" + content + "] 应触发嵌套校验");
        }
    }

    @Test
    @DisplayName("temperature 边界：null/0.0/0.7/2.0 合法，-0.1/2.1 非法")
    void temperatureBoundaryShouldBeEnforced() {

        for (Double temperature : Arrays.asList((Double) null, 0.0, 0.7, 2.0)) {
            assertTrue(validator.validate(requestWith(temperature, null)).isEmpty(),
                    "temperature=" + temperature + " 应合法");
        }

        for (Double temperature : Arrays.asList(-0.1, 2.1)) {
            assertTrue(hasViolationOn(requestWith(temperature, null), "temperature"),
                    "temperature=" + temperature + " 应非法");
        }
    }

    @Test
    @DisplayName("maxTokens 边界：null/1/500 合法，0/-1 非法")
    void maxTokensBoundaryShouldBeEnforced() {

        for (Integer maxTokens : Arrays.asList((Integer) null, 1, 500)) {
            assertTrue(validator.validate(requestWith(null, maxTokens)).isEmpty(),
                    "maxTokens=" + maxTokens + " 应合法");
        }

        for (Integer maxTokens : Arrays.asList(0, -1)) {
            assertTrue(hasViolationOn(requestWith(null, maxTokens), "maxTokens"),
                    "maxTokens=" + maxTokens + " 应非法");
        }
    }

    @Test
    @DisplayName("InvokeRequest 不暴露内部资源 ID 入口")
    void requestShouldNotContainInternalResourceFields() {

        Set<String> fields = Arrays.stream(InvokeRequest.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        for (String forbidden : List.of("applicationId", "providerId", "modelId", "deploymentId")) {
            assertFalse(fields.contains(forbidden), "不应包含字段 " + forbidden);
        }
    }

    @Test
    @DisplayName("响应仅包含 content/model/finishReason，不含内部字段")
    void responseShouldOnlyContainContractFields() {

        String json = jsonMapper.writeValueAsString(
                new InvokeResponse("HashMap is...", "Mock Chat Model", "stop"));

        JsonNode node = jsonMapper.readTree(json);

        assertEquals("HashMap is...", node.get("content").asText());
        assertEquals("Mock Chat Model", node.get("model").asText());
        assertEquals("stop", node.get("finishReason").asText());

        for (String forbidden : List.of("usage", "cost", "providerResponse", "deploymentId")) {
            assertFalse(node.has(forbidden), "响应不应出现 " + forbidden);
        }
    }

    // ---- helpers ----

    private InvokeMessage message(MessageRole role, String content) {
        InvokeMessage message = new InvokeMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private InvokeRequest requestWith(Double temperature, Integer maxTokens) {
        InvokeRequest request = new InvokeRequest();
        request.setMessages(List.of(message(MessageRole.USER, "Hello")));
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);
        return request;
    }

    private boolean hasViolationOn(Object target, String propertyPathPrefix) {
        return validator.validate(target).stream()
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().startsWith(propertyPathPrefix));
    }
}

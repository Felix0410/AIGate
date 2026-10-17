package com.felix.aigate.provider.adapter;

import com.felix.aigate.provider.adapter.dto.OpenAiChatMessage;
import com.felix.aigate.provider.adapter.dto.OpenAiChatRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * OpenAI-Compatible Chat Completions 协议适配器。
 *
 * <p>请求方向：统一 InvokeRequest -> OpenAiChatRequest（model 只来自 remoteModelName，
 * maxTokens 映射 max_tokens，可选 null 字段序列化时省略，消息顺序与内容原样保留）。
 * 响应方向：只读取 choices[0] 与 model，忽略 id/created/usage 等额外字段，
 * 结构不合法一律抛 {@link InvalidProviderResponseException}，绝不伪造响应。
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProviderAdapter implements ProviderAdapter {

    private final JsonMapper jsonMapper;

    @Override
    public ProviderType supportedType() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public OpenAiChatRequest buildRequest(InvokeRequest request, String remoteModelName) {

        List<OpenAiChatMessage> messages = request.getMessages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        return new OpenAiChatRequest(
                remoteModelName,
                messages,
                request.getTemperature(),
                request.getMaxTokens()
        );
    }

    @Override
    public InvokeResponse parseResponse(String responseBody) {

        JsonNode root = readRootObject(responseBody);

        String model = requireNonBlankText(root.get("model"), "model");

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new InvalidProviderResponseException("choices is missing or empty");
        }

        JsonNode firstChoice = choices.get(0);
        if (firstChoice == null || !firstChoice.isObject()) {
            throw new InvalidProviderResponseException("choices[0] is not an object");
        }

        JsonNode message = firstChoice.get("message");
        if (message == null || !message.isObject()) {
            throw new InvalidProviderResponseException("message is missing");
        }

        // content 允许为空字符串，但必须是字符串且不能缺失。
        String content = requireText(message.get("content"), "content");

        String finishReason = optionalText(firstChoice.get("finish_reason"), "finish_reason");

        return new InvokeResponse(content, model, finishReason);
    }

    private OpenAiChatMessage toOpenAiMessage(InvokeMessage message) {
        return new OpenAiChatMessage(
                message.getRole().name().toLowerCase(),
                message.getContent()
        );
    }

    private JsonNode readRootObject(String responseBody) {
        try {
            JsonNode root = jsonMapper.readTree(responseBody);
            if (root == null || !root.isObject()) {
                throw new InvalidProviderResponseException("root is not a JSON object");
            }
            return root;
        } catch (InvalidProviderResponseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 无效 JSON：不携带原始响应内容。
            throw new InvalidProviderResponseException("body is not valid JSON");
        }
    }

    private String requireNonBlankText(JsonNode node, String field) {
        String value = requireText(node, field);
        if (value.isBlank()) {
            throw new InvalidProviderResponseException(field + " is blank");
        }
        return value;
    }

    private String requireText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isString()) {
            throw new InvalidProviderResponseException(field + " is missing or not a string");
        }
        return node.asString();
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidProviderResponseException(field + " is not a string");
        }
        return node.asString();
    }
}

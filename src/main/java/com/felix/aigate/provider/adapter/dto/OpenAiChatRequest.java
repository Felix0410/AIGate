package com.felix.aigate.provider.adapter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * OpenAI-Compatible Chat Completions 请求体，Provider 协议内部结构。
 *
 * <p>禁止作为 Runtime Controller 的请求/响应 DTO 暴露。
 * 可选字段为 null 时由 {@code NON_NULL} 策略直接省略，不设默认值；
 * 不包含 stream / tools / response_format 等本阶段不需要的字段。
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

    private String model;

    private List<OpenAiChatMessage> messages;

    private Double temperature;

    /** Java 侧 maxTokens，序列化为 OpenAI 协议的 max_tokens。 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}

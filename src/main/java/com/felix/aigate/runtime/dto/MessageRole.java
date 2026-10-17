package com.felix.aigate.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一模型调用消息角色。
 *
 * <p>Java 内部使用枚举，对外 JSON 固定为小写；通过 {@link JsonProperty} 同时约束序列化与反序列化，
 * 未知角色会触发反序列化异常，空角色由 Bean Validation 拦截。不引入任何 Provider/OpenAI SDK 枚举。
 */
public enum MessageRole {

    @JsonProperty("system")
    SYSTEM,

    @JsonProperty("user")
    USER,

    @JsonProperty("assistant")
    ASSISTANT
}

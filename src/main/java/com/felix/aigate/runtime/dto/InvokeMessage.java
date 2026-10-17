package com.felix.aigate.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一模型调用中的单条消息。保持极简，不含 tool calls / 图片 / 音频 / 多模态结构。
 */
@Getter
@Setter
public class InvokeMessage {

    @NotNull
    private MessageRole role;

    @NotBlank
    private String content;
}

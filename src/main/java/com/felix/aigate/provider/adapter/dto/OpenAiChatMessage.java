package com.felix.aigate.provider.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OpenAI-Compatible Chat Completions 单条消息，Provider 协议内部结构。
 *
 * <p>role 为已转换好的小写字符串（system / user / assistant），禁止作为 Runtime DTO 暴露。
 */
@Getter
@AllArgsConstructor
public class OpenAiChatMessage {

    private String role;

    private String content;
}

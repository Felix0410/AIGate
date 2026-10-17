package com.felix.aigate.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AIGate 返回给客户端的统一模型调用响应。
 *
 * <p>仅暴露 content / model / finishReason，不含 usage / cost / requestId / providerResponse /
 * deploymentId / routingMetadata，也不复用任何 Provider SDK 响应类。
 */
@Getter
@AllArgsConstructor
public class InvokeResponse {

    private String content;

    private String model;

    private String finishReason;
}

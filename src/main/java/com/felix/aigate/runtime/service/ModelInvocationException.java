package com.felix.aigate.runtime.service;

import com.felix.aigate.runtime.exception.ProxyErrorType;
import lombok.Getter;

/**
 * 模型代理编排与出站调用失败时抛出的统一业务异常，携带一个稳定的 {@link ProxyErrorType} 分类。
 *
 * <p>P2-T09：错误码 / HTTP 语义 / 对外消息全部来自 {@link ProxyErrorType} 的固定脱敏文本，
 * 异常只接受 errorType，绝不携带 cause，也绝不接收上游响应体、URL、Credential 等敏感内容，
 * 因此可以安全地交给 {@code GlobalExceptionHandler} 做分类映射而不泄露内部信息。
 */
@Getter
public class ModelInvocationException extends RuntimeException {

    private final ProxyErrorType errorType;

    public ModelInvocationException(ProxyErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }
}

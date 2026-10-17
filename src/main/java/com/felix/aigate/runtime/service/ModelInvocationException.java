package com.felix.aigate.runtime.service;

/**
 * 模型代理编排与出站调用失败时抛出的统一业务异常。
 *
 * <p>P2-T08 只保证"明确失败、不伪造成功、不泄露敏感信息"：错误描述固定且非敏感，
 * 不携带 API Key、Provider Credential、上游 URL/响应体等。
 * 到具体 HTTP 状态码与错误码（PROVIDER_*）的正式映射属于 P2-T09。
 */
public class ModelInvocationException extends RuntimeException {

    public ModelInvocationException(String message) {
        super(message);
    }
}

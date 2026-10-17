package com.felix.aigate.provider.adapter;

/**
 * Provider 成功响应结构不合法（缺失/类型错误/无效 JSON 等）时抛出。
 *
 * <p>错误描述固定且非敏感，绝不携带上游响应体内容。
 * 到 PROVIDER_INVALID_RESPONSE 的统一 HTTP 错误映射属于 P2-T09，本阶段不处理。
 */
public class InvalidProviderResponseException extends RuntimeException {

    public InvalidProviderResponseException(String detail) {
        super("Invalid provider response: " + detail);
    }
}

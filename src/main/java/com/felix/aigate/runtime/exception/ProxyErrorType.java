package com.felix.aigate.runtime.exception;

import org.springframework.http.HttpStatus;

/**
 * 模型代理链路的稳定错误分类：把本地配置错误、上游传输/状态错误、上游协议结构错误
 * 统一映射为 AIGate 对外的固定错误码 + HTTP 语义 + 脱敏消息。
 *
 * <p>约定（P2-T09）：
 * <ul>
 *   <li>本地配置 / 数据前置错误：零出站，绝不伪装成 Provider HTTP 故障。</li>
 *   <li>Provider 4xx/5xx/连接/超时：502 / 503 / 504 是 AIGate 对下游客户端的语义映射，
 *       不是简单透传上游状态；Provider 401 不等于 AIGate 认证失败，429 不等于 AIGate 配额不足。</li>
 *   <li>Provider HTTP 200 但 JSON 不符合协议：{@code PROVIDER_INVALID_RESPONSE}。</li>
 * </ul>
 *
 * <p>{@code message} 是面向客户端的固定、脱敏文本，绝不包含上游响应体、内部 URL、
 * Authorization、Credential、Prompt 或原始异常信息。
 */
public enum ProxyErrorType {

    // ---- 本地配置 / 数据前置错误（均零出站）----
    APPLICATION_NOT_FOUND("APPLICATION_NOT_FOUND", HttpStatus.NOT_FOUND,
            "Application not found"),
    MODEL_DEPLOYMENT_NOT_CONFIGURED("MODEL_DEPLOYMENT_NOT_CONFIGURED", HttpStatus.CONFLICT,
            "No default model deployment configured"),
    MODEL_DEPLOYMENT_NOT_FOUND("MODEL_DEPLOYMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
            "Default model deployment not found"),
    MODEL_DEPLOYMENT_DISABLED("MODEL_DEPLOYMENT_DISABLED", HttpStatus.CONFLICT,
            "Default model deployment is disabled"),
    PROVIDER_NOT_FOUND("PROVIDER_NOT_FOUND", HttpStatus.NOT_FOUND,
            "Provider not found"),
    // 本地能力缺口：没有对应 ProviderType 的 Adapter，属于服务端未实现，不是上游 HTTP 故障。
    PROVIDER_TYPE_UNSUPPORTED("PROVIDER_TYPE_UNSUPPORTED", HttpStatus.NOT_IMPLEMENTED,
            "Provider type is not supported"),

    // ---- 上游传输 / 状态错误 ----
    PROVIDER_BAD_REQUEST("PROVIDER_BAD_REQUEST", HttpStatus.BAD_GATEWAY,
            "Upstream provider rejected the request"),
    PROVIDER_AUTH_FAILED("PROVIDER_AUTH_FAILED", HttpStatus.BAD_GATEWAY,
            "Upstream provider authentication failed"),
    PROVIDER_RATE_LIMITED("PROVIDER_RATE_LIMITED", HttpStatus.SERVICE_UNAVAILABLE,
            "Upstream provider is rate limiting requests"),
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
            "Upstream provider is unavailable"),
    PROVIDER_CONNECTION_ERROR("PROVIDER_CONNECTION_ERROR", HttpStatus.BAD_GATEWAY,
            "Cannot reach the upstream provider"),
    PROVIDER_TIMEOUT("PROVIDER_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
            "Upstream provider request timed out"),

    // ---- 上游 HTTP 200 但响应结构不符合协议 ----
    PROVIDER_INVALID_RESPONSE("PROVIDER_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
            "Upstream provider returned an invalid response");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ProxyErrorType(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}

package com.felix.aigate.runtime.client;

import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.service.ModelInvocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Provider 出站 HTTP 组件：只负责用共享的 RestClient POST 一次并返回原始 JSON 字符串。
 *
 * <p>不做协议转换（属于 Adapter），不重试、不熔断。失败时按 P2-T09 约定分类为携带
 * {@link ProxyErrorType} 的 {@link ModelInvocationException}：
 * <ul>
 *   <li>只读取安全的上游 HTTP 状态码，绝不读取 / 保存 / 传播上游响应体。</li>
 *   <li>连接失败与超时分别归类；{@link HttpTimeoutException}（含其子类 HttpConnectTimeoutException）
 *       视为超时，不能因链路里同时存在 {@link java.net.ConnectException} 而降级为普通连接失败。</li>
 *   <li>无法准确识别的 {@link ResourceAccessException} 安全归类为连接/网络故障。</li>
 * </ul>
 * 抛出的异常不带 cause，避免上游 URL / 响应体 / Header 落入兜底日志。
 */
@Component
@RequiredArgsConstructor
public class ProviderHttpClient {

    private final RestClient providerRestClient;

    public String postChatCompletions(String url, Object providerRequest, String providerCredential) {
        try {
            RestClient.RequestBodySpec spec = providerRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);

            // 无 Provider Credential 时不附加 Authorization；客户端 AIGate API Key 永不进入此处。
            if (providerCredential != null) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + providerCredential);
            }

            return spec.body(providerRequest)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            // 仅提取状态码；不读取 getResponseBodyAsString()，不携带上游响应体或原始异常。
            throw new ModelInvocationException(classifyResponseStatus(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new ModelInvocationException(classifyNetworkFailure(exception));
        }
    }

    /** 上游非成功 HTTP 状态码 -> 安全分类；除明确约定的 400/401/403/429 外，其余一律 PROVIDER_UNAVAILABLE。 */
    static ProxyErrorType classifyResponseStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> ProxyErrorType.PROVIDER_BAD_REQUEST;
            case 401, 403 -> ProxyErrorType.PROVIDER_AUTH_FAILED;
            case 429 -> ProxyErrorType.PROVIDER_RATE_LIMITED;
            default -> ProxyErrorType.PROVIDER_UNAVAILABLE;
        };
    }

    /**
     * ResourceAccessException -> 超时或连接故障。沿 cause chain 查找代表性网络异常类型，
     * 超时优先（HttpConnectTimeoutException 是 HttpTimeoutException 的子类，会被一并识别为超时）。
     */
    static ProxyErrorType classifyNetworkFailure(Throwable throwable) {
        if (hasCauseInstanceOf(throwable, HttpTimeoutException.class)
                || hasCauseInstanceOf(throwable, SocketTimeoutException.class)) {
            return ProxyErrorType.PROVIDER_TIMEOUT;
        }
        return ProxyErrorType.PROVIDER_CONNECTION_ERROR;
    }

    private static boolean hasCauseInstanceOf(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}

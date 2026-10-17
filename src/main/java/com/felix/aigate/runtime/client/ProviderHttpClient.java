package com.felix.aigate.runtime.client;

import com.felix.aigate.runtime.service.ModelInvocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Provider 出站 HTTP 组件：只负责用共享的 RestClient POST 一次并返回原始 JSON 字符串。
 *
 * <p>不做协议转换（属于 Adapter），不重试、不熔断。失败时抛出固定、非敏感的
 * {@link ModelInvocationException}，绝不携带上游响应体，避免落入全局兜底日志造成泄露。
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
        } catch (RestClientResponseException | ResourceAccessException exception) {
            // 故意不携带 cause：上游状态码/响应体/URL 可能敏感，正式分类映射留给 P2-T09。
            throw new ModelInvocationException("Provider request failed");
        }
    }
}

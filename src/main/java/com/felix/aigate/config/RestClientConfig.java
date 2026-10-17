package com.felix.aigate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Provider 出站 HTTP 客户端配置：同步非流式 RestClient，带有限的连接/读取超时。
 *
 * <p>使用 Spring Framework 提供的 {@link JdkClientHttpRequestFactory}，不引入 WebFlux/Feign/第三方 SDK。
 * 超时值可通过简单配置属性覆盖；默认值是 Happy Path 的有界兜底，并非性能调优结果。
 * 不重试、不熔断、不异步。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient providerRestClient(
            @Value("${aigate.provider.connect-timeout-seconds:3}") long connectTimeoutSeconds,
            @Value("${aigate.provider.read-timeout-seconds:60}") long readTimeoutSeconds) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}

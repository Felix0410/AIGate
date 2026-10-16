package com.felix.aigate.config;

import com.felix.aigate.security.ApiKeyAuthenticationFilter;
import com.felix.aigate.security.ApiKeyAuthenticationService;
import com.felix.aigate.security.RestAccessDeniedHandler;
import com.felix.aigate.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * Runtime 链：/v1/** 只认 Application API Key（Bearer），不启用 HTTP Basic。
     * ApiKeyAuthenticationFilter 建好 SecurityContext 后，再由 AuthorizationFilter 判断 authenticated()。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain runtimeFilterChain(HttpSecurity http) throws Exception {

        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
                new ApiKeyAuthenticationFilter(apiKeyAuthenticationService, authenticationEntryPoint);

        http
                .securityMatcher("/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(apiKeyAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }

    /**
     * Management 链：其余请求沿用原有行为，/api/** 只用 HTTP Basic；不加入 API Key Filter，两套认证双向隔离。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .httpBasic(httpBasic ->
                        httpBasic.authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }
}

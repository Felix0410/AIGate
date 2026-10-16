package com.felix.aigate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runtime 认证过滤器：只负责 HTTP 与 SecurityContext，不查库、不做哈希。
 *
 * <p>仅在 /v1/** 责任链内注册。无 Authorization 或非 Bearer 时一律放行给后续授权判断，
 * 不在这里手写 401；只有真正认证失败才复用 {@link RestAuthenticationEntryPoint} 输出统一错误。
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 交给后续 /v1/** 的 authenticated() 统一返回 401，此处不报错、也不尝试 Basic。
            filterChain.doFilter(request, response);
            return;
        }

        String rawApiKey = header.substring(BEARER_PREFIX.length()).trim();

        try {
            ApplicationIdentity identity = apiKeyAuthenticationService.authenticate(rawApiKey);

            // credentials = null：绝不把完整 Bearer Token / secret 存入 SecurityContext。
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    identity, null, AuthorityUtils.NO_AUTHORITIES);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        }
    }
}

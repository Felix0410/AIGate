package com.felix.aigate.security.support;

import com.felix.aigate.security.ApplicationIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仅供测试注册的 Runtime 端点：证明 /v1/** 经 API Key 认证后 principal 确为 {@link ApplicationIdentity}。
 *
 * <p>只暴露身份字段，不含 apiKey / secret / keyHash。不放进 src/main，避免污染生产语义。
 */
@RestController
@RequestMapping("/v1/test")
public class RuntimeSecurityTestController {

    @GetMapping("/identity")
    public Map<String, Object> identity(Authentication authentication) {

        ApplicationIdentity identity = (ApplicationIdentity) authentication.getPrincipal();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationId", identity.applicationId());
        body.put("applicationName", identity.applicationName());
        body.put("teamId", identity.teamId());
        return body;
    }
}

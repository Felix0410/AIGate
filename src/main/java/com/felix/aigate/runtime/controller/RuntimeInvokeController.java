package com.felix.aigate.runtime.controller;

import com.felix.aigate.invocation.service.InvocationTrackingFacade;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.security.ApplicationIdentity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式 Runtime 调用入口：POST /v1/invoke。
 *
 * <p>applicationId 只能来自 P2-T05 认证后的 {@link ApplicationIdentity}，绝不从请求体/查询参数/Header 接收。
 * Controller 不查库、不解密、不发 HTTP，全部委托 {@link InvocationTrackingFacade}。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class RuntimeInvokeController {

    private final InvocationTrackingFacade invocationTrackingFacade;

    @PostMapping("/invoke")
    public InvokeResponse invoke(Authentication authentication,
                                 @Valid @RequestBody InvokeRequest request) {

        ApplicationIdentity identity = (ApplicationIdentity) authentication.getPrincipal();

        return invocationTrackingFacade.invoke(identity.applicationId(), request);
    }
}
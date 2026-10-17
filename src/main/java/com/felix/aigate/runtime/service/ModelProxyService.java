package com.felix.aigate.runtime.service;

import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.credential.service.CredentialService;
import com.felix.aigate.deployment.entity.ModelDeployment;
import com.felix.aigate.deployment.mapper.ModelDeploymentMapper;
import com.felix.aigate.provider.adapter.ProviderAdapter;
import com.felix.aigate.provider.adapter.ProviderAdapterRegistry;
import com.felix.aigate.provider.entity.Provider;
import com.felix.aigate.provider.mapper.ProviderMapper;
import com.felix.aigate.runtime.client.ProviderHttpClient;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 单模型代理编排：Application -> 默认 Deployment -> Provider -> Adapter -> Credential -> HTTP。
 *
 * <p>不在此写协议 JSON（交给 Adapter），不在此处理 HTTP（交给 ProviderHttpClient）。
 * 任一前置校验失败都必须在发起出站请求之前明确失败，绝不 fallback、绝不伪造成功响应。
 */
@Service
@RequiredArgsConstructor
public class ModelProxyService {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    private final ApplicationMapper applicationMapper;
    private final ModelDeploymentMapper modelDeploymentMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final CredentialService credentialService;
    private final ProviderHttpClient providerHttpClient;

    public InvokeResponse invoke(Long applicationId, InvokeRequest request) {

        // 1. Application：以数据库当前配置为准，不凭 Authentication 推断默认 Deployment。
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new ModelInvocationException("Application not found");
        }

        // 2. 默认 Deployment：为空立即拒绝，不选择其他 Deployment、不 fallback。
        Long defaultDeploymentId = application.getDefaultDeploymentId();
        if (defaultDeploymentId == null) {
            throw new ModelInvocationException("No default deployment configured");
        }

        // 3. ModelDeployment：必须存在且运行时 enabled（不依赖外键代替 enabled 检查）。
        ModelDeployment deployment = modelDeploymentMapper.selectById(defaultDeploymentId);
        if (deployment == null) {
            throw new ModelInvocationException("Default deployment not found");
        }
        if (!Boolean.TRUE.equals(deployment.getEnabled())) {
            throw new ModelInvocationException("Default deployment is disabled");
        }

        // 4. Provider 必须存在。
        Provider provider = providerMapper.selectById(deployment.getProviderId());
        if (provider == null) {
            throw new ModelInvocationException("Provider not found");
        }

        // 5. 由 Registry 按 ProviderType 选择 Adapter；不支持则在此失败，仍不出站。
        ProviderAdapter adapter = providerAdapterRegistry.select(provider.getType());

        // 6. remoteModelName 只能来自 Deployment。
        Object providerRequest = adapter.buildRequest(request, deployment.getRemoteModelName());

        // 7. 有加密凭证才解密；无凭证则不附加上游 Authorization。客户端 API Key 永不参与。
        String providerCredential = deployment.getEncryptedCredential() == null
                ? null
                : credentialService.decrypt(deployment.getEncryptedCredential());

        // 8. 出站 POST，返回上游原始 JSON。
        String url = buildChatCompletionsUrl(deployment.getEndpointUrl());
        String responseBody = providerHttpClient.postChatCompletions(url, providerRequest, providerCredential);

        // 9. 交回 Adapter 解析为统一响应，不重复解析 choices。
        return adapter.parseResponse(responseBody);
    }

    /** base URL 去除末尾斜杠后拼接 /chat/completions，不重复 /v1，不改造其他路径。 */
    private String buildChatCompletionsUrl(String endpointUrl) {
        String base = endpointUrl.endsWith("/")
                ? endpointUrl.substring(0, endpointUrl.length() - 1)
                : endpointUrl;
        return base + CHAT_COMPLETIONS_SUFFIX;
    }
}

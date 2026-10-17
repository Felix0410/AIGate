package com.felix.aigate.runtime.service;

import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.credential.service.CredentialService;
import com.felix.aigate.deployment.entity.ModelDeployment;
import com.felix.aigate.deployment.mapper.ModelDeploymentMapper;
import com.felix.aigate.provider.adapter.OpenAiCompatibleProviderAdapter;
import com.felix.aigate.provider.adapter.ProviderAdapterRegistry;
import com.felix.aigate.provider.entity.Provider;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.provider.mapper.ProviderMapper;
import com.felix.aigate.runtime.client.ProviderHttpClient;
import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.dto.InvokeMessage;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.dto.MessageRole;
import com.felix.aigate.provider.adapter.dto.OpenAiChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ModelProxyService 编排单元测试：默认 Deployment 解析、enabled 检查、Provider/Adapter 选择、
 * remoteModelName 与 Credential 处理，以及"前置失败绝不出站"。真实 Registry/Adapter，Mapper 与 HTTP 用 Mock。
 * 不启 Spring、不连数据库、不发真实 HTTP。
 */
@ExtendWith(MockitoExtension.class)
class ModelProxyServiceTest {

    private static final long APP_ID = 1L;
    private static final long DEPLOYMENT_ID = 10L;
    private static final long PROVIDER_ID = 5L;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ModelDeploymentMapper modelDeploymentMapper;
    @Mock
    private ProviderMapper providerMapper;
    @Mock
    private CredentialService credentialService;
    @Mock
    private ProviderHttpClient providerHttpClient;

    private ModelProxyService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        ProviderAdapterRegistry registry = new ProviderAdapterRegistry(
                List.of(new OpenAiCompatibleProviderAdapter(jsonMapper)));
        service = new ModelProxyService(
                applicationMapper, modelDeploymentMapper, providerMapper,
                registry, credentialService, providerHttpClient);
    }

    @Test
    @DisplayName("Happy Path：加载默认 Deployment、remoteModelName 生效、Credential 为空时不出站 Authorization")
    void shouldInvokeDefaultDeploymentWithoutCredential() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(true, PROVIDER_ID, "remote-model", null, "http://localhost:9000/v1"));
        stubProvider(ProviderType.OPENAI_COMPATIBLE);
        when(providerHttpClient.postChatCompletions(anyString(), any(), nullable(String.class)))
                .thenReturn(validProviderResponse());

        InvokeResponse response = service.invoke(APP_ID, invokeRequest());

        assertEquals("HashMap is a hash table.", response.getContent());
        assertEquals("remote-model", response.getModel());
        assertEquals("stop", response.getFinishReason());

        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> credentialCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerHttpClient).postChatCompletions(
                urlCaptor.capture(), requestCaptor.capture(), credentialCaptor.capture());

        assertEquals("http://localhost:9000/v1/chat/completions", urlCaptor.getValue());
        assertEquals("remote-model", ((OpenAiChatRequest) requestCaptor.getValue()).getModel());
        // 未配置 Credential：出站凭证为 null，Adapter 层不会附加 Authorization。
        assertNull(credentialCaptor.getValue());
        verifyNoInteractions(credentialService);
    }

    @Test
    @DisplayName("配置 Credential 时解密并作为 Provider Authorization 传出")
    void shouldDecryptCredentialWhenPresent() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(true, PROVIDER_ID, "remote-model", "v1:encrypted", "http://localhost:9000/v1"));
        stubProvider(ProviderType.OPENAI_COMPATIBLE);
        when(credentialService.decrypt("v1:encrypted")).thenReturn("sk-provider-secret");
        when(providerHttpClient.postChatCompletions(anyString(), any(), nullable(String.class)))
                .thenReturn(validProviderResponse());

        service.invoke(APP_ID, invokeRequest());

        verify(credentialService).decrypt("v1:encrypted");
        ArgumentCaptor<String> credentialCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerHttpClient).postChatCompletions(anyString(), any(), credentialCaptor.capture());
        assertEquals("sk-provider-secret", credentialCaptor.getValue());
    }

    @Test
    @DisplayName("endpointUrl 末尾斜杠不会产生双斜杠")
    void shouldNormalizeTrailingSlash() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(true, PROVIDER_ID, "remote-model", null, "http://localhost:9000/v1/"));
        stubProvider(ProviderType.OPENAI_COMPATIBLE);
        when(providerHttpClient.postChatCompletions(anyString(), any(), nullable(String.class)))
                .thenReturn(validProviderResponse());

        service.invoke(APP_ID, invokeRequest());

        verify(providerHttpClient).postChatCompletions(
                eq("http://localhost:9000/v1/chat/completions"), any(), nullable(String.class));
    }

    @Test
    @DisplayName("Application 不存在 -> 失败且不出站")
    void applicationNotFoundShouldNotCallUpstream() {

        when(applicationMapper.selectById(APP_ID)).thenReturn(null);

        assertInvocationFails(ProxyErrorType.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("未绑定默认 Deployment -> 失败且不出站")
    void missingDefaultDeploymentShouldNotCallUpstream() {

        stubApplication(defaultDeployment(null));

        assertInvocationFails(ProxyErrorType.MODEL_DEPLOYMENT_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("Deployment disabled -> 失败且不出站")
    void disabledDeploymentShouldNotCallUpstream() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(false, PROVIDER_ID, "remote-model", null, "http://localhost:9000/v1"));

        assertInvocationFails(ProxyErrorType.MODEL_DEPLOYMENT_DISABLED);
    }

    @Test
    @DisplayName("Deployment 不存在 -> 失败且不出站")
    void deploymentNotFoundShouldNotCallUpstream() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        when(modelDeploymentMapper.selectById(DEPLOYMENT_ID)).thenReturn(null);

        assertInvocationFails(ProxyErrorType.MODEL_DEPLOYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Provider 不存在 -> 失败且不出站")
    void providerNotFoundShouldNotCallUpstream() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(true, PROVIDER_ID, "remote-model", null, "http://localhost:9000/v1"));
        when(providerMapper.selectById(PROVIDER_ID)).thenReturn(null);

        assertInvocationFails(ProxyErrorType.PROVIDER_NOT_FOUND);
    }

    @Test
    @DisplayName("ProviderType 无可用 Adapter -> 失败且不出站")
    void unsupportedProviderTypeShouldNotCallUpstream() {

        stubApplication(defaultDeployment(DEPLOYMENT_ID));
        stubDeployment(deployment(true, PROVIDER_ID, "remote-model", null, "http://localhost:9000/v1"));
        // Registry 只注册了 OPENAI_COMPATIBLE；provider.getType() 返回 null 应无匹配。
        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        provider.setType(null);
        when(providerMapper.selectById(PROVIDER_ID)).thenReturn(provider);

        assertInvocationFails(ProxyErrorType.PROVIDER_TYPE_UNSUPPORTED);
    }

    // ---- helpers ----

    /** 断言 invoke 以指定 {@link ProxyErrorType} 失败，且绝不向 Provider 出站。 */
    private void assertInvocationFails(ProxyErrorType expected) {
        ModelInvocationException exception = assertThrows(
                ModelInvocationException.class, () -> service.invoke(APP_ID, invokeRequest()));
        assertEquals(expected, exception.getErrorType());
        verifyNoInteractions(providerHttpClient);
    }

    private void stubApplication(Application application) {
        when(applicationMapper.selectById(APP_ID)).thenReturn(application);
    }

    private void stubDeployment(ModelDeployment deployment) {
        when(modelDeploymentMapper.selectById(DEPLOYMENT_ID)).thenReturn(deployment);
    }

    private void stubProvider(ProviderType type) {
        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        provider.setType(type);
        when(providerMapper.selectById(PROVIDER_ID)).thenReturn(provider);
    }

    private Application defaultDeployment(Long defaultDeploymentId) {
        Application application = new Application();
        application.setId(APP_ID);
        application.setDefaultDeploymentId(defaultDeploymentId);
        return application;
    }

    private ModelDeployment deployment(boolean enabled, Long providerId, String remoteModelName,
                                       String encryptedCredential, String endpointUrl) {
        ModelDeployment deployment = new ModelDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setEnabled(enabled);
        deployment.setProviderId(providerId);
        deployment.setRemoteModelName(remoteModelName);
        deployment.setEncryptedCredential(encryptedCredential);
        deployment.setEndpointUrl(endpointUrl);
        return deployment;
    }

    private InvokeRequest invokeRequest() {
        InvokeMessage message = new InvokeMessage();
        message.setRole(MessageRole.USER);
        message.setContent("Explain HashMap.");
        InvokeRequest request = new InvokeRequest();
        request.setMessages(List.of(message));
        request.setTemperature(0.7);
        request.setMaxTokens(500);
        return request;
    }

    private String validProviderResponse() {
        return """
                {"model": "remote-model",
                 "choices": [{"index": 0, "message": {"role": "assistant", "content": "HashMap is a hash table."}, "finish_reason": "stop"}]}
                """;
    }
}

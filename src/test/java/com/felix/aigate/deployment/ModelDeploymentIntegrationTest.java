package com.felix.aigate.deployment;

import com.felix.aigate.credential.service.CredentialService;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.deployment.dto.request.UpdateModelDeploymentRequest;
import com.felix.aigate.deployment.mapper.ModelDeploymentMapper;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ModelDeployment 集成测试：创建 / provider-model 缺失 404 / name 重复 409 /
 * 同名更新 / endpointUrl 空 400 / 删除被引用的 Provider、Model -> 409 RESOURCE_CONFLICT。
 */
class ModelDeploymentIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ModelDeploymentMapper modelDeploymentMapper;

    @Autowired
    private CredentialService credentialService;

    @Test
    @DisplayName("创建 Deployment -> 200，providerId/modelId 正确")
    void createDeploymentShouldSucceed() throws Exception {

        long providerId = createProvider("deployment-it-provider");
        long modelId = createModel("deployment-it-model");

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createRequest("deployment-it-create", providerId, modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("deployment-it-create"))
                .andExpect(jsonPath("$.providerId").value(providerId))
                .andExpect(jsonPath("$.modelId").value(modelId));
    }

    @Test
    @DisplayName("providerId 不存在 -> 404 PROVIDER_NOT_FOUND")
    void createWithMissingProviderShouldReturn404() throws Exception {

        long modelId = createModel("deployment-it-missing-provider-model");

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createRequest("deployment-it-missing-provider", 999999999L, modelId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("modelId 不存在 -> 404 MODEL_NOT_FOUND")
    void createWithMissingModelShouldReturn404() throws Exception {

        long providerId = createProvider("deployment-it-missing-model-provider");

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createRequest("deployment-it-missing-model", providerId, 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_FOUND"));
    }

    @Test
    @DisplayName("Deployment name 重复 -> 409 MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS")
    void duplicateDeploymentNameShouldReturn409() throws Exception {

        long providerId = createProvider("deployment-it-dup-provider");
        long modelId = createModel("deployment-it-dup-model");
        CreateModelDeploymentRequest request =
                createRequest("deployment-it-dup", providerId, modelId);

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("update 使用原名称 -> 200（excludeId 不误判自己）")
    void updateWithSameNameShouldSucceed() throws Exception {

        long providerId = createProvider("deployment-it-upd-provider");
        long modelId = createModel("deployment-it-upd-model");
        long id = createDeployment("deployment-it-update-same", providerId, modelId);

        UpdateModelDeploymentRequest update = new UpdateModelDeploymentRequest();
        update.setName("deployment-it-update-same");
        update.setProviderId(providerId);
        update.setModelId(modelId);
        update.setEndpointUrl("http://localhost:9999/v1");
        update.setRemoteModelName("test-model");
        update.setEnabled(true);

        mockMvc.perform(authed(put("/api/model-deployments/" + id))
                        .contentType(APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("deployment-it-update-same"));
    }

    @Test
    @DisplayName("endpointUrl 空字符串 -> 400 VALIDATION_ERROR，errors.endpointUrl 存在")
    void blankEndpointUrlShouldReturn400() throws Exception {

        long providerId = createProvider("deployment-it-blank-provider");
        long modelId = createModel("deployment-it-blank-model");

        CreateModelDeploymentRequest request =
                createRequest("deployment-it-blank-url", providerId, modelId);
        request.setEndpointUrl("");

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.endpointUrl").exists());
    }

    @Test
    @DisplayName("删除被 Deployment 引用的 Provider -> 409 RESOURCE_CONFLICT")
    void deleteProviderReferencedByDeploymentShouldReturn409() throws Exception {

        long providerId = createProvider("deployment-it-ref-provider");
        long modelId = createModel("deployment-it-ref-model-provider");
        createDeployment("deployment-it-ref-provider-del", providerId, modelId);

        mockMvc.perform(authed(delete("/api/providers/" + providerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    @DisplayName("删除被 Deployment 引用的 Model -> 409 RESOURCE_CONFLICT")
    void deleteModelReferencedByDeploymentShouldReturn409() throws Exception {

        long providerId = createProvider("deployment-it-ref-model-provider2");
        long modelId = createModel("deployment-it-ref-model-del");
        createDeployment("deployment-it-ref-model-del-dep", providerId, modelId);

        mockMvc.perform(authed(delete("/api/models/" + modelId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    @DisplayName("创建带 credential 的 Deployment -> 200")
    void createWithCredentialShouldSucceed() throws Exception {

        long providerId = createProvider("dep-cred-create-provider");
        long modelId = createModel("dep-cred-create-model");

        CreateModelDeploymentRequest request =
                createRequest("dep-cred-create", providerId, modelId);
        request.setCredential("sk-test-secret");

        mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("dep-cred-create"));
    }

    @Test
    @DisplayName("创建带 credential 的 Deployment -> 响应不泄露 credential")
    void responseShouldNotLeakCredential() throws Exception {

        long providerId = createProvider("dep-cred-leak-provider");
        long modelId = createModel("dep-cred-leak-model");

        CreateModelDeploymentRequest request =
                createRequest("dep-cred-leak", providerId, modelId);
        request.setCredential("sk-test-secret");

        String body = mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential").doesNotExist())
                .andExpect(jsonPath("$.encryptedCredential").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(body.contains("sk-test-secret"));
    }

    @Test
    @DisplayName("credential 以密文落库：DB 值 != 明文、以 v1: 开头、可解密还原")
    void credentialShouldBeStoredEncrypted() throws Exception {

        long providerId = createProvider("dep-cred-db-provider");
        long modelId = createModel("dep-cred-db-model");

        long id = createDeploymentWithCredential(
                "dep-cred-db", providerId, modelId, "sk-test-secret");

        String stored = readStoredCredential(id);

        assertNotNull(stored);
        assertNotEquals("sk-test-secret", stored);
        assertTrue(stored.startsWith("v1:"));
        assertEquals("sk-test-secret", credentialService.decrypt(stored));
    }

    @Test
    @DisplayName("credential = null -> 200，DB encryptedCredential 为 null")
    void createWithNullCredentialShouldSucceed() throws Exception {

        long providerId = createProvider("dep-cred-null-provider");
        long modelId = createModel("dep-cred-null-model");

        long id = createDeploymentWithCredential(
                "dep-cred-null", providerId, modelId, null);

        assertNull(readStoredCredential(id));
    }

    @Test
    @DisplayName("更新 credential -> DB 密文改变，解密得到新值")
    void updateCredentialShouldReEncrypt() throws Exception {

        long providerId = createProvider("dep-cred-upd-provider");
        long modelId = createModel("dep-cred-upd-model");

        long id = createDeploymentWithCredential(
                "dep-cred-upd", providerId, modelId, "old-secret");
        String oldStored = readStoredCredential(id);

        mockMvc.perform(authed(put("/api/model-deployments/" + id))
                        .contentType(APPLICATION_JSON)
                        .content(json(updateRequest(
                                "dep-cred-upd", providerId, modelId, "new-secret"))))
                .andExpect(status().isOk());

        String newStored = readStoredCredential(id);

        assertNotEquals(oldStored, newStored);
        assertNotEquals("old-secret", newStored);
        assertNotEquals("new-secret", newStored);
        assertEquals("new-secret", credentialService.decrypt(newStored));
    }

    @Test
    @DisplayName("PUT credential = null -> 全量更新语义下 DB encryptedCredential 变 null")
    void updateWithNullCredentialShouldClear() throws Exception {

        long providerId = createProvider("dep-cred-clear-provider");
        long modelId = createModel("dep-cred-clear-model");

        long id = createDeploymentWithCredential(
                "dep-cred-clear", providerId, modelId, "old-secret");
        assertNotNull(readStoredCredential(id));

        mockMvc.perform(authed(put("/api/model-deployments/" + id))
                        .contentType(APPLICATION_JSON)
                        .content(json(updateRequest(
                                "dep-cred-clear", providerId, modelId, null))))
                .andExpect(status().isOk());

        assertNull(readStoredCredential(id));
    }

    // ---- helpers ----

    private CreateModelDeploymentRequest createRequest(
            String name, long providerId, long modelId) {

        CreateModelDeploymentRequest request = new CreateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl("http://localhost:9999/v1");
        request.setRemoteModelName("test-model");
        request.setEnabled(true);
        return request;
    }

    private long createDeployment(String name, long providerId, long modelId) throws Exception {

        String body = mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createRequest(name, providerId, modelId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createProvider(String name) throws Exception {

        CreateProviderRequest request = new CreateProviderRequest();
        request.setName(name);
        request.setType(ProviderType.OPENAI_COMPATIBLE);

        String body = mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createModel(String name) throws Exception {

        CreateModelRequest request = new CreateModelRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createDeploymentWithCredential(
            String name, long providerId, long modelId, String credential) throws Exception {

        CreateModelDeploymentRequest request = createRequest(name, providerId, modelId);
        request.setCredential(credential);

        String body = mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private UpdateModelDeploymentRequest updateRequest(
            String name, long providerId, long modelId, String credential) {

        UpdateModelDeploymentRequest update = new UpdateModelDeploymentRequest();
        update.setName(name);
        update.setProviderId(providerId);
        update.setModelId(modelId);
        update.setEndpointUrl("http://localhost:9999/v1");
        update.setRemoteModelName("test-model");
        update.setEnabled(true);
        update.setCredential(credential);
        return update;
    }

    /** 直接查库读取落库后的 credential，验证真实存储内容。 */
    private String readStoredCredential(long id) {
        return modelDeploymentMapper.selectById(id).getEncryptedCredential();
    }
}

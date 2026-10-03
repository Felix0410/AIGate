package com.felix.aigate.deployment;

import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.deployment.dto.request.UpdateModelDeploymentRequest;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}

package com.felix.aigate.application;

import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.application.dto.request.UpdateApplicationRequest;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application 集成测试：核心链路（Team 关联 / 404 / 重名 409）
 * + 默认 Deployment 绑定的 null/绑定/切换/清空/404，以及被引用 Deployment 删除 409（真实 FK RESTRICT）。
 */
class ApplicationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("在已存在 Team 下创建 Application -> 200")
    void createApplicationWithExistingTeamShouldSucceed() throws Exception {

        long teamId = createTeam("application-it-team");

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplication("application-it-create", teamId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("application-it-create"))
                .andExpect(jsonPath("$.teamId").value(teamId));
    }

    @Test
    @DisplayName("指向不存在 Team 创建 Application -> 404 TEAM_NOT_FOUND")
    void createApplicationWithMissingTeamShouldReturn404() throws Exception {

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplication("application-it-ghost", 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("重复 name 创建 Application -> 409 APPLICATION_NAME_ALREADY_EXISTS")
    void duplicateApplicationNameShouldReturn409() throws Exception {

        long teamId = createTeam("application-it-dup-team");
        CreateApplicationRequest request = createApplication("application-it-dup", teamId);

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_NAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("创建 Application 不指定默认 Deployment 应成功")
    void createApplicationWithoutDeploymentShouldSucceed() throws Exception {

        long teamId = createTeam("app-dep-none-team");

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplication("app-dep-none", teamId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("app-dep-none"))
                .andExpect(jsonPath("$.defaultDeploymentId").value(nullValue()));
    }

    @Test
    @DisplayName("创建 Application 绑定存在 Deployment 应成功")
    void createApplicationWithExistingDeploymentShouldSucceed() throws Exception {

        long teamId = createTeam("app-dep-bind-team");
        long deploymentId = createReadyDeployment("app-dep-bind");

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplicationWithDeployment(
                                "app-dep-bind", teamId, deploymentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.defaultDeploymentId").value(deploymentId));
    }

    @Test
    @DisplayName("绑定不存在 Deployment 应返回 404 MODEL_DEPLOYMENT_NOT_FOUND")
    void createApplicationWithMissingDeploymentShouldReturn404() throws Exception {

        long teamId = createTeam("app-dep-missing-team");

        mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplicationWithDeployment(
                                "app-dep-missing", teamId, 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODEL_DEPLOYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("更新 Application 应可切换默认 Deployment")
    void updateApplicationShouldSwitchDefaultDeployment() throws Exception {

        long teamId = createTeam("app-dep-switch-team");
        long deploymentA = createReadyDeployment("app-dep-switch-a");
        long deploymentB = createReadyDeployment("app-dep-switch-b");

        long appId = createApplicationId(createApplicationWithDeployment(
                "app-dep-switch", teamId, deploymentA));

        mockMvc.perform(authed(put("/api/applications/" + appId))
                        .contentType(APPLICATION_JSON)
                        .content(json(updateApplicationRequest(
                                "app-dep-switch", teamId, deploymentB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId))
                .andExpect(jsonPath("$.defaultDeploymentId").value(deploymentB));
    }

    @Test
    @DisplayName("PUT defaultDeploymentId 为 null 应清空绑定")
    void updateApplicationWithNullDeploymentShouldClear() throws Exception {

        long teamId = createTeam("app-dep-clear-team");
        long deploymentId = createReadyDeployment("app-dep-clear");

        long appId = createApplicationId(createApplicationWithDeployment(
                "app-dep-clear", teamId, deploymentId));

        mockMvc.perform(authed(put("/api/applications/" + appId))
                        .contentType(APPLICATION_JSON)
                        .content(json(updateApplicationRequest(
                                "app-dep-clear", teamId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultDeploymentId").value(nullValue()));
    }

    @Test
    @DisplayName("删除被 Application 引用的 Deployment 应返回 409 RESOURCE_CONFLICT")
    void deleteDeploymentReferencedByApplicationShouldReturn409() throws Exception {

        long teamId = createTeam("app-dep-ref-team");
        long deploymentId = createReadyDeployment("app-dep-ref");
        createApplicationId(createApplicationWithDeployment(
                "app-dep-ref", teamId, deploymentId));

        mockMvc.perform(authed(delete("/api/model-deployments/" + deploymentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    // ---- helpers ----

    private long createTeam(String name) throws Exception {

        CreateTeamRequest request = new CreateTeamRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private CreateApplicationRequest createApplication(String name, Long teamId) {
        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        return request;
    }

    private CreateApplicationRequest createApplicationWithDeployment(
            String name, Long teamId, Long defaultDeploymentId) {
        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(defaultDeploymentId);
        return request;
    }

    private UpdateApplicationRequest updateApplicationRequest(
            String name, Long teamId, Long defaultDeploymentId) {
        UpdateApplicationRequest request = new UpdateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(defaultDeploymentId);
        return request;
    }

    private long createApplicationId(CreateApplicationRequest request) throws Exception {

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    /** 便捷：建一整套 Provider -> Model -> ModelDeployment，返回 deploymentId。 */
    private long createReadyDeployment(String tag) throws Exception {

        long providerId = createProvider(tag + "-provider");
        long modelId = createModel(tag + "-model");
        return createDeployment(tag + "-deployment", providerId, modelId);
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

    private long createDeployment(String name, long providerId, long modelId) throws Exception {

        CreateModelDeploymentRequest request = new CreateModelDeploymentRequest();
        request.setName(name);
        request.setProviderId(providerId);
        request.setModelId(modelId);
        request.setEndpointUrl("http://localhost:9999/v1");
        request.setRemoteModelName("test-model");
        request.setEnabled(true);

        String body = mockMvc.perform(authed(post("/api/model-deployments"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }
}

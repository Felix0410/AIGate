package com.felix.aigate.application;

import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application 核心链路集成测试：关联已存在 Team、Team 不存在 404、name 重复 409。
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
}

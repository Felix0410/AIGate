package com.felix.aigate.team;

import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.felix.aigate.team.dto.request.UpdateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Team 核心链路集成测试：创建 / 校验 / 404 / 409 / 同名更新。
 */
class TeamIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("创建 Team -> 200，返回 id 与 name")
    void createTeamShouldSucceed() throws Exception {

        mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createTeam("team-it-create"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("team-it-create"));
    }

    @Test
    @DisplayName("创建 Team name 为空 -> 400 VALIDATION_ERROR，errors.name 存在")
    void createTeamWithBlankNameShouldReturn400() throws Exception {

        mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createTeam(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("GET 不存在的 Team -> 404 TEAM_NOT_FOUND")
    void getMissingTeamShouldReturn404() throws Exception {

        mockMvc.perform(authed(get("/api/teams/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("重复创建同名 Team -> 409 TEAM_NAME_ALREADY_EXISTS")
    void duplicateTeamNameShouldReturn409() throws Exception {

        CreateTeamRequest request = createTeam("team-it-conflict");

        mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_NAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("更新 Team 保持自身原名 -> 200（excludeId 不误判自己）")
    void updateTeamWithSameNameShouldSucceed() throws Exception {

        long id = createAndReadId("team-it-update-same");

        UpdateTeamRequest update = new UpdateTeamRequest();
        update.setName("team-it-update-same");

        mockMvc.perform(authed(put("/api/teams/" + id))
                        .contentType(APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("team-it-update-same"));
    }

    // ---- helpers ----

    private long createAndReadId(String name) throws Exception {

        String body = mockMvc.perform(authed(post("/api/teams"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createTeam(name))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private CreateTeamRequest createTeam(String name) {
        CreateTeamRequest request = new CreateTeamRequest();
        request.setName(name);
        return request;
    }
}

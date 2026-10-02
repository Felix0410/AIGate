package com.felix.aigate.employee;

import com.felix.aigate.employee.dto.request.CreateEmployeeRequest;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee 核心链路集成测试：关联已存在 Team、Team 不存在 404、email 重复 409。
 */
class EmployeeIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("在已存在 Team 下创建 Employee -> 200，teamId 正确")
    void createEmployeeWithExistingTeamShouldSucceed() throws Exception {

        long teamId = createTeam("employee-it-team");

        mockMvc.perform(authed(post("/api/employees"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createEmployee("Employee IT", "employee-it-001@example.com", teamId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("employee-it-001@example.com"))
                .andExpect(jsonPath("$.teamId").value(teamId));
    }

    @Test
    @DisplayName("指向不存在 Team 创建 Employee -> 404 TEAM_NOT_FOUND")
    void createEmployeeWithMissingTeamShouldReturn404() throws Exception {

        mockMvc.perform(authed(post("/api/employees"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createEmployee("Ghost", "employee-it-ghost@example.com", 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("重复 email 创建 Employee -> 409 EMAIL_ALREADY_EXISTS")
    void duplicateEmployeeEmailShouldReturn409() throws Exception {

        long teamId = createTeam("employee-it-dup-team");
        CreateEmployeeRequest request = createEmployee("Dup Emp", "employee-it-dup@example.com", teamId);

        mockMvc.perform(authed(post("/api/employees"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/employees"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
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

    private CreateEmployeeRequest createEmployee(String name, String email, Long teamId) {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setName(name);
        request.setEmail(email);
        request.setTeamId(teamId);
        return request;
    }
}

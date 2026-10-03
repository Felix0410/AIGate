package com.felix.aigate.model;

import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Model 集成测试：创建成功 / name 重复 409。
 */
class ModelIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("创建 Model -> 200，返回 id/name")
    void createModelShouldSucceed() throws Exception {

        mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createModel("model-it-create"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("model-it-create"));
    }

    @Test
    @DisplayName("重复 name 创建 Model -> 409 MODEL_NAME_ALREADY_EXISTS")
    void duplicateModelNameShouldReturn409() throws Exception {

        CreateModelRequest request = createModel("model-it-conflict");

        mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/models"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_NAME_ALREADY_EXISTS"));
    }

    // ---- helpers ----

    private CreateModelRequest createModel(String name) {
        CreateModelRequest request = new CreateModelRequest();
        request.setName(name);
        return request;
    }
}

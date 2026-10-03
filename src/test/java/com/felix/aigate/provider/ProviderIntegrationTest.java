package com.felix.aigate.provider;

import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider 集成测试：创建 / name 重复 409 / ProviderType 与 MySQL VARCHAR 的写入读取往返。
 */
class ProviderIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("创建 Provider -> 200，返回 id/name/type")
    void createProviderShouldSucceed() throws Exception {

        mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createProvider("provider-it-create"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("provider-it-create"))
                .andExpect(jsonPath("$.type").value("OPENAI_COMPATIBLE"));
    }

    @Test
    @DisplayName("重复 name 创建 Provider -> 409 PROVIDER_NAME_ALREADY_EXISTS")
    void duplicateProviderNameShouldReturn409() throws Exception {

        CreateProviderRequest request = createProvider("provider-it-conflict");

        mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDER_NAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("ProviderType OPENAI_COMPATIBLE 能写入 MySQL VARCHAR 并原样读回")
    void providerTypeShouldPersistAndReadBack() throws Exception {

        long id = createAndReadId(createProvider("provider-it-type-roundtrip"));

        // GET 直接走 selectById -> MyBatis 枚举 -> VARCHAR 反序列化链路
        mockMvc.perform(authed(get("/api/providers/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("OPENAI_COMPATIBLE"));
    }

    // ---- helpers ----

    private CreateProviderRequest createProvider(String name) {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setName(name);
        request.setType(ProviderType.OPENAI_COMPATIBLE);
        return request;
    }

    private long createAndReadId(CreateProviderRequest request) throws Exception {

        String body = mockMvc.perform(authed(post("/api/providers"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }
}

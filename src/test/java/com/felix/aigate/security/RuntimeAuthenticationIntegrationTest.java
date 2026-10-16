package com.felix.aigate.security;

import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.security.support.RuntimeSecurityTestController;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-T05 Runtime 认证集成测试：/v1/** 只认 Application API Key（Bearer），
 * 与 /api/** 的 HTTP Basic 双向隔离；覆盖成功、缺头、非 Bearer、格式错误、
 * keyId 不存在、secret 错误、REVOKED、Basic 越界用 /v1、API Key 越界用 /api、Swagger 公共路径。
 */
class RuntimeAuthenticationIntegrationTest extends IntegrationTestBase {

    private static final String IDENTITY_URL = "/v1/test/identity";

    /**
     * 仅测试上下文注册的 Runtime 端点：@SpringBootTest 会自动发现嵌套的 @TestConfiguration。
     */
    @TestConfiguration
    @Import(RuntimeSecurityTestController.class)
    static class RuntimeEndpointConfiguration {
    }

    @Test
    @DisplayName("ACTIVE API Key 访问 /v1/** -> 200，principal 为正确 Application")
    void activeApiKeyShouldAuthenticateRuntimeRequest() throws Exception {

        long teamId = createTeam("runtime-ok-team");
        String appName = "runtime-ok-app";
        long appId = createApplication(appName, teamId);

        JsonNode key = createApiKey(appId, "runtime-ok-key");
        String apiKey = key.get("apiKey").asText();

        mockMvc.perform(authed(get(IDENTITY_URL)).header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appId))
                .andExpect(jsonPath("$.applicationName").value(appName))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.keyHash").doesNotExist());
    }

    @Test
    @DisplayName("缺少 Authorization 访问 /v1/** -> 401 UNAUTHORIZED")
    void missingAuthorizationShouldReturn401() throws Exception {

        mockMvc.perform(get(IDENTITY_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("非 Bearer Header 访问 /v1/** -> 401 UNAUTHORIZED")
    void nonBearerHeaderShouldReturn401() throws Exception {

        mockMvc.perform(get(IDENTITY_URL).header(HttpHeaders.AUTHORIZATION, "Something xxx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("malformed API Key -> 401 UNAUTHORIZED")
    void malformedApiKeyShouldReturn401() throws Exception {

        String[] badKeys = {
                "aig_live_invalid",
                "abc",
                "aig_live_.xxx",
                "aig_live_xxx."
        };

        for (String badKey : badKeys) {
            mockMvc.perform(get(IDENTITY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + badKey))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @DisplayName("格式合法但 keyId 不存在 -> 401 UNAUTHORIZED（不泄露 API_KEY_NOT_FOUND）")
    void unknownKeyIdShouldReturn401() throws Exception {

        mockMvc.perform(get(IDENTITY_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer aig_live_nonexistentkeyid.somesecret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("真实 keyId + 错误 secret -> 401 UNAUTHORIZED")
    void wrongSecretShouldReturn401() throws Exception {

        long teamId = createTeam("runtime-wrong-secret-team");
        long appId = createApplication("runtime-wrong-secret-app", teamId);

        String keyId = createApiKey(appId, "runtime-wrong-secret-key").get("keyId").asText();
        String forged = "aig_live_" + keyId + ".definitely-the-wrong-secret";

        mockMvc.perform(get(IDENTITY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("REVOKED Key 访问 /v1/** -> 401 UNAUTHORIZED")
    void revokedKeyShouldReturn401() throws Exception {

        long teamId = createTeam("runtime-revoked-team");
        long appId = createApplication("runtime-revoked-app", teamId);

        JsonNode key = createApiKey(appId, "runtime-revoked-key");
        String apiKey = key.get("apiKey").asText();
        long keyId = key.get("id").asLong();

        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys/" + keyId + "/revoke")))
                .andExpect(status().isOk());

        mockMvc.perform(get(IDENTITY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("HTTP Basic 不能访问 /v1/** -> 401 UNAUTHORIZED")
    void basicAuthShouldNotPassRuntimeChain() throws Exception {

        mockMvc.perform(get(IDENTITY_URL).with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("API Key 不能代替 Management Basic 访问 /api/** -> 401 UNAUTHORIZED")
    void apiKeyShouldNotPassManagementChain() throws Exception {

        long teamId = createTeam("runtime-cross-team");
        long appId = createApplication("runtime-cross-app", teamId);
        String apiKey = createApiKey(appId, "runtime-cross-key").get("apiKey").asText();

        mockMvc.perform(get("/api/teams").header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Basic 仍可正常访问 /api/** -> 200")
    void basicAuthShouldStillPassManagementChain() throws Exception {

        mockMvc.perform(authed(get("/api/teams")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger 公共路径无需认证 -> 200")
    void swaggerApiDocsShouldRemainPublic() throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
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

    private long createApplication(String name, long teamId) throws Exception {

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private JsonNode createApiKey(long applicationId, String name) throws Exception {

        CreateApplicationApiKeyRequest request = new CreateApplicationApiKeyRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/applications/" + applicationId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body);
    }
}

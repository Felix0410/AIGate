package com.felix.aigate.application;

import tools.jackson.databind.JsonNode;
import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.entity.ApplicationApiKey;
import com.felix.aigate.application.mapper.ApplicationApiKeyMapper;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application API Key 生命周期集成测试：
 * 创建 / 多 Key / DB 不存明文 / List 不泄露 / Application 缺失 404 / name 空 400 /
 * Revoke 状态流转 / 幂等 / 跨 Application 隔离 / 不存在 404 / ON DELETE CASCADE。
 */
class ApplicationApiKeyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ApplicationApiKeyMapper applicationApiKeyMapper;

    @Test
    @DisplayName("创建 API Key -> 200，ACTIVE、revokedAt=null、apiKey 以 aig_live_ 开头")
    void createApiKeyShouldSucceed() throws Exception {

        long appId = createApplication("apikey-create");

        JsonNode response = createApiKey(appId, "key-1");

        assertEquals("ACTIVE", response.get("status").asText());
        assertTrue(response.get("revokedAt").isNull());
        assertTrue(response.get("apiKey").asText().startsWith("aig_live_"));
        assertEquals(appId, response.get("applicationId").asLong());
    }

    @Test
    @DisplayName("同一 Application 可创建多把 Key，keyId/apiKey 均不同")
    void createMultipleKeysShouldDiffer() throws Exception {

        long appId = createApplication("apikey-multi");

        JsonNode first = createApiKey(appId, "same-name");
        JsonNode second = createApiKey(appId, "same-name");

        assertNotEquals(first.get("keyId").asText(), second.get("keyId").asText());
        assertNotEquals(first.get("apiKey").asText(), second.get("apiKey").asText());
    }

    @Test
    @DisplayName("DB 只存 hash：keyHash != secret、!= 完整 apiKey、== SHA-256(secret)")
    void databaseShouldStoreHashNotPlaintext() throws Exception {

        long appId = createApplication("apikey-hash");

        JsonNode response = createApiKey(appId, "key-hash");
        String apiKey = response.get("apiKey").asText();
        String secret = apiKey.substring(apiKey.indexOf('.') + 1);
        long keyId = response.get("id").asLong();

        ApplicationApiKey stored = applicationApiKeyMapper.selectById(keyId);
        String keyHash = stored.getKeyHash();

        assertNotEquals(secret, keyHash);
        assertNotEquals(apiKey, keyHash);
        assertEquals(sha256Hex(secret), keyHash);
    }

    @Test
    @DisplayName("List 返回元数据但不泄露 apiKey/secret/keyHash")
    void listShouldNotLeakSensitiveFields() throws Exception {

        long appId = createApplication("apikey-list");
        createApiKey(appId, "key-list");

        String body = mockMvc.perform(authed(get("/api/applications/" + appId + "/api-keys")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].keyPrefix").exists())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(body.contains("keyPrefix"));
    }

    @Test
    @DisplayName("Application 存在但无 Key -> 200 []")
    void listEmptyKeysShouldReturnEmptyArray() throws Exception {

        long appId = createApplication("apikey-list-empty");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/api-keys")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Application 不存在时 Create/List -> 404 APPLICATION_NOT_FOUND")
    void createOrListOnMissingApplicationShouldReturn404() throws Exception {

        long missingAppId = 999999999L;

        mockMvc.perform(authed(post("/api/applications/" + missingAppId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createKeyRequest("ghost-key"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

        mockMvc.perform(authed(get("/api/applications/" + missingAppId + "/api-keys")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("name 为空 -> 400 VALIDATION_ERROR")
    void blankNameShouldReturn400() throws Exception {

        long appId = createApplication("apikey-blank");

        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createKeyRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("Revoke ACTIVE Key -> REVOKED，revokedAt 不为空")
    void revokeActiveKeyShouldSucceed() throws Exception {

        long appId = createApplication("apikey-revoke");
        long keyId = createApiKey(appId, "key-revoke").get("id").asLong();

        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys/" + keyId + "/revoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").exists());
    }

    @Test
    @DisplayName("Revoke 后 row 仍存在（revoke 不是 delete）")
    void revokedKeyRowShouldStillExist() throws Exception {

        long appId = createApplication("apikey-revoke-exist");
        long keyId = createApiKey(appId, "key-exist").get("id").asLong();

        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys/" + keyId + "/revoke")))
                .andExpect(status().isOk());

        ApplicationApiKey stored = applicationApiKeyMapper.selectById(keyId);
        assertNotNull(stored);
        assertEquals("REVOKED", String.valueOf(stored.getStatus()));
    }

    @Test
    @DisplayName("重复 Revoke 幂等 -> 第二次仍 200，revokedAt 与第一次完全相同")
    void repeatedRevokeShouldBeIdempotent() throws Exception {

        long appId = createApplication("apikey-revoke-idempotent");
        long keyId = createApiKey(appId, "key-idem").get("id").asLong();

        String firstBody = mockMvc.perform(authed(
                        post("/api/applications/" + appId + "/api-keys/" + keyId + "/revoke")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstRevokedAt = jsonMapper.readTree(firstBody).get("revokedAt").asText();

        String secondBody = mockMvc.perform(authed(
                        post("/api/applications/" + appId + "/api-keys/" + keyId + "/revoke")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondRevokedAt = jsonMapper.readTree(secondBody).get("revokedAt").asText();

        assertEquals(firstRevokedAt, secondRevokedAt);
    }

    @Test
    @DisplayName("Revoke 不存在 Key -> 404 API_KEY_NOT_FOUND")
    void revokeMissingKeyShouldReturn404() throws Exception {

        long appId = createApplication("apikey-revoke-missing");

        mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys/999999999/revoke")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("跨 Application Revoke -> 404 API_KEY_NOT_FOUND，原 Key 仍 ACTIVE")
    void crossApplicationRevokeShouldReturn404() throws Exception {

        long appA = createApplication("apikey-cross-a");
        long appB = createApplication("apikey-cross-b");

        long keyUnderA = createApiKey(appA, "key-cross").get("id").asLong();

        mockMvc.perform(authed(post("/api/applications/" + appB + "/api-keys/" + keyUnderA + "/revoke")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));

        ApplicationApiKey stored = applicationApiKeyMapper.selectById(keyUnderA);
        assertEquals("ACTIVE", String.valueOf(stored.getStatus()));
    }

    @Test
    @DisplayName("删除 Application -> application_api_key row 级联删除")
    void deleteApplicationShouldCascadeKeys() throws Exception {

        long appId = createApplication("apikey-cascade");
        long keyId = createApiKey(appId, "key-cascade").get("id").asLong();

        mockMvc.perform(authed(delete("/api/applications/" + appId)))
                .andExpect(status().isOk());

        assertNull(applicationApiKeyMapper.selectById(keyId));
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

    private long createApplication(String name) throws Exception {

        long teamId = createTeam(name + "-team");

        var request = new com.felix.aigate.application.dto.request.CreateApplicationRequest();
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

    private CreateApplicationApiKeyRequest createKeyRequest(String name) {
        CreateApplicationApiKeyRequest request = new CreateApplicationApiKeyRequest();
        request.setName(name);
        return request;
    }

    private JsonNode createApiKey(long applicationId, String name) throws Exception {

        String body = mockMvc.perform(authed(post("/api/applications/" + applicationId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createKeyRequest(name))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

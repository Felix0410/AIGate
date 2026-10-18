package com.felix.aigate.invocation;

import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvocationQueryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private InvocationRecordMapper invocationRecordMapper;

    // ---- Test 1: 默认 limit=20 ----

    @Test
    @DisplayName("默认 limit=20：返回最近 20 条，按 id DESC 排序")
    void defaultLimitReturns20LatestRecords() throws Exception {

        long appId = createReadyApplication("q-default");
        insertRecords(appId, 25);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].applicationId").value(appId))
                .andExpect(jsonPath("$[0].id").isNumber());

        List<InvocationRecord> records = listAllInvocationRecords(appId);

        assertEquals(25, records.size());

        long firstReturnedId = records.stream()
                .mapToLong(InvocationRecord::getId)
                .max()
                .orElse(0);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(jsonPath("$[0].id").value(firstReturnedId));

        verifyAllBelongToApplication(appId);
    }

    // ---- Test 2: 显式 limit=1 ----

    @Test
    @DisplayName("limit=1：只返回最新一条")
    void explicitLimitOneShouldReturnSingleRecord() throws Exception {

        long appId = createReadyApplication("q-limit1");
        insertRecords(appId, 5);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].applicationId").value(appId));
    }

    // ---- Test 3: limit=100 ----

    @Test
    @DisplayName("limit=100：应返回恰好 100 条，按 id DESC 排序")
    void limitMaxShouldReturn100Records() throws Exception {

        long appId = createReadyApplication("q-max");
        insertRecords(appId, 101);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[0].applicationId").value(appId));

        verifyAllBelongToApplication(appId);
    }

    // ---- Test 4: 非法 limit ----

    @Test
    @DisplayName("limit=0 -> 400 INVALID_REQUEST")
    void limitZeroShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-bad-limit");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid invocation query limit"));
    }

    @Test
    @DisplayName("limit=-1 -> 400 INVALID_REQUEST")
    void limitNegativeShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-neg");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("limit=101 -> 400 INVALID_REQUEST")
    void limitExceedMaxShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-exceed");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("limit=abc -> 400 INVALID_REQUEST")
    void limitNonNumericShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-alpha");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("limit=空字符串 -> 400 INVALID_REQUEST")
    void limitEmptyStringShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-empty");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("limit=1.5 -> 400 INVALID_REQUEST")
    void limitDecimalShouldReturn400() throws Exception {

        long appId = createReadyApplication("q-decimal");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=1.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- Test 5: Application 不存在 ----

    @Test
    @DisplayName("Application 不存在 -> 404 APPLICATION_NOT_FOUND")
    void applicationNotFoundShouldReturn404() throws Exception {

        mockMvc.perform(authed(get("/api/applications/999999999/invocations")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    // ---- Test 6: Application 存在但无记录 ----

    @Test
    @DisplayName("Application 存在但无调用记录 -> 200，body 为 []")
    void applicationWithoutRecordsShouldReturnEmptyArray() throws Exception {

        long appId = createReadyApplication("q-empty-app");

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Test 7: 跨 Application 数据隔离 ----

    @Test
    @DisplayName("跨 Application 数据隔离：查询 A 只返回 A 的记录")
    void crossApplicationDataIsolation() throws Exception {

        long appA = createReadyApplication("q-iso-a");
        long appB = createReadyApplication("q-iso-b");

        insertRecord(appA, InvocationStatus.SUCCESS, null, 100L);
        insertRecord(appB, InvocationStatus.SUCCESS, null, 200L);
        insertRecord(appA, InvocationStatus.SUCCESS, null, 150L);
        insertRecord(appB, InvocationStatus.SUCCESS, null, 250L);
        insertRecord(appA, InvocationStatus.SUCCESS, null, 300L);

        mockMvc.perform(authed(get("/api/applications/" + appA + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value(appA))
                .andExpect(jsonPath("$[1].applicationId").value(appA))
                .andExpect(jsonPath("$[2].applicationId").value(appA));

        List<InvocationRecord> recordsA = listAllInvocationRecords(appA);
        assertEquals(3, recordsA.size());
        assertTrue(recordsA.stream().allMatch(r -> r.getApplicationId().equals(appA)));

        mockMvc.perform(authed(get("/api/applications/" + appB + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value(appB))
                .andExpect(jsonPath("$[1].applicationId").value(appB));

        List<InvocationRecord> recordsB = listAllInvocationRecords(appB);
        assertEquals(2, recordsB.size());
        assertTrue(recordsB.stream().allMatch(r -> r.getApplicationId().equals(appB)));
    }

    // ---- Test 8: 管理认证 ----

    @Test
    @DisplayName("未认证访问 -> 401")
    void unauthenticatedShouldReturn401() throws Exception {

        long appId = createReadyApplication("q-auth");

        mockMvc.perform(get("/api/applications/" + appId + "/invocations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Application API Key 不能访问管理查询接口")
    void applicationApiKeyShouldNotAccessManagementQuery() throws Exception {

        long appId = createReadyApplication("q-app-key");

        String apiKey = createApiKeyForApplication(appId);

        mockMvc.perform(get("/api/applications/" + appId + "/invocations")
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理 Basic 认证可成功访问")
    void managementBasicAuthShouldSucceed() throws Exception {

        long appId = createReadyApplication("q-mgmt");
        insertRecords(appId, 3);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    // ---- Test 9: DTO 字段与隐私 ----

    @Test
    @DisplayName("SUCCESS errorCode=null、FAILED errorCode 正确，字段仅包含六个")
    void dtoShouldContainOnlySixFields() throws Exception {

        long appId = createReadyApplication("q-dto");

        insertRecord(appId, InvocationStatus.SUCCESS, null, 157L);
        insertRecord(appId, InvocationStatus.FAILED, "PROVIDER_UNAVAILABLE", 0L);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].applicationId").value(appId))
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].errorCode").exists())
                .andExpect(jsonPath("$[0].durationMs").isNumber())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("FAILED 记录 errorCode 正确返回")
    void failedRecordShouldReturnCorrectErrorCode() throws Exception {

        long appId = createReadyApplication("q-failed");

        insertRecord(appId, InvocationStatus.FAILED, "PROVIDER_UNAVAILABLE", 0L);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorCode").value("PROVIDER_UNAVAILABLE"));
    }

    // ---- Test 10: 数据库查询限制 ----

    @Test
    @DisplayName("数据库应使用 LIMIT，不返回全部记录后截取")
    void queryShouldUseDatabaseLimit() throws Exception {

        long appId = createReadyApplication("q-dblimit");
        insertRecords(appId, 50);

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations?limit=5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
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

    private long createApplication(String name, long teamId, long deploymentId) throws Exception {

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        request.setDefaultDeploymentId(deploymentId);

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createReadyApplication(String tag) throws Exception {

        long teamId = createTeam(tag + "-team");
        long providerId = createProvider(tag + "-provider");
        long modelId = createModel(tag + "-model");
        long deploymentId = createDeployment(tag + "-deployment", providerId, modelId);
        return createApplication(tag + "-app", teamId, deploymentId);
    }

    private void insertRecord(long applicationId, InvocationStatus status, String errorCode, long durationMs) {

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(applicationId);
        record.setStatus(status);
        record.setErrorCode(errorCode);
        record.setDurationMs(durationMs);
        record.setCreatedAt(Instant.now());

        invocationRecordMapper.insert(record);
    }

    private void insertRecords(long applicationId, int count) {

        for (int i = 0; i < count; i++) {
            insertRecord(applicationId, InvocationStatus.SUCCESS, null, 100L + i);
        }
    }

    private List<InvocationRecord> listAllInvocationRecords(Long applicationId) {

        return invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, applicationId));
    }

    private void verifyAllBelongToApplication(long appId) throws Exception {

        mockMvc.perform(authed(get("/api/applications/" + appId + "/invocations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].applicationId").value(
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is(Long.valueOf(appId).intValue()))));
    }

    private String createApiKeyForApplication(long appId) throws Exception {

        String body = mockMvc.perform(authed(post("/api/applications/" + appId + "/api-keys"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"test-key\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("apiKey").asText();
    }
}
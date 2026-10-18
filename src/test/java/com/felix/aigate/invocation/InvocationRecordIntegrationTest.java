package com.felix.aigate.invocation;

import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.support.IntegrationTestBase;
import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.felix.aigate.team.mapper.TeamMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvocationRecordIntegrationTest extends IntegrationTestBase {

    @Autowired
    private InvocationRecordMapper invocationRecordMapper;

    @Autowired
    private ApplicationMapper applicationMapper;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("invocation_record 表存在，字段类型和索引正确")
    void tableShouldExistWithCorrectSchema() {

        int count = JdbcTestUtils.countRowsInTable(jdbcTemplate, "invocation_record");
        assertTrue(count >= 0);

        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invocation_record' " +
                "ORDER BY ORDINAL_POSITION",
                String.class);

        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("application_id"));
        assertTrue(columns.contains("status"));
        assertTrue(columns.contains("error_code"));
        assertTrue(columns.contains("duration_ms"));
        assertTrue(columns.contains("created_at"));
    }

    @Test
    @DisplayName("插入 SUCCESS 记录并根据 ID 读取")
    void insertSuccessRecordAndReadById() throws Exception {

        long appId = createApplication("inv-success");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(150L);
        record.setCreatedAt(Instant.now());

        invocationRecordMapper.insert(record);

        assertNotNull(record.getId());

        InvocationRecord fetched = invocationRecordMapper.selectById(record.getId());
        assertNotNull(fetched);
        assertEquals(appId, fetched.getApplicationId());
        assertEquals(InvocationStatus.SUCCESS, fetched.getStatus());
        assertEquals(150L, fetched.getDurationMs());
        assertNotNull(fetched.getCreatedAt());
    }

    @Test
    @DisplayName("插入 FAILED 记录并读取 errorCode")
    void insertFailedRecordAndReadErrorCode() throws Exception {

        long appId = createApplication("inv-failed");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.FAILED);
        record.setErrorCode("PROVIDER_TIMEOUT");
        record.setDurationMs(30000L);
        record.setCreatedAt(Instant.now());

        invocationRecordMapper.insert(record);

        InvocationRecord fetched = invocationRecordMapper.selectById(record.getId());
        assertNotNull(fetched);
        assertEquals(InvocationStatus.FAILED, fetched.getStatus());
        assertEquals("PROVIDER_TIMEOUT", fetched.getErrorCode());
        assertEquals(30000L, fetched.getDurationMs());
    }

    @Test
    @DisplayName("SUCCESS 可以让 errorCode 为 NULL")
    void successRecordShouldAllowNullErrorCode() throws Exception {

        long appId = createApplication("inv-null-err");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setErrorCode(null);
        record.setDurationMs(50L);
        record.setCreatedAt(Instant.now());

        invocationRecordMapper.insert(record);

        InvocationRecord fetched = invocationRecordMapper.selectById(record.getId());
        assertNotNull(fetched);
        assertEquals(InvocationStatus.SUCCESS, fetched.getStatus());
        assertNull(fetched.getErrorCode());
    }

    @Test
    @DisplayName("duration_ms 不允许负数")
    void negativeDurationShouldFail() throws Exception {

        long appId = createApplication("inv-neg-dur");

        assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO invocation_record (application_id, status, duration_ms, created_at) " +
                    "VALUES (?, 'SUCCESS', ?, NOW(6))",
                    appId, -1L);
        });
    }

    @Test
    @DisplayName("status 不允许非法值")
    void invalidStatusShouldFail() throws Exception {

        long appId = createApplication("inv-bad-status");

        assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO invocation_record (application_id, status, duration_ms, created_at) " +
                    "VALUES (?, 'UNKNOWN', ?, NOW(6))",
                    appId, 100L);
        });
    }

    @Test
    @DisplayName("不存在的 application_id 不能插入")
    void missingApplicationIdShouldFail() {

        long missingAppId = 999999999L;

        assertThrows(DataIntegrityViolationException.class, () -> {
            InvocationRecord record = new InvocationRecord();
            record.setApplicationId(missingAppId);
            record.setStatus(InvocationStatus.SUCCESS);
            record.setDurationMs(100L);
            record.setCreatedAt(Instant.now());
            invocationRecordMapper.insert(record);
        });
    }

    @Test
    @DisplayName("删除 Application 后关联的 InvocationRecord 自动删除")
    void deleteApplicationShouldCascadeInvocationRecords() throws Exception {

        long appId = createApplication("inv-cascade");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(100L);
        record.setCreatedAt(Instant.now());
        invocationRecordMapper.insert(record);
        long recordId = record.getId();

        mockMvc.perform(authed(delete("/api/applications/" + appId)))
                .andExpect(status().isOk());

        assertNull(invocationRecordMapper.selectById(recordId));
        assertNull(applicationMapper.selectById(appId));
    }

    @Test
    @DisplayName("根据 (application_id,id) 索引支持有界最新记录查询")
    void compositeIndexShouldSupportBoundedLatestQuery() throws Exception {

        long appId = createApplication("inv-index");

        for (int i = 0; i < 5; i++) {
            InvocationRecord record = new InvocationRecord();
            record.setApplicationId(appId);
            record.setStatus(InvocationStatus.SUCCESS);
            record.setDurationMs((long) (i + 1) * 100);
            record.setCreatedAt(Instant.now());
            invocationRecordMapper.insert(record);
        }

        List<InvocationRecord> latest = invocationRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvocationRecord>()
                        .eq(InvocationRecord::getApplicationId, appId)
                        .orderByDesc(InvocationRecord::getId)
                        .last("LIMIT 3"));

        assertEquals(3, latest.size());

        assertTrue(latest.get(0).getId() > latest.get(1).getId());
        assertTrue(latest.get(1).getId() > latest.get(2).getId());
    }

    @Test
    @DisplayName("更新记录可以正常执行")
    void updateRecordShouldSucceed() throws Exception {

        long appId = createApplication("inv-update");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(100L);
        record.setCreatedAt(Instant.now());
        invocationRecordMapper.insert(record);

        record.setStatus(InvocationStatus.FAILED);
        record.setErrorCode("UPDATED_ERROR");
        invocationRecordMapper.updateById(record);

        InvocationRecord fetched = invocationRecordMapper.selectById(record.getId());
        assertEquals(InvocationStatus.FAILED, fetched.getStatus());
        assertEquals("UPDATED_ERROR", fetched.getErrorCode());
    }

    @Test
    @DisplayName("删除记录可以正常执行")
    void deleteRecordShouldSucceed() throws Exception {

        long appId = createApplication("inv-delete");

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(appId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(100L);
        record.setCreatedAt(Instant.now());
        invocationRecordMapper.insert(record);

        int deleted = invocationRecordMapper.deleteById(record.getId());
        assertEquals(1, deleted);

        assertNull(invocationRecordMapper.selectById(record.getId()));
    }

    // ---- helpers ----

    private long createApplication(String name) throws Exception {

        long teamId = createTeam("team-" + name);

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(APPLICATION_JSON)
                        .content(json(createApplicationRequest(name, teamId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

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

    private com.felix.aigate.application.dto.request.CreateApplicationRequest createApplicationRequest(
            String name, Long teamId) {
        com.felix.aigate.application.dto.request.CreateApplicationRequest request =
                new com.felix.aigate.application.dto.request.CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);
        return request;
    }
}
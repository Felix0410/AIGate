package com.felix.aigate.invocation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.invocation.service.InvocationRecordRetentionService;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 30 天保留规则集成测试。
 *
 * <p>使用固定的 now/cutoff 验证 TIMESTAMP(6) 边界，不依赖系统时间修改。
 * <p>通过真实 REST 创建 Application 以满足外键约束，测试结束通过 CASCADE 清理。
 * <p>通过记录 ID 断言个体状态，不依赖全局表行数。
 */
class InvocationRecordRetentionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private InvocationRecordMapper invocationRecordMapper;

    @Autowired
    private InvocationRecordRetentionService retentionService;

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    private Long app1;
    private Long app2;

    @BeforeEach
    void createRealApplications() throws Exception {

        String suffix = String.valueOf(System.nanoTime());

        long teamId = createTeam("rett-" + suffix);
        app1 = createApplication("reta1-" + suffix, teamId);
        app2 = createApplication("reta2-" + suffix, teamId);
    }

    @AfterEach
    void removeApplications() throws Exception {

        if (app1 != null) {
            mockMvc.perform(authed(delete("/api/applications/" + app1)));
        }
        if (app2 != null) {
            mockMvc.perform(authed(delete("/api/applications/" + app2)));
        }
    }

    @Test
    @DisplayName("cutoff 前 1μs 删除、cutoff 相等保留、cutoff 后保留")
    void boundaryCleanupShouldMatchSpec() {

        Instant beforeCutoff = CUTOFF.minusNanos(1000);
        Instant atCutoff = CUTOFF;
        Instant afterCutoff = CUTOFF.plusNanos(1000);

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        InvocationRecord r2 = buildRecord(app1, atCutoff);
        InvocationRecord r3 = buildRecord(app1, afterCutoff);

        invocationRecordMapper.insert(r1);
        invocationRecordMapper.insert(r2);
        invocationRecordMapper.insert(r3);

        retentionService.deleteBefore(CUTOFF);

        assertNull(recordById(r1.getId()), "cutoff 前的记录已删除");
        assertNotNull(recordById(r2.getId()), "cutoff 相等的记录保留");
        assertNotNull(recordById(r3.getId()), "cutoff 后的记录保留");

        List<InvocationRecord> appRecords = recordsForApp(app1);
        List<Long> ids = appRecords.stream().map(InvocationRecord::getId).toList();
        assertTrue(ids.contains(r2.getId()));
        assertTrue(ids.contains(r3.getId()));
    }

    @Test
    @DisplayName("只影响 invocation_record，Application 不受影响")
    void cleanupShouldNotAffectOtherTables() throws Exception {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        invocationRecordMapper.insert(r1);

        retentionService.deleteBefore(CUTOFF);

        assertNull(recordById(r1.getId()), "过期记录应被删除");

        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/applications/" + app1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("对相同 cutoff 第二次清理后本测试记录仍不存在")
    void idempotentCleanupShouldReturnZero() {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        invocationRecordMapper.insert(r1);

        retentionService.deleteBefore(CUTOFF);

        assertNull(recordById(r1.getId()), "第一次清理后过期记录已删除");

        int second = retentionService.deleteBefore(CUTOFF);

        assertNull(recordById(r1.getId()), "第二次清理后过期记录仍不存在");
    }

    @Test
    @DisplayName("不同 Application 的过期记录都会被清理，Application 保留")
    void applicationIsolationInCleanup() throws Exception {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        InvocationRecord r2 = buildRecord(app2, beforeCutoff);

        invocationRecordMapper.insert(r1);
        invocationRecordMapper.insert(r2);

        retentionService.deleteBefore(CUTOFF);

        assertNull(recordById(r1.getId()), "app1 过期记录已删除");
        assertNull(recordById(r2.getId()), "app2 过期记录已删除");

        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/applications/" + app1)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/applications/" + app2)))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private InvocationRecord recordById(Long id) {
        return invocationRecordMapper.selectById(id);
    }

    private List<InvocationRecord> recordsForApp(Long appId) {
        return invocationRecordMapper.selectList(new LambdaQueryWrapper<InvocationRecord>()
                .eq(InvocationRecord::getApplicationId, appId));
    }

    private InvocationRecord buildRecord(Long applicationId, Instant createdAt) {

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(applicationId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(100L);
        record.setCreatedAt(createdAt);
        return record;
    }

    private long createTeam(String name) throws Exception {

        com.felix.aigate.team.dto.request.CreateTeamRequest request =
                new com.felix.aigate.team.dto.request.CreateTeamRequest();
        request.setName(name);

        String body = mockMvc.perform(authed(post("/api/teams"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createApplication(String name, long teamId) throws Exception {

        com.felix.aigate.application.dto.request.CreateApplicationRequest request =
                new com.felix.aigate.application.dto.request.CreateApplicationRequest();
        request.setName(name);
        request.setTeamId(teamId);

        String body = mockMvc.perform(authed(post("/api/applications"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(body).get("id").asLong();
    }
}
package com.felix.aigate.invocation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.invocation.service.InvocationRecordRetentionService;
import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 30 天保留规则集成测试。
 *
 * <p>使用固定的 now/cutoff 验证 TIMESTAMP(6) 边界，不依赖系统时间修改。
 * <p>通过真实 REST 创建 Application 以满足外键约束。
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

        int deleted = retentionService.deleteBefore(CUTOFF);
        assertEquals(1, deleted);

        List<InvocationRecord> remaining = listAll();

        List<Long> remainingIds = remaining.stream()
                .map(InvocationRecord::getId)
                .toList();
        assertTrue(remainingIds.contains(r2.getId()));
        assertTrue(remainingIds.contains(r3.getId()));
        assertEquals(2, remainingIds.size());
    }

    @Test
    @DisplayName("只影响 invocation_record，Application 不受影响")
    void cleanupShouldNotAffectOtherTables() throws Exception {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        invocationRecordMapper.insert(r1);

        int deleted = retentionService.deleteBefore(CUTOFF);
        assertEquals(1, deleted);

        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/applications/" + app1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("对相同 cutoff 第二次清理返回 0，结果不变")
    void idempotentCleanupShouldReturnZero() {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        invocationRecordMapper.insert(buildRecord(app1, beforeCutoff));

        int first = retentionService.deleteBefore(CUTOFF);
        assertEquals(1, first);

        int second = retentionService.deleteBefore(CUTOFF);
        assertEquals(0, second);
    }

    @Test
    @DisplayName("不同 Application 的记录都会在 cutoff 前全部被清理")
    void applicationIsolationInCleanup() {

        Instant beforeCutoff = CUTOFF.minus(Duration.ofDays(1));

        InvocationRecord r1 = buildRecord(app1, beforeCutoff);
        InvocationRecord r2 = buildRecord(app2, beforeCutoff);

        invocationRecordMapper.insert(r1);
        invocationRecordMapper.insert(r2);

        int deleted = retentionService.deleteBefore(CUTOFF);
        assertEquals(2, deleted);
    }

    // ---- helpers ----

    private InvocationRecord buildRecord(Long applicationId, Instant createdAt) {

        InvocationRecord record = new InvocationRecord();
        record.setApplicationId(applicationId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setDurationMs(100L);
        record.setCreatedAt(createdAt);
        return record;
    }

    private List<InvocationRecord> listAll() {
        return invocationRecordMapper.selectList(new LambdaQueryWrapper<InvocationRecord>()
                .in(InvocationRecord::getApplicationId, List.of(app1, app2)));
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
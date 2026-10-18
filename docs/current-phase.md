# AIGate — Current Phase

> 总控确定业务与架构、执行验收及阶段收尾；开发导师实现和验证。以本文件当前状态为准，历史任务规范与验收记录见 progress、roadmap、architecture 和 GitHub 提交。

## 当前状态（2026-10-18）

- **Phase 1：CLOSED**
- **Phase 2：CLOSED / ACCEPTED**
- **Phase 3：ACTIVE — Invocation Records & Basic Visibility**
- P3-T01：业务与架构基线、ADR-003 — DONE / ACCEPTED
- P3-T02：Flyway V7、Entity、Mapper — DONE / ACCEPTED
- P3-T03：成功调用 SUCCESS best-effort 记录 — DONE / ACCEPTED
- P3-T04：管理端按 Application 查询历史 — DONE / ACCEPTED
- **P3-T05：失败记录 + 30 天清理 + E2E — DONE / ACCEPTED**
- **当前任务 P3-T06：Phase 3 Closeout — ACTIVE**
- **Phase 4：NOT STARTED，尚未冻结业务范围**

按照用户反馈，已将原 P3-T05 失败记录和 P3-T06 数据清理整合执行；现在 P3-T06 是阶段收尾，不再做原本 P3-T07。

## P3-T05 实际验收记录

实现提交：`8262303df74598f6e76e68ad1f10ce40b096e2c6`
测试修复提交：`dfcc70afb3228c817c9b42693257474b75895384`

**代码与测试设计审查通过，无阻塞性生产代码缺陷：**

- `InvocationTrackingFacade` 在合法 Runtime 业务调用完成后写入 SUCCESS；代理抛出 `ModelInvocationException` 时写 FAILED + 原 `ProxyErrorType` 安全错误码，`InvalidProviderResponseException` 写 PROVIDER_INVALID_RESPONSE，其它 RuntimeException 写 INTERNAL_SERVER_ERROR。
- 在写入 FAILED 之后 `throw original`；记录写入失败被局部隔离，只留下固定安全 warn；不覆盖原始模型成功结果或失败异常、不重试、不使用包围上游 HTTP 的大事务。
- 无效 API Key 401 和 @Valid 校验 400 在业务执行前拒绝，不产生记录。
- `InvocationRecordRetentionService` + Mapper `DELETE ... WHERE created_at < cutoff` 清理；`InvocationRecordRetentionScheduler` 每天 UTC 03:00 执行，截止时间为 `Instant.now().minus(Duration.ofDays(30))`，清理异常仅固定告警。
- MySQL Testcontainers 边界测试以独立记录 ID 验证“早于删除/等于保留/晚于保留”；新增 Scheduler 测试核对 cron、cutoff 和异常隔离。MockMvc + 本地 Provider E2E 覆盖上游 400/401/403/429/5xx、timeout、无效上游成功结构、默认部署配置失败、双状态管理查询。
- V1–V7 Flyway 保持不变；不新增 MQ/Redis/ES、微服务或计费。
- 用户未附本轮完整 Maven `Tests run/BUILD SUCCESS` 控制台汇总；GitHub 无独立 CI status/workflow，因此**本次验收是代码/测试覆盖验收，尚无可核验的本轮全量测试统计**。遵循既有“提交意味着用户已本地测试”的协作约定，但不能声称 CI 绿灯。

**P3-T05 DONE / ACCEPTED，后续测试证据与非阻塞测试稳定性问题列入 P3-T06 阶段收尾**；无须为每个小测试修改再拆分验收任务。

---

## P3-T06 — Phase 3 Closeout（ACTIVE）

### Business Goal

> 对整个“模型调用 + 成功/失败元数据记录 + 管理查询 + 30 天生命周期”业务闭环进行阶段级质量复核，确保代码、测试、架构、文档、技术债和面试表达真实一致；最终给出 Phase 3 是否可以正式关闭的结论。

### Scope / Main Flow

```text
Application API Key + valid POST /v1/invoke
→ InvocationTrackingFacade
→ ModelProxyService → Provider
   ├─ 成功 → SUCCESS / durationMs / createdAt
   └─ 失败 → FAILED / safe errorCode / durationMs / createdAt
→ MySQL invocation_record（best effort，不修改客户端结果）
→ GET /api/applications/{id}/invocations?limit=20（HTTP Basic）
→ 最近 1..100 条、按 App 隔离、id DESC
→ 每天 UTC 03:00 清除早于 30 天的历史
```

保持 Spring Boot 模块化单体、MySQL Flyway V1–V7，ADR-003 MySQL 同步 best-effort 记录已被实际实现；不是强一致计费/审计系统。

### 必须完成的检查和文档动作

1. **功能检查**：P3-T01～T05 与冻结 requirements 和 API/数据库设计逐条对应；确保 P3 业务闭环真实可用。
2. **Phase-level Code Review**：职责是否清晰（Controller/Facade/ModelProxyService/Adapter/Mapper/Scheduler）；外部 HTTP 与本地数据库之间没有假事务；脱敏、认证隔离和安全日志不退化。
3. **测试检查**：最好在 Java 21 + Docker 可用环境运行 `./mvnw test`，记录实际 `Tests run / Failures / Errors / Skipped / BUILD`。此前的 149/0/0/0 属于 P3-T02 代码基线，不可冒充 P3-T05 的新增测试结果；GitHub CI Gate TD-009 未解决。
4. **测试稳定性专项**：`RuntimeInvokeRecordingIntegrationTest.providerTimeoutShouldRecordFailed` 将 HTTP Mock 延迟与默认读超时均设置为 60 秒，时序竞争会导致慢/偶发失败。优先复用独立 1s 读超时 Test Context 和 3s 延迟，或更明确地将超时测试隔离；不要为这一个问题引入新技术。资源测试：`InvocationRecordRetentionIntegrationTest` 的 AfterEach 检查 Application 删除状态并清理测试 Team，`onlyOneEnableScheduling` 不要声称已经扫描全项目唯一注解。
5. **架构检查/成本**：审视每次业务请求同步入库的额外延迟与允许丢记录的 best-effort 语义，清理 SQL 尚无 created_at 索引/分批能力。只登记未来大数据量风险，不提前创建 V8 / MQ。
6. **技术债清单**：持续标出 TD-001 开发期管理 Basic、TD-009 CI、TD-010 Provider Master Key Rotation、TD-011 出站 URL/SSRF；区分公共生产环境前必须处理与下一业务阶段可延迟事项。
7. **文档完成**：核对/更新 `project-overview`、`requirements`、`architecture`、`domain-model`、`module-design`、`roadmap`、`progress`、`api-design`、`database-design`、`knowledge-map`、`interview-notes`。明确实际实现、历史状态与未来计划；P3 Closeout 结果单独形成报告。
8. **ADR**：ADR-003 已涵盖 MySQL 同步 best-effort 的决策；没有新重大技术决策则无需新 ADR。
9. **知识与面试**：按 P3 实际进度整理 ①为什么需要调用记录而不是仅日志，②为什么 MySQL 而不是 MQ，③Facade 异常透传/保存失败隔离，④按 Application 限量查询及索引，⑤30 天清理、UTC 边界与数据增长风险。
10. **关闭门槛**：只有业务闭环、代码/测试/架构评审无阻塞、项目文档一致且明确遗留风险，才标记 Phase 3 CLOSED。Phase 4 当前不启动，不由开发导师自行决定方向。

### Acceptance Criteria

- 可解释并验证一次真实请求：模型输出 → SUCCESS/FAILED → 管理端查询 → 定时清理。
- 上游 Provider/Client 认证与错误 HTTP 契约未改变；凭证/Prompt/模型正文未入库或泄漏错误；保存失败不覆盖原结果。
- 对 P3-T05 当前新增测试提供可复现测试结果，或明确记录未独立验证的缺口，不虚构统计；超时边界测试不要持续留下不稳定性风险。
- 现有 V7 索引和保留策略合理，性能/扩容问题登记但不提前过度优化。
- 核心文档、阶段知识点、面试表达、技术债与实际 GitHub main 一致。
- 有完整 Closeout 记录与结论；后续 Phase 4 由总控另行冻结业务目标。

### 教学重点

复盘整个 Phase 3 的真实工程演进，而不是重复基础 CRUD：**业务可观测性与业务审计的区别、best-effort 一致性取舍、异常不能被辅助逻辑覆盖、时间与数据库查询的稳定性**。

**Execution Gate：P3-T06 ACTIVE，Phase 3 尚未关闭，Phase 4 NOT STARTED。** P3-T06 以阶段审核和文档为主；若查出实质缺陷，作为一个集中修复包处理，不为小问题继续拆小任务。

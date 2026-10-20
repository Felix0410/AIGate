# AIGate Current Phase — Phase 3 CLOSED

> 本文件由总控维护。总控决定业务目标、架构、任务和验收；开发导师只按冻结任务讲解与编码。2026-10-20 Phase 3 正式完成收尾；下一 Phase 未获启动确认，禁止提前编码。

## 1. Official Current Status（2026-10-20）

| Phase / Task | Status |
|---|---|
| Phase 1 — Foundation & Core Identity | CLOSED |
| Phase 2 — Model Registry & Single Model Proxy | CLOSED / ACCEPTED |
| **Phase 3 — Invocation Records & Basic Visibility** | **CLOSED / ACCEPTED** |
| P3-T01 — Scope & ADR-003 | DONE / ACCEPTED |
| P3-T02 — Flyway V7 / Entity / Mapper | DONE / ACCEPTED |
| P3-T03 — SUCCESS best-effort recording | DONE / ACCEPTED |
| P3-T04 — Management query by Application | DONE / ACCEPTED |
| P3-T05 — FAILED recording / 30-day retention / E2E | DONE / ACCEPTED |
| P3-T06 — Phase Closeout | **DONE / ACCEPTED** |
| Phase 4 | **NOT STARTED** |

正式阶段验收报告：`docs/phase-3-closeout.md`。用户明确要求后续任务按**完整业务交付包**划分，不再把表、Service、API、测试分成大量微任务；这一原则从后续 Phase 继续生效。

## 2. Phase 3 已实现业务闭环

```text
Client POST /v1/invoke + Application Bearer Key
 → /v1 Runtime Security + @Valid
 → InvocationTrackingFacade
 → ModelProxyService → ProviderAdapter/RestClient → Provider
    ├─ SUCCESS → best-effort save SUCCESS / null errorCode
    └─ FAILED → best-effort save FAILED / safe errorCode → original error
 → MySQL V7 invocation_record(applicationId,status,errorCode,durationMs,createdAt)

Management Basic GET /api/applications/{id}/invocations?limit=20
 → verify Application → WHERE application_id=? ORDER BY id DESC LIMIT 1..100
 → six-field JSON array, 404 if App missing, [] if no history

Daily 03:00 UTC Scheduled Retention
 → delete records WHERE created_at < now(UTC) - 30 days
```

默认同步 best-effort：记录写入失败不更改上游模型调用成功结果/失败异常，不保证每条绝对持久化，不可用于可靠计费或严格合规审计。DB V1–V7，不存在 Redis/MQ/ES/微服务。敏感的 Prompt、模型输出正文、Client Key 和 Provider Credential 不存入记录表。

## 3. P3-T06 Final Acceptance

- 2026-10-20 总控审核最新 GitHub main 和 `docs/phase-3-closeout.md`，确认 P3-T06 **DONE / ACCEPTED**。
- 新增独立超时 E2E：1 秒 read timeout vs 3 秒 Mock Provider 延迟；原 60s/60s 竞态已去除。验证 504 PROVIDER_TIMEOUT、一条 FAILED、无出站重试。
- 测试结束清理完整的 Application / Deployment / Model / Provider / Team 资源，RetentionIntegrationTest 的资源清理/断言已增强。
- GitHub 没有该阶段的 CI workflow/status；本次未取得 Maven 全量测试数字。用户的「提交代表本地测试通过」约定用于阶段代码接收，但**不写成独立测试已执行的统计**。P3-T02 的 149/0/0/0 是更早代码基线，不能代替 P3-T06 的测试结果。
- 非阻塞风险：多步测试 fixture 创建中途失败可能遗留资源；单次 MySQL 删除对大表会有性能成本；多实例调度将需单独协调。
- 生产化关键技术债：TD-001 管理 Basic、TD-009 CI Gate、TD-010 Provider Key Rotation、TD-011 Provider Endpoint SSRF。

## 4. Next Phase Gate

**Phase 4 目前 NOT STARTED，开发导师不得自行进行功能选题、架构选择或编写 Phase 4 代码。** 总控将结合现有模型代理能力规划下一完整业务目标，冻结 Business Goal / Scope / Main Flow / Architecture / Tasks / Acceptance Criteria / Knowledge Points，并在用户确认启动后将本文件替换为 Phase 4 的真实执行合同。

Phase 3 阶段已 CLOSED，本文件只留当前事实。历史设计细节在 `docs/phase-3-closeout.md`、`docs/progress.md`、`docs/architecture.md` 和对应 GitHub commits。

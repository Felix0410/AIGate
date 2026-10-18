# AIGate Progress

> 当前任务事实与验收记录。历史详细设计、各次验收证据参见 `docs/phase-2-closeout.md`、GitHub 历史提交及核心文档。

## 1. Current Status

- **Phase 1 — CLOSED**
- **Phase 2 — CLOSED / ACCEPTED（2026-10-18）**
- **Phase 3 — ACTIVE**
- **Current Task：P3-T04 — Management Invocation History Query（ACTIVE）**
- **Next Task：P3-T05 — Failed Invocation Recording（NOT STARTED）**

Phase 2 的关闭已通过完整十项检查：功能、Code Review、测试、架构、技术债、progress、architecture、必要 ADR、知识点和面试表达。

## 2. Phase 2 任务里程碑

| Task | 实际完成 | 状态 |
|---|---|---|
| P2-T01 | Provider / Model / ModelDeployment 实体、表与管理接口 | DONE / ACCEPTED |
| P2-T02 | CredentialService AES-256-GCM，Provider 密文存储 | DONE / ACCEPTED |
| P2-T03 | Application.defaultDeploymentId 绑定/切换/解绑 | DONE / ACCEPTED |
| P2-T04 | Application API Key CREATE/LIST/REVOKE、Hash 存储 | DONE / ACCEPTED |
| P2-T05 | 两条 SecurityFilterChain、API Key Runtime 认证和 ApplicationIdentity | DONE / ACCEPTED |
| P2-T06 | InvokeRequest、InvokeMessage、MessageRole、InvokeResponse 校验与 JSON 契约 | DONE / ACCEPTED |
| P2-T07 | ProviderAdapter + Registry + OpenAiCompatibleProviderAdapter | DONE / ACCEPTED |
| P2-T08 | RuntimeInvokeController、ModelProxyService、ProviderHttpClient + RestClient HTTP Happy Path | DONE / ACCEPTED |
| P2-T09 | ProxyErrorType：Provider HTTP/超时/坏 JSON、安全错误映射 | DONE / ACCEPTED |
| P2-T10 | E2E：Key 生命周期、默认 Deployment 切换、错误、真实超时、测试清理 | DONE / ACCEPTED |
| P2-T11 | Phase Closeout：阶段检查、文档、知识点、面试复盘及技术债 | **DONE / ACCEPTED** |

### 已验证的 Runtime 主流程

```text
/api/** HTTP Basic 配置 Provider / Model / Deployment / Application
→ Application API Key 只创建时返回明文（高熵 Secret SHA-256 Hash 存储）
→ POST /v1/invoke + Bearer Key
→ ApplicationIdentity
→ Application.defaultDeploymentId → enabled ModelDeployment → Provider
→ ProviderAdapter → RestClient → 上游 Provider
→ InvokeResponse 或脱敏 ApiErrorResponse
```

上游 Provider Credential 使用 AES-GCM 加密存储，出站时解密；不会把客户端 Application API Key 转发上游。

### 测试结果与证据边界

用户于 2026-10-17 提供本地 Maven 输出：

```text
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

不是 GitHub CI status / workflow artifact；未单独记录完整退出码与 Surefire XML。因此 Phase 2 功能验收通过，但 CI 缺口 **TD-009 保留**。

### 数据库状态

Flyway V1–V6，MySQL 8.4；无新增 Usage/Quota/InvocationRecord 表。Key 状态 ACTIVE/REVOKED，新 Key 不会自动撤销旧 ACTIVE Key。

## 3. Phase 3 — 总控已冻结的规划（2026-10-18）

**Business Goal：模型调用记录与基础可见性。** 管理员按 Application 查询最近的 SUCCESS/FAILED、errorCode、durationMs 和 createdAt。不做 Prompt/模型回答存储、计费或复杂可观测系统。

总控已完成 P3-T01：核对 P2 Runtime 代码、比较普通日志/结构化日志/MySQL/MQ，决定**单 MySQL 的最小元数据同步 best-effort 持久化**；ADR-003 已提交，状态 DONE / ACCEPTED。

| Task | Work | Status |
|---|---|---|
| P3-T01 | 目标/范围/架构与 ADR 冻结 | **DONE / ACCEPTED** |
| P3-T02 | Flyway V7 + InvocationRecord Entity/Mapper/DB Test | **DONE / ACCEPTED** |
| P3-T03 | 成功调用记录、写入失败隔离 | **DONE / ACCEPTED** |
| P3-T04 | HTTP Basic 管理查询最近历史、limit 1..100 | **ACTIVE** |
| P3-T05 | 失败调用记录、安全错误分类 | NOT STARTED |
| P3-T06 | 30 天保留/清理、完整 E2E 回归 | NOT STARTED |
| P3-T07 | Phase 3 关闭评审和文档/面试整理 | NOT STARTED |

计划数据：`invocation_record(id,application_id,status,error_code,duration_ms,created_at)`，index(application_id,id)，FK application_id CASCADE。**P3-T02 已实现并验收 V7，当前数据库代码 schema 为 V1–V7。**

P3-T02 仅建数据基础，不改 Runtime 业务行为；后续遵循 Happy Path → 验证 → 失败记录 → 清理与架构检查。

### 验收推进

单任务经总控验收通过后自动更新文档并启动下一任务。开发导师没有自主决定业务范围的权限；变更需求先回到总控。

## 4. 技术债

- **生产/安全关键：** TD-001（HTTP Basic 管理面）、TD-011（Provider 出站 URL/SSRF）、TD-010（Provider 主密钥轮换）
- **质量及运维：** TD-009（CI Gate）、TD-006（缺 requestId / traceId / path）、TD-008（增量补全测试）
- **按实际业务演进：** TD-002（授权模型）、TD-003（事务边界）、TD-004（跨模块 Mapper 依赖）、TD-005（字符串错误码）、TD-007（单组织唯一约束）

Phase 3 规划需考虑技术债，但不能为丰富技术栈而在一个任务里全部偿还。

## 5. Advancement Rule

用户已授权：单任务验收通过、无阻塞时完成收尾文档后自动启动下一任务；未通过则修复复验。跨 Phase 先完成 Phase Closeout。

**当前：Phase 3 ACTIVE / P3-T01 ACTIVE；P3-T02 NOT STARTED。**


---

## P3-T02 验收记录（2026-10-18，PENDING）

实现提交：`b790b2a780d333226ff0d6afff91d2c69aaa930f`。

**代码审查通过**：V7 `invocation_record` + CHECK/FK CASCADE/复合索引、InvocationRecord/InvocationStatus/Mapper 与 10 个数据库测试方法；没有改动 P2 Runtime，也没有提前增加调用记录 API。当前测试源码覆盖 CRUD、非法状态、负 duration、无效 Application FK、CASCADE、按 App 最新 ID 限量查询。数据库文档时间线已修正：Phase 2 实际 V1–V6，V7 属于 Phase 3。

**待完成条件：** 提供在本次 V7 代码上实际运行的 Maven 全量测试结果。GitHub 未提供 CI workflow/status，Phase 2 的 138 测试不能证明新增测试通过。

**历史 PENDING 状态已经解除，最新验收见下方。**

## P3-T02 验收通过（2026-10-18）

代码提交 b790b2a：V7 invocation_record、Entity、枚举、Mapper、MySQL Testcontainers DB 测试；代码审查已通过。用户提供新的本地 Maven 汇总：Tests run 149 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS。这不是 CI 记录；TD-009 保留。

**P3-T02 DONE / ACCEPTED；P3-T03 ACTIVE。**

## P3-T03 冻结执行范围

让正常完成 POST /v1/invoke 的 Application 记录一条 SUCCESS、空 errorCode、durationMs 和 createdAt。通过轻量 TrackingFacade 包装现有 ModelProxyService；记录插入失败不能把 Provider 的正常响应变成错误。T03 不记录 FAILED、不加查询 API、不做 30 天清理。详细验收标准以 docs/current-phase.md 为准。


## P3-T03 Acceptance — 2026-10-18

实现 `ee38c95f20781de6aa219ea80b99ab590e7016bb`：`InvocationTrackingFacade` 显式包装 `ModelProxyService`，成功调用后以 `System.nanoTime()` 测耗时，保存一条 `SUCCESS` / null errorCode 记录，写入异常被安全固定 warn 隔离，不改变原 Provider `InvokeResponse`；Provider 失败继续透传，不写 FAILED。

单元测试覆盖 Mapper 写入失败隔离和 Provider 异常透传；`RuntimeInvokeRecordingIntegrationTest` 通过 MockMvc + MySQL Testcontainers + local HttpServer 验证真实 HTTP 成功入库、重复请求两条记录、401/400 不记录、Provider 500/429 回归。

**P3-T03 DONE / ACCEPTED（静态代码与测试覆盖验收，无阻塞）。** GitHub 无该提交的 CI status/workflow；本次未收到独立 Maven 汇总，不宣称新测试总数。沿用用户的提交前测试工作约定，TD-009 仍开放。

## P3-T04 ACTIVE — Management Invocation History Query

冻结新管理面 API：`GET /api/applications/{applicationId}/invocations?limit=20`。复用 HTTP Basic，按 Application 存在性检查（404 APPLICATION_NOT_FOUND）、限量参数（1..100，默认20，非法为400 INVALID_REQUEST）执行 SQL `WHERE application_id=? ORDER BY id DESC LIMIT ?`，返回仅有六字段的 DTO 数组，无记录则 `[]`。必须测试跨 Application 隔离、排序和上限、401/404/400、安全响应与 P3-T03 回归。不做 FAILED 自动写入/保留清理。详细需求见 `docs/current-phase.md`。

**Current: P3-T04 ACTIVE；P3-T05 NOT STARTED。**

# AIGate Current Phase

> 总控决定业务目标、技术方案、任务拆分、验收；开发导师只按已冻结任务讲解、编码、测试和 Review。GitHub 文档是项目事实基线。

## 1. Phase State

- Phase 1：**CLOSED**
- Phase 2：**CLOSED / ACCEPTED**
- Phase 3：**ACTIVE — Invocation Records & Basic Visibility（调用记录与基础业务可见性）**
- **P3-T01 — Business Goal & Architecture Baseline：DONE / ACCEPTED（总控）**
- **Current Task：P3-T02 — Invocation Record Schema（ACTIVE / 待 Maven 测试证据）**
- Next：P3-T03 — Successful Invocation Recording（NOT STARTED）

用户 2026-10-18 要求由总控决定项目内容。P3-T01 已由总控基于真实代码与 Phase 2 闭环制定明确目标、方案、任务和 ADR-003；开发导师不再自主选题。

## 2. Business Goal

Phase 2 已经能通过 `POST /v1/invoke` 真实调用 Provider、返回结果或脱敏错误，但管理端无法按 Application 查看跨请求的成功/失败、失败类别和耗时历史。

Phase 3 的**唯一核心目标**：管理员可以查询某 Application 最近的模型调用结果、分类错误码与延迟，进行基础排障。**不是**计费账本、聚合报表、分布式链路追踪或复杂运维平台。

## 3. Planned Main Flow

```text
POST /v1/invoke + Application API Key
→ P2 Runtime Security / @Valid
→ 小型 Tracking Facade（P3-T03）
→ P2 ModelProxyService / ProviderAdapter / RestClient
→ Provider success / failure
→ best-effort save metadata to MySQL invocation_record

Management HTTP Basic
→ GET /api/applications/{id}/invocations?limit=20
→ MySQL bounded latest-record query
```

只记录**认证和入参校验通过后，实际进入 Invoke 业务**的请求。不在无效 API Key、非法 Request 阶段产生记录；失败写入不得覆盖原始 Provider 结果或异常。这个表不是 exactly-once 审计/计费账本。

## 4. Phase 3 Scope

- Flyway V7 一张 `invocation_record` 表、Entity、Mapper
- 成功调用记录 SUCCESS；后续失败记录 FAILED + 安全分类 errorCode
- 记录 elapsed milliseconds，并按 Application 查询最近记录
- 写入失败 best-effort，不改变 `InvokeResponse` 或原异常
- 30 天保留，最终通过简单定时清理控制数据量
- 完整 Testcontainers + Mock HTTP Provider E2E、隐私检查和阶段收尾

**不做** Redis / MQ / Elasticsearch / 微服务、token Usage/Cost/Quota、模型路由、SSE、异步事件、复杂聚合、原始 Prompt/Output 存储。

## 5. Frozen Tasks

| Task | Deliverable | Status |
|---|---|---|
| P3-T01 | 总控确定目标、范围、候选方案/取舍、ADR 与任务路线 | **DONE / ACCEPTED** |
| P3-T02 | V7 invocation_record 表、Entity/Mapper、DB 测试 | **ACTIVE** |
| P3-T03 | 成功 Invoke 的最小记录和记录失败隔离 | NOT STARTED |
| P3-T04 | 管理端按 Application 查询最近调用记录 | NOT STARTED |
| P3-T05 | 失败 Invoke 记录和错误码、隐私检查 | NOT STARTED |
| P3-T06 | 30 天清理 + 完整 E2E/回归/增长检查 | NOT STARTED |
| P3-T07 | Phase 3 Closeout：验收、架构、技术债、文档、知识点、面试 | NOT STARTED |

只开展当前 ACTIVE Task。每次任务验收通过，**由总控**更新文档并自动启动下一任务。跨 Phase 必须先完成 Closeout。

## 6. Frozen Data Contract（PLANNED，非已实现）

V7: `invocation_record`

| Column | Type | Semantics |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| application_id | BIGINT NOT NULL | FK → application.id ON DELETE CASCADE |
| status | VARCHAR(16) NOT NULL | SUCCESS / FAILED |
| error_code | VARCHAR(64) NULL | SUCCESS 为空；FAILED 为固定安全错误码 |
| duration_ms | BIGINT NOT NULL | 非负耗时 |
| created_at | TIMESTAMP(6) NOT NULL | 记录时间 |

索引：`INDEX(application_id, id)`，服务于 `WHERE application_id=? ORDER BY id DESC LIMIT ?`。

**不保存** Prompt、模型返回文本、Client API Key、Provider Credential、内部 Provider URL、原始上游错误 Body。暂不存 Deployment ID：最小排障目标首先是“哪个 Application 何时失败、原因类别及耗时”；避免为多模型路由提前设计复杂记录模型。

`ON DELETE CASCADE` 因为这只是 Application 的基础排障历史，删除 Application 不应因为记录表被阻塞；正式法定审计/收费不可直接复用此设计。

## 7. Frozen Management Query（PLANNED，非已实现）

```http
GET /api/applications/{applicationId}/invocations?limit=20
Authorization: Basic <management credentials>
```

默认 20，限制 **1..100**，超出返回 400；按 `id DESC` 排序；不存在 Application 返回 404，无记录返回 `[]`。每个 item 仅 `id, applicationId, status, errorCode, durationMs, createdAt`。本阶段不实现全量导出、跨 Application 聚合或用户级细粒度权限。

## 8. ADR / Tradeoffs

`docs/adr/ADR-003-invocation-recording-persistence.md` 已决定 **MySQL 同步 best-effort 保存最小元数据**。

- 当前普通日志不适合稳定的按 Application 业务历史查询。
- 复用 MySQL 比新增日志搜索平台、MQ 或 Redis 简单，容易用 Testcontainers E2E 测试。
- 成本：每次有效 Invoke 多一次 DB 写；若记录保存失败可能丢历史，**不保证 exactly-once**。
- 30 天清理是因为每次调用都会产生记录，必须避免无界增长；主业务打通之后再做清理。
- 将来只有出现真实吞吐/可靠性需求时才评估批量写入、事件队列或 Outbox。

## 9. Current Task P3-T02 — ONLY

**Business Goal:** 为未来的模型调用记录提供最小数据库基础；此任务不应改变实际 `POST /v1/invoke` 行为。

**Tasks:**
1. 新建 Flyway **V7**，保持 V1–V6 不变；字段、FK、复合索引严格按第 6 节。
2. 新增简单 `invocation` 模块（或项目一致命名）的 `InvocationRecord` Entity 与 `InvocationRecordMapper`。
3. 处理 SUCCESS/FAILED 状态映射，避免非法状态值无意落库；不设计复杂状态机。
4. 使用现有 MySQL Testcontainers 测试 migration、CRUD、FK、CASCADE、基本索引及异常/nullable 字段。
5. 更新 `docs/database-design.md` 为“V7 已实现”，但只能在真实代码实现并验收后标记 DONE。
6. 运行 Maven 测试，提供测试数/失败/错误/跳过汇总。

**Acceptance Criteria:**
- V7 可在真实 MySQL Testcontainers 自动迁移；历史 migration 不被改写
- 可保存并读取 SUCCESS 和 FAILED 两类记录；errorCode nullable，耗时非负
- FK 和级联删除与既有 Application 管理 API 兼容
- `(application_id,id)` 索引可用于最新记录有界查询
- 表结构没有敏感请求/响应/凭证字段
- `POST /v1/invoke`、Security 和 Provider Adapter 均未被提前修改
- 没有新 Controller、MQ、Redis 或额外 Framework
- 完整现有测试回归并提供实际执行结果

**Knowledge Points（开发导师需讲清楚）：** 业务记录表的必要性、Flyway V7、FK CASCADE、复合索引与按 App 查询、MyBatis-Plus Mapper、MySQL Testcontainers。

## 10. Future Failure & Query Rules（NOT STARTED）

P3-T03 采用 Tracking Facade 包装现有 `ModelProxyService`，不修改 ProviderAdapter/ProviderHttpClient 的职责。以单调时钟测耗时；保存失败只警告，不覆盖真实成功响应。P3-T05 才处理模型调用失败，分类错误和未知异常的安全码，不吞异常。P3-T06 约定默认保留 30 天，简单每日清理和边界测试，非计费账本。

已知风险继续保留：TD-001 临时管理 Basic、TD-009 无 CI Test Gate、TD-010 Provider Master Key Rotation、TD-011 Provider Endpoint SSRF。公网发布前仍需独立处理。

**Execution Gate: P3-T02 ACTIVE。开发导师现在仅执行 Schema/Mapper/测试，完成 Review 后回总控验收。**

---

## P3-T02 验收检查（2026-10-18）

代码实现提交：`b790b2a780d333226ff0d6afff91d2c69aaa930f`。

静态 Code Review 已通过：Flyway V7、InvocationRecord Entity/Mapper、SUCCESS/FAILED 和非负耗时 CHECK、Application 外键 CASCADE、(application_id,id) 索引、MySQL Testcontainers CRUD/约束/级联测试。提交仅包含数据库/映射/测试和数据库文档，未提前接入 Runtime 调用链，也未新增 API 或中间件。

**尚未最终 ACCEPTED：** 最新代码提交没有 GitHub CI statuses/workflow run，本轮未收到 V7 后的完整 Maven 测试执行汇总。旧的 Phase 2 138/0/0/0 不可复用作为新代码测试证据。

复验只需在 Java 21 + Docker 可用环境执行 `./mvnw test`，提供测试统计（Tests run, Failures, Errors, Skipped），并处理实际失败项。现有测试已经覆盖主要数据约束，额外的 INFORMATION_SCHEMA 索引断言可以后续改进但不是阻塞项。

**状态保持 P3-T02 ACTIVE，P3-T03 NOT STARTED。** 实测无失败并经确认后，总控按自动推进规则完成验收并启动 P3-T03。
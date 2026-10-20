# AIGate Progress

> 当前任务事实与验收记录。历史详细设计、各次验收证据参见 `docs/phase-2-closeout.md`、GitHub 历史提交及核心文档。

## 1. Current Status

- **Phase 1 — CLOSED**
- **Phase 2 — CLOSED / ACCEPTED（2026-10-18）**
- **Phase 3 — CLOSED / ACCEPTED（2026-10-20）**
- **Current Task：None（Phase 3 已关闭）**
- **Next: Phase 4 — 未冻结业务范围；Phase 3 Closeout 完成后再决定**

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
| P3-T04 | HTTP Basic 管理查询最近历史、limit 1..100 | **DONE / ACCEPTED** |
| P3-T05 | FAILED 记录 + 30 天保留清理 + 完整 E2E（合并） | **DONE / ACCEPTED** |
| P3-T06 | Phase 3 Closeout（原 T07） | **DONE / ACCEPTED** |

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


## P3-T04 Acceptance — 2026-10-18

实现提交：`aae1546785e9daf7db3d40f989c2dfbaaede82ec`。

新增 InvocationQueryController / InvocationQueryService / InvocationRecordResponse / InvalidInvocationQueryException，并在 GlobalExceptionHandler 处理安全固定 400 INVALID_REQUEST。

已验收：管理 Basic 和 Runtime API Key 隔离，默认 limit=20/合法 1..100、非法 400，按 Application 过滤并 SQL ORDER BY id DESC LIMIT，存在但无记录返回 200 []、Application 不存在 404，固定六字段 DTO，测试涵盖跨 App、排序/限制与管理认证。

代码/测试覆盖审查没有阻塞缺陷；本次 GitHub 无 CI status/workflow 或独立 Maven 汇总，沿用户提交前自行测试的工作约定接受，不虚构测试数。**P3-T04 DONE / ACCEPTED**。建议后续增强“仅六字段”的精确 JSON 属性集合断言；非阻塞。

## 合并任务决策与当前 P3-T05

用户反馈拆分过细增加协作和总控开销。总控将原 T05（失败记录）与原 T06（30 天清理、E2E）合并为 **P3-T05 — Invocation Record Completion（ACTIVE）**；原 T07 Closeout 改为 **P3-T06**。此调整是任务组织变更，不更换 Phase 3 业务目标或 ADR-003 架构。

P3-T05 一次完成：Facade 保留原异常并记录安全 FAILED 码；写入异常不覆盖模型成功或失败；按 UTC 30 天边界做简单 Spring Scheduled 清理；Testcontainers/Mock Provider E2E 验证成功、失败、脱敏、管理查询、清理；Maven 全量回归提供结果。内部子任务无需逐个回总控验收。全部完成后统一 Code Review 并自动进入 P3-T06 Closeout。

**当前 Phase 3 ACTIVE / P3-T05 ACTIVE；P3-T06 NOT STARTED。**


## P3-T05 正式验收 — 2026-10-18

实现 `8262303df74598f6e76e68ad1f10ce40b096e2c6`，测试修复 `dfcc70afb3228c817c9b42693257474b75895384`。已审查：Facade 对 ModelInvocationException/InvalidProviderResponseException/其它 RuntimeException 记录安全 FAILED 码后原样抛异常；SUCCESS/FAILED 记录 best-effort 隔离；UTC 03:00 定时清理 30 天前记录；按 SQL cutoff 严格小于删除。MySQL Testcontainers/Mock Provider 测试覆盖错误分类、认证/校验零记录、混合管理查询、时间边界及 Scheduler 异常隔离。

**P3-T05 DONE / ACCEPTED（代码和测试覆盖验收）**。GitHub 没有 CI status/workflow，本次尚无独立提供的 Maven 全量统计；遵循提交前本地测试工作约定，不虚构 CI 结论。阶段收尾 P3-T06 需要记录最终可复现的 Maven 结果并处理测试稳定性问题。

**非阻塞测试债与 P3-T06 集中处理**：60 秒 Mock HTTP 延迟恰等于读取超时可能发生时序竞态；RetentionIntegrationTest 需加强清理后状态断言及测试 Team 清理；onlyOneEnableScheduling 单测并未证明整个项目注解唯一。数据库 created_at 暂无高效清理索引/批次，仅作为日后真实规模增长时的性能风险记录。

**当前 P3-T06 Phase Closeout ACTIVE；Phase 3 未关闭；Phase 4 未启动。** 核心执行清单见 docs/current-phase.md。


## P3-T06 阶段收尾代码复审（2026-10-20）

集中测试修复提交 `f3b0f94d6fbd79eb4305eeea7e2ec5fcd4c69c98` 已静态审查：独立 RuntimeInvokeTimeoutRecordingIntegrationTest 使用真实 RestClientConfig 配置 `aigate.provider.read-timeout-seconds=1`、本地 Provider 3 秒延迟，验证 504/PROVIDER_TIMEOUT、一条 FAILED 及无重试；移除了旧 60s 对 60s 的竞态测试。RetentionIntegrationTest 已在 AfterEach 按 Application→Team 顺序删除并核对 HTTP 200；Scheduler 测试纠正过度表述的方法名。没有改动生产代码或文档。

**待关闭缺口（测试资源可重复性）**：独立 timeout 测试新建 Provider、Model、ModelDeployment 使用固定名称，仅删除 Application 和 Team，未删除其他三个依赖实体。需要在测试中记录全部创建的 ID，以 Application→ModelDeployment→Model/Provider→Team 顺序清理，并在测试异常时仍执行；否则共享 Testcontainers DB 重复运行可能遭遇名称唯一冲突。P3-T06 仍 ACTIVE，不宣布 Phase 3 CLOSED。完整 Maven 本地统计尚未作为本轮独立证据提供；遵循提交前测试通过约定，不虚构 CI。


## P3-T06 最终代码复审完成（2026-10-20）

最终测试资源清理提交 `a2a68fe6b137aba78fdb359b7cd781c0fd228681` 已审查：`RuntimeInvokeTimeoutRecordingIntegrationTest` 增加 @AfterEach，完整保留 appId、providerId、modelId、deploymentId、teamId，依 Application → Deployment → Model → Provider → Team 外键依赖顺序，通过管理接口逐项删除且断言 HTTP 200。504/PROVIDER_TIMEOUT、单条 FAILED、无自动重试等断言保持；只修改测试文件。P3-T06 集中代码复审无已知阻塞生产缺陷。

边界说明：测试 setup 中途失败且 TimeoutSetup 尚未返回时，可能留下部分夹具资源，记录为非阻塞测试债。按“已提交”协作约定默认用户本地测试通过，但本轮未提供可引用的全量 Tests run/BUILD SUCCESS 数字，也无 GitHub CI status；不得引用 P3-T02 的 149 项作为当前统计。

已形成 `docs/phase-3-closeout.md` 候验收报告。**P3-T06 审查完毕、建议总控验收；Phase 3 是否 CLOSED 尚需总控确认。Phase 4 未启动。**


## Phase 3 Final Closeout（2026-10-20）
总控复核 P3-T01～T06，验收通过，**Phase 3 CLOSED / ACCEPTED**。用户的“Git 提交代表本地测试通过”约定不等于 CI 运行证据；本轮无独立 Maven 数字/CI workflow。历史149/0/0/0仅为 P3-T02 基线。测试超时1s/3s竞态修复与夹具清理已审查。TD-001/009/010/011仍开放。完整收尾报告：docs/phase-3-closeout.md。**Phase 4 当时尚未启动；最新状态见下文正式启动记录。**

---

## Phase 4 Official Start — 2026-10-20

**Phase 3 CLOSED / ACCEPTED；Phase 4 ACTIVE；Current Task P4-T01 — End-to-End Application Rate Limiting。**

这是总控确认过的业务扩展，尚未修改生产代码。冻结方案：

- V8 在 application 增 `requests_per_minute`（NULL=不限流，1..10000）。
- 管理面 Basic 的 GET/PUT `/api/applications/{id}/rate-limit`，enabled true/false 与 RPM 配对，单独返回策略 DTO；旧 Application CRUD 行为保持。
- 入站正常认证和参数校验之后、TrackingFacade 之前使用按 ApplicationId 的单 JVM 令牌桶，`System.nanoTime` 定速补充，`ConcurrentHashMap.compute` 原子扣减，多个 Key 共享桶。
- 本地超限返回 429 APPLICATION_RATE_LIMITED + Retry-After，既不向上游出站也不写入 InvocationRecord；Provider 429 原样保留 503 PROVIDER_RATE_LIMITED。
- 10分钟闲置桶清理，所有算法使用可控时钟做确定性/并发测试。

**本阶段仅两个整包任务：** P4-T01 完整业务代码+E2E（ACTIVE）；P4-T02 集中回归及阶段收尾（NOT STARTED）。由总控维护文档并按每个交付包验收，不把每个小类/表/API 拆成独立开发任务。

**ADR-004 已接受设计决策，未实施的 V8/管理 API/Token Bucket 仍属 PLANNED。** TD-001/009/010/011 继续保留，Phase 5 NOT STARTED。


## 2026-10-20 — 暂停 P4 编码，重新核对产品主线

用户要求在进一步开发前，先从最上层确认 AIGate 最终产品流程，再逐层核对业务主体、用户/Team/Application 权属、额度归属、使用流程、功能优先级。**P4-T01 STATUS = PAUSED / NO CODING**；原“ACTIVE”和 V8/令牌桶/管理 API 仅是此前已批准但目前暂停的设计，不再是开发导师执行授权。Phase 1～3 CLOSED 的已实现事实保持。由总控在用户逐层确认后修改规划、ADR 和 current-phase，再决定 P4 是否继续做速率限制或优先补用户/额度闭环。


## Product Scope Decision — 2026-10-20

用户明确 AIGate 是**公司内部已有 AI Agent 的统一接入、资源与模型流量治理平台**，不开发 Agent/编排器/执行引擎。目标包括员工与团队差异化额度、团队额度池和审批、紧急任务资源优先、Agent 可用模型安全策略、模型成本和可见压力监测；确切的用户/Team/Agent/Application 关系、额度归属及实现顺序尚待逐层确认。已由总控写入 project-overview、requirements、current-phase。**P4-T01 持续 PAUSED；旧 Application 限流设计仅为候选，不能继续编码。**


## Product Discovery Decision #3 — 2026-10-20

已正式整理 `docs/product-business-rules.md`：员工主团队/临时跨团队任务、管理员纳管而不开发 Agent、Agent=Application 1:1、按真实员工扣费（无人值守可独立专用账号）、**按周的授权 Agent 免费额度→员工个人额度→团队额外申请**、紧急任务临时额度和优先权、员工选择 Agent 可用模型/同模型多部署路由。业务方向已确认，**按周细则、可信员工归属、跨团队谁出额度、费用和异常结算尚未确认**。旧 P4-T01 仍 PAUSED；文档由总控维护，不授权开发导师编码。

# AIGate Progress

## 1. Current Status

**Phase 2 — Model Registry & Single Model Proxy：ACTIVE**

Current Task: **P2-T11 — Phase Closeout（ACTIVE）**

Next Planned: **Phase 3 (only after completed P2-T11 closeout)**

P2-T10 已于 2026-10-17 获得用户本地 Maven 测试汇总（138 / 0 / 0 / 0）并验收通过；按自动推进规则启动 P2-T11。

## 2. Phase 2 Tasks

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | DONE |
| P2-T07 | Provider Adapter | **DONE / ACCEPTED** |
| P2-T08 | RestClient + Single Model Proxy | **DONE / ACCEPTED** |
| P2-T09 | Provider Error Mapping | **DONE / ACCEPTED** |
| P2-T10 | End-to-End Integration Test | **DONE / ACCEPTED** |
| P2-T11 | Phase Closeout | **ACTIVE** |

---

## 3. 已完成阶段记录

### P2-T01 / P2-T02 / P2-T03

- Provider / Model / ModelDeployment Registry CRUD
- Provider Credential AES-GCM at rest
- Application.defaultDeploymentId (nullable, FK RESTRICT)

### P2-T04 / P2-T05

- Application API Key CREATE / LIST / REVOKE
- `aig_live_<keyId>.<secret>`，数据库 SHA-256 hash
- Runtime `/v1/**` API Key Authentication
- `ApplicationIdentity(applicationId, applicationName, teamId)`
- `/api/**` HTTP Basic 与 `/v1/**` API Key 使用双 SecurityFilterChain

### P2-T06

```text
InvokeRequest(messages, temperature?, maxTokens?)
InvokeMessage(role, content)
MessageRole(system/user/assistant)
InvokeResponse(content, model, finishReason)
```

完成 DTO、JSON 序列化、Validation 单元测试。

### P2-T07 — Provider Adapter（本次验收）

实现提交：`14f0203d91dac394bc90c71bb8cf8bc1bccedbc7`

生产类：

```text
provider.adapter.ProviderAdapter
provider.adapter.ProviderAdapterRegistry
provider.adapter.OpenAiCompatibleProviderAdapter
provider.adapter.InvalidProviderResponseException
provider.adapter.dto.OpenAiChatRequest
provider.adapter.dto.OpenAiChatMessage
```

已实现：

```text
InvokeRequest + remoteModelName
→ ProviderAdapterRegistry
→ OpenAI-Compatible model/messages/temperature/max_tokens JSON

OpenAI-Compatible response JSON
→ choices[0].message.content / model / finish_reason
→ InvokeResponse
```

Registry 对无匹配类型、重复注册明确失败；无效 JSON、空 choices、缺失必要响应字段不伪造成功。

新增测试：

```text
OpenAiCompatibleProviderAdapterTest
ProviderAdapterRegistryTest
```

覆盖消息顺序、role、小写 JSON、null 参数省略、remoteModelName、成功响应、额外字段忽略、无效结构和 Registry 行为。

**验收结果：P2-T07 COMPLETED / ACCEPTED。**

当前无阻塞性问题；没有必要追加 ADR（延续已冻结的轻量 Adapter 设计）。未发现新技术债。最新实现 commit 无 CI status / workflow run，TD-009 继续保留。

---

## 4. P2-T08 Accepted Implementation

### Business Goal Achieved

> 已打通真实单部署、同步非流式模型调用 Happy Path。

```text
POST /v1/invoke + Application API Key
↓
ApplicationIdentity
↓
Application.defaultDeploymentId
↓
ModelDeployment(enabled) + Provider
↓
ProviderAdapter
↓
CredentialService.decrypt (if needed)
↓
RestClient POST {endpointUrl}/chat/completions
↓
Provider HTTP 200 JSON
↓
Adapter.parseResponse
↓
InvokeResponse
```

### Implemented Components / Scope

最小组件：

- RuntimeInvokeController
- ModelProxyService
- Spring RestClient configuration / small HTTP boundary

需求：

- Controller `@Valid InvokeRequest`，只从 SecurityContext 读取 ApplicationIdentity
- App 默认 Deployment 必须配置且 enabled，拒绝无配置/禁用的调用
- Provider Type 选择 Adapter
- endpointUrl 统一解释为 API base URL，拼接 `/chat/completions`
- 上游 Credential 仅在有密文时解密并注入 Bearer Header
- 客户端 API Key 绝不转发 Provider
- RestClient 连接与读取超时有界；不自动重试
- HTTP 200 成功经过 Adapter 真正返回 InvokeResponse

### Tests — Present in Repository

测试类 `RuntimeInvokeIntegrationTest` / `ProviderHttpClientHttpTest` / `ModelProxyServiceTest` 包含以下验证：

- ACTIVE Key 通过 `POST /v1/invoke` 并触发真实 Mock HTTP Provider
- 无 Key 401；请求校验失败 400
- 正确 `remoteModelName` 和上游路径/JSON
- 有/无 Provider Credential 的 Authorization Header 行为
- 无默认 Deployment / disabled Deployment 不出站
- 上游成功响应原样走 Adapter，非硬编码结果

P2-T08 验收通过（提交 `5edbd46`、`6b1501e`），已通过代码与测试覆盖检查；GitHub 当前没有 CI status/workflow run，不能声称 CI 实际运行成功。

当前尚未解决的错误语义（例如缺少默认 Deployment 时的通用 500）已作为 P2-T09 输入。P2-T10 再完成全量失败路径的 E2E 测试矩阵。

当前预计不新增 Flyway，仍为 V1–V6。

---

## 5. Architecture / Security

仍保持：

```text
模块化单体
MySQL 8.4
Spring MVC
Spring Security 双链
MyBatis-Plus
AES-GCM + SHA-256
Provider Adapter
```

P2-T08 已增加 RestClient 同步出站能力，3 秒连接超时、60 秒读取超时（均可配置），无自动重试。暂不引入 WebFlux / SSE / MQ / Redis / Routing / Microservices / Retry / Fallback / Circuit Breaker。

---

## 6. 技术债

继续保留：

- TD-001 HTTP Basic 为临时管理面认证
- TD-002 暂无 Role / Permission
- TD-003 暂无统一事务设计
- TD-004 跨模块存在少量 Mapper 直接依赖
- TD-005 Error Code 使用字符串
- TD-006 ApiErrorResponse 缺少 traceId / requestId / path
- TD-007 单 Organization 假设下的唯一约束
- TD-008 测试聚焦关键链路
- TD-009 暂无 CI Test Gate
- TD-010 Provider Master Key Rotation 尚未设计

---

## 7. Execution Rule

用户于 2026-10-17 调整：验收通过、无阻塞问题，完成收尾与必要文档后**自动启动下一任务**；未通过先修复复验；跨 Phase 必须先完成完整 Phase Closeout。

**当前 P2-T08/P2-T09 DONE / ACCEPTED；P2-T10 ACTIVE；P2-T11 NOT STARTED。**


---

## 8. P2-T08 Acceptance Record — 2026-10-17

Actual implementation:

```text
RuntimeInvokeController POST /v1/invoke
ModelProxyService
ProviderHttpClient
RestClientConfig
ModelInvocationException
```

Verified code behavior:

- Application API Key → ApplicationIdentity → fresh Application.defaultDeploymentId
- ModelDeployment exists and enabled, Provider resolved, correct Adapter selected
- Credentials decrypted only when stored and only Provider Credential forwarded upstream
- POST `{endpointUrl}/chat/completions` via RestClient, response processed by adapter
- Explicit fail/no outbound on absent or disabled Deployment
- Controller `@Valid` and existing 400/401 behavior
- True HTTP Happy Path verified by tests against a JVM-local Mock Provider plus Testcontainers MySQL
- No new Flyway migration; current baseline remains V1–V6

Review result: **COMPLETED / ACCEPTED**, no blocking issues.

Evidence limit: Test code reviewed but GitHub latest code commit has no CI statuses or workflow runs. TD-009 remains.

New technical debt:

**TD-011 — Provider endpointURL restrictions / SSRF hardening before public production deployment.** Provider Endpoint is configured by an admin yet used for arbitrary server-side outbound HTTP; validate schemes and restrict private/metadata destinations according to deployment policy. Keep localhost test fixture working during Phase 2.

## 9. P2-T09 Active Scope

Goal: safely translate upstream HTTP failures (400, 401/403, 429, 5xx), network failures, timeouts and malformed upstream responses into deterministic AIGate errors; map missing/disabled default Deployment to explicit local configuration errors rather than generic 500.

Rules:

- Provider HTTP 401/403 ≠ client API Key 401
- Provider rate limit ≠ AIGate's own quota
- Do not pass raw upstream body, internal URL, credentials, Authorization or Prompt to Client or logs
- No retry/fallback
- Maintain success behavior and request validation

Acceptance: mapping code and unit / local HTTP / MockMvc tests reviewed. P2-T09 DONE / ACCEPTED；P2-T10 ACTIVE。


---

## 10. P2-T09 Acceptance Record — 2026-10-17

实现提交：`0ea97870ffab56d19d31b6675605d363a1cd4224`

代码变动：

```text
ProxyErrorType (new enum, stable HTTP/code/message mapping)
ModelInvocationException(errorType)
ProviderHttpClient status/timeout/network classification
ModelProxyService local configuration error classification
GlobalExceptionHandler proxy + invalid provider response handlers
```

Actual mappings:

| 场景 | Status | Code |
|---|---:|---|
| 无默认 Deployment | 409 | MODEL_DEPLOYMENT_NOT_CONFIGURED |
| 默认 Deployment disabled | 409 | MODEL_DEPLOYMENT_DISABLED |
| Provider 400 | 502 | PROVIDER_BAD_REQUEST |
| Provider 401/403 | 502 | PROVIDER_AUTH_FAILED |
| Provider 429 | 503 | PROVIDER_RATE_LIMITED |
| Provider 5xx / unknown error status | 502 | PROVIDER_UNAVAILABLE |
| Connection refused | 502 | PROVIDER_CONNECTION_ERROR |
| Read / connect timeout | 504 | PROVIDER_TIMEOUT |
| Invalid Provider success response | 502 | PROVIDER_INVALID_RESPONSE |

Code Review facts:

- Safe fixed messages and error codes; upstream body, URL and credentials not embedded in typed exception causes.
- Local configuration failures do not invoke Provider.
- Upstream HTTP 401/403 is not AIGate Runtime Authentication 401.
- Upstream 429 does not claim AIGate application quota exceeded.
- No retry/fallback.
- Existing real HTTP Happy Path preserved by tests.

Updated tests:

```text
RuntimeInvokeIntegrationTest
ProviderHttpClientHttpTest
ModelProxyServiceTest
```

Includes local HttpServer statuses, timeout, connection failure, API Key auth/validation, zero outbound on missing/disabled default Deployment and invalid success response.

**P2-T09 DONE / ACCEPTED; no blocking issue.** GitHub did not expose CI status / workflow run; actual full Maven suite was not executed by this review, so TD-009 remains.

Nonblocking improvements for P2-T10: better test resource cleanup and log-capture assertions; do not infer that a safe HTTP error response alone proves all logs are clean.

Current tech debt: TD-001~TD-011, including TD-011 endpoint URL/SSRF hardening for public production exposure.

## 11. P2-T10 End-to-End Test — ACTIVE

Business Goal:

> Systematically verify real Phase 2 Runtime chain across management configuration, API Key lifecycle, Security, MySQL, HTTP Provider and public success/error behavior.

Actions:

- Review existing test matrix and fill genuine gaps; don't duplicate tests already present.
- Verify ACTIVE/REVOKED/replacement Key behavior through /v1/invoke.
- Verify default Deployment switching/disabled/cleared binding affects next request.
- Preserve Provider secret and client API Key separation.
- Verify real HTTP status failures (400/401/403/429/5xx), invalid body, timeout, network failure, and no retry.
- Check management Basic vs runtime API Key separation and request validation 400.
- Tighten HttpServer/Executor lifecycle; prevent cross-test state leakage.
- Run actual Maven tests using MySQL Testcontainers and save reproducible run command/results.

No DB migrations, no new external Provider dependency, no Redis/MQ/Streaming/Retry.

Execution gate: **P2-T10 ACTIVE; P2-T11 NOT STARTED**. After passing acceptance, auto-start P2-T11 Phase Closeout; completion of P2-T11 still requires full Phase audit and documentation.

---

## 12. P2-T10 Acceptance Review — 2026-10-17

实现提交：`2943dce28313742acc608fa11aaf962e23aae3c0`。

静态代码/测试覆盖审查通过：

- API Key revoke 后 401、多个 ACTIVE Key 可用
- 切换 Application.defaultDeploymentId 后发往新的 Mock Provider context / model
- Application 解绑或 Deployment disabled 后 409 且零出站
- Provider 400/401/429/500、无效响应、连接异常等测试覆盖
- 新增完整 HTTP 504 timeout E2E（独立测试上下文/本地延迟 Provider）
- Handler 日志安全断言；Mock HttpServer Executor 显式 shutdown/awaitTermination

**最终验收 PENDING：** P2-T10 特别要求的真实 Maven 全量测试执行结果目前缺失。GitHub 当前没有 CI status/workflow run，也没有可核实的测试运行汇总；验收环境没有 Docker/Maven，不能独立运行 Testcontainers 测试。

待复验：在 Java 21 + Docker 可用环境运行 `./mvnw test`，提供退出状态与 Surefire `Tests run, Failures, Errors, Skipped` 汇总。出现失败需修复并复跑。

**P2-T10 的上述 PENDING 状态已于用户补交真实 Maven 汇总后解除；最终验收见下文第 13 节。TD-009 CI Test Gate 继续保留。**

---

## 13. P2-T10 Final Acceptance — 2026-10-17

当前实现基线 commit：`2943dce28313742acc608fa11aaf962e23aae3c0`。

用户提供本地 Maven 全量测试汇总：

```text
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

证据来源：**用户提供本地终端输出**，非 GitHub CI；未收到单独的命令退出码或 Surefire XML 文件。本次满足“真实执行有测试统计”的任务验收要求，但不应把该结果写成 CI 自动执行通过。

结合此前 E2E/Code Review：

- MockMvc / MySQL Testcontainers / JDK HttpServer 贯穿真实 Runtime 链路
- ACTIVE/REVOKED API Key、多个 Key 并存
- Application 默认部署切换、禁用及解绑
- Provider Credential 与客户端 Key 严格隔离
- 上游错误码/超时/坏响应、安全日志及无重试验证
- Test HttpServer / Executor 资源生命周期清理

**结论：P2-T10 — DONE / ACCEPTED；无新增阻塞缺陷。**

TD-009（没有 CI Test Gate）和 TD-011（Provider Endpoint SSRF 约束）仍开放。

## 14. P2-T11 Phase Closeout — ACTIVE

Business Goal: 正式审查并收尾 Phase 2 的真实业务闭环、代码、测试、架构、技术债、核心文档、知识点和面试表达。

主要交付：

1. 功能检查：P2-T01~T10 与业务目标比对，确认 API/表/链路可运行且完整
2. Phase 级 Code Review：职责、依赖、凭证隔离、错误映射、安全风险
3. 测试：记录本地 138 / 0 / 0 / 0 与 CI 缺口，识别应补的测试
4. 架构检查：坚持模块化单体，检查真实演进和未来风险
5. 技术债：TD-001~TD-011 分类、优先级、生产发布前门槛（尤其 TD-011）
6. 文档：更新/核对 project-overview、requirements、architecture、domain-model、module-design、roadmap、api-design、database-design、progress
7. ADR：必要时新增，避免无真实决策而形式化增加
8. knowledge-map：按真实 Phase 2 开发路线补齐知识地图
9. interview-notes：形成可讲清楚的业务闭环与设计取舍
10. Phase Closeout 结论、阻塞清单和下一阶段准备

**当前 P2-T11 ACTIVE，Phase 2 仍 ACTIVE；不把“138 个测试通过”直接等同于“Phase 2 CLOSED”。** 在 P2-T11 无阻塞且文档全部同步后，再按自动推进约定进入下一 Phase。


---

## P2-T11 文档核对记录（2026-10-18）

- **当前状态：P2-T11 ACTIVE；Phase 2 尚未 CLOSED；Phase 3 未启动。** P2-T01～T10 均 DONE / ACCEPTED。
- 核心闭环已实现：管理配置 → Application 默认部署 → Application API Key → Spring Security 认证 → Adapter/RestClient → Provider → 统一结果或错误。
- 用户提供的本地 Maven 汇总为 **138 tests / 0 failures / 0 errors / 0 skipped**；不是 CI 执行结果，TD-009 保留。
- 关键边界：HTTP Basic 管理面与 Bearer Runtime；API Key hash 与 Provider Credential AES-GCM；Adapter 协议转换与 ProviderHttpClient HTTP 传输分离。
- 生产化前重点：TD-001 管理面认证升级、TD-011 出站 URL/SSRF 防护；工程质量 TD-009 CI 和 TD-006 requestId/traceId 后续处理。其余 TD-002~TD-010 继续按真实需求决定，无需本阶段一次偿还。
- 文档核对已明确：修正 roadmap 对 Phase 2 的过期 PLANNED 说明、architecture 中 Runtime 尚未实现的过期说明；补写 Phase 2 开发知识与面试表达。未修改业务代码、数据库或阶段状态。
- **收尾门槛**：仍需总控综合验收 P2-T11，再决定 Phase 2 CLOSED 与 Phase 3 启动，不以文档提交自动替代验收。

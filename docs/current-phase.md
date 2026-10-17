# AIGate Current Phase

> 项目总控、开发导师、Code Review 的共享执行基线。实际代码和已验收记录为事实来源。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T10 — End-to-End Integration Test（ACTIVE / PENDING EXECUTION EVIDENCE）**

Next Planned Task: **P2-T11 — Phase Closeout（NOT STARTED）**

> P2-T09 已于 2026-10-17 通过代码及测试覆盖验收，按自动推进规则直接启动 P2-T10。当前聚焦整条 Runtime 链路的可重复 E2E 测试，不新增新的业务功能。

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | DONE |
| P2-T07 | Provider Adapter | DONE |
| P2-T08 | RestClient + Single Model Proxy | **DONE / ACCEPTED** |
| P2-T09 | Provider Error Mapping | **DONE / ACCEPTED** |
| P2-T10 | End-to-End Integration Test | **ACTIVE** |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T08 Acceptance — Actual Implementation

Reviewed code commits:

```text
5edbd468757e15df8492205eb3512cf8f8c112ef
6b1501ed681806642ff640637f60ad5ebf7fb596
```

Implemented production components:

```text
runtime.controller.RuntimeInvokeController
runtime.service.ModelProxyService
runtime.service.ModelInvocationException
runtime.client.ProviderHttpClient
config.RestClientConfig
```

Request main flow:

```text
Client POST /v1/invoke
→ Runtime API Key SecurityFilterChain
→ ApplicationIdentity
→ @Valid InvokeRequest
→ ModelProxyService
→ Application.defaultDeploymentId
→ ModelDeployment.enabled
→ Provider.type
→ ProviderAdapterRegistry
→ OpenAiCompatibleProviderAdapter.buildRequest
→ CredentialService.decrypt (only if encrypted credential exists)
→ RestClient HTTP POST {endpointUrl}/chat/completions
→ OpenAiCompatibleProviderAdapter.parseResponse
→ InvokeResponse
```

- Client API Key is never sent to upstream; only the separately stored Provider credential can be used as Authorization.
- `endpointUrl` is the configured Provider API base URL. One trailing slash is normalized; `/chat/completions` is appended.
- Null Provider Credential means no upstream Authorization header.
- Connection timeout defaults to 3 seconds; read timeout defaults to 60 seconds. They are configurable.
- No retries, fallback, circuit breakers, SSE, routing, Redis, MQ or new DB migration.

### P2-T08 Tests (Code Reviewed)

```text
RuntimeInvokeIntegrationTest
ProviderHttpClientHttpTest
ModelProxyServiceTest
```

Test code covers:

- True HTTP Happy Path via local JDK HttpServer Mock Provider and MySQL Testcontainers
- API Key 401 and Request Validation 400
- Correct upstream path / JSON / remoteModelName / Content-Type
- Provider Credential present/absent; client API Key not forwarded
- No default Deployment or disabled Deployment -> no outbound request
- Missing Application / Deployment / Provider / Unsupported Provider Type -> no outbound request
- Trailing slash URL handling
- Return of true provider content via Adapter (no hard-coded 200)

GitHub implementation head `6b1501e...` has no CI statuses or workflow runs. Therefore only code/test coverage has been reviewed; do not claim actual CI green. TD-009 retained.

**Acceptance result: P2-T08 COMPLETED / ACCEPTED.**

Non-blocking follow-up: local configuration failures currently surface generic 500, to be mapped as explicit state/configuration failures in P2-T09. Production endpoint URL trust/SSRF restrictions must be addressed before internet-facing deployment (TD-011).

---

## 4. P2-T09 Original Business Goal（历史冻结需求，现已完成）

> 当模型 Provider 出错、超时或返回坏数据时，AIGate 应准确向 Client 表示“本次调用失败了”，并区分错误大类，同时不暴露 Provider Credential、原始上游响应、内部 URL 或服务堆栈。

P2-T08 already has successful invocation; P2-T09 makes the **failure path predictable, useful and safe**.

First principle: no fake success, no silent fallback, no retry. Client should receive a meaningful AIGate error category; don't forward raw upstream error body to untrusted callers.

## 5. Target Failure Flow

```text
RestClient request / response
↓
ProviderHttpClient detects:
  - upstream HTTP status
  - timeout / network exception
↓
safe categorized proxy exception
↓
GlobalExceptionHandler
↓
ApiErrorResponse
→ HTTP status + stable error code + sanitized message
```

Also cover:

```text
OpenAiCompatibleProviderAdapter.parseResponse
→ InvalidProviderResponseException
→ mapped provider invalid-response error
```

And local precondition failures in ModelProxyService:

```text
Application default Deployment missing/disabled
→ explicit local configuration error
→ never issue outbound HTTP request
```

## 6. Error Classification Scope

At minimum distinguish:

| Failure source | Category / intended client meaning |
|---|---|
| No default deployment | Runtime configuration unavailable (not successful) |
| Disabled deployment | Runtime configuration disabled |
| Provider HTTP 400 | Upstream rejected request |
| Provider HTTP 401/403 | Provider authentication/authorization failed (not client's API Key) |
| Provider HTTP 429 | Upstream rate-limited (not AIGate quota) |
| Provider HTTP 5xx | Upstream provider failure |
| Network / connection failure | Provider connection failure |
| Read timeout | Provider timeout |
| Invalid JSON / missing choices or content | Provider invalid response |

Choose a **small explicit code→HTTP-status table** before implementation. Recommended guidance:

```text
MODEL_DEPLOYMENT_NOT_CONFIGURED → 409
MODEL_DEPLOYMENT_DISABLED       → 409
PROVIDER_BAD_REQUEST           → 502
PROVIDER_AUTH_FAILED           → 502
PROVIDER_RATE_LIMITED          → 503
PROVIDER_UNAVAILABLE           → 502
PROVIDER_CONNECTION_ERROR      → 502
PROVIDER_TIMEOUT               → 504
PROVIDER_INVALID_RESPONSE      → 502
```

These are proposed phase mappings, not currently implemented facts. It is acceptable to refine specific names/statuses during review provided they are consistent, documented and do not confuse Provider 401/429 with AIGate API Key auth or own quota errors.

Unclassified or unsupported upstream codes must also result in safe, non-2xx failure.

## 7. Data Exposure / Security Contract

Must never expose or log:

```text
Client Application API Key
Provider Credential plaintext or ciphertext
Authorization header
Provider raw error body
Request messages or private prompt content
Internal Provider URL if potentially sensitive
Provider stacktrace to Client
```

Prefer fixed error messages and stable codes. No `exception.getMessage()` from upstream HTTP exceptions in public response or logs. Avoid preserving raw response bodies in nested causes that are logged by `GlobalExceptionHandler`.

Provider 401/403 does **not** mean client needs a new Application API Key. Provider 429 does **not** imply an AIGate application quota was exceeded.

## 8. Minimal Architecture

Prefer to reuse:

```text
ProviderHttpClient
ModelProxyService
InvalidProviderResponseException
GlobalExceptionHandler
ApiErrorResponse
```

A small typed failure kind / exception and a focused mapping handler are sufficient. Avoid introducing a large universal error framework, resilience library, retry policy, circuit breaker, Redis or MQ.

Clear separation:

- ProviderHttpClient classifies outbound transport/status errors
- ProviderAdapter flags invalid successful response structure
- ModelProxyService gives explicit configuration failure
- GlobalExceptionHandler translates classified failure into public safe API response

## 9. Acceptance Criteria / Tests

- Provider HTTP 400/401/403/429/5xx and unknown statuses → deterministic safe errors, not generic success
- Connection refused and request/read timeout → deterministic categorized errors
- Invalid Provider JSON / empty choices / missing required fields → safe `PROVIDER_INVALID_RESPONSE`
- Missing/disabled default Deployment → explicit non-500 configuration error, zero outbound calls
- Client missing/invalid API Key continues to return existing `401 UNAUTHORIZED` (do not conflate with upstream 401)
- Correct Request Validation continues returning `400 VALIDATION_ERROR` / `400 INVALID_REQUEST`
- No exception/log/client response leaks secret, Provider raw body or internal URL
- No retry or fallback: each invocation makes at most one outbound attempt
- Provider HTTP 200 Happy Path still produces existing InvokeResponse
- Tests cover at least one real local HTTP Mock Provider failure plus fast unit tests for mappings
- No DB schema change, no new dependencies unless a real blocker emerges

## 10. Deferred / Technical Debt

Keep TD-001 ~ TD-010.

Add **TD-011 — Provider endpoint URL restrictions / SSRF hardening before internet-facing deployment**: currently an admin-configured endpointUrl is used to initiate server-side HTTP, so production should validate scheme / destination and restrict private-network/metadata access according to deployment policy. Do not break localhost Mock Provider tests in this stage; this is separate from error mapping.

Defer to later phases:

```text
Retry
Fallback
Circuit Breaker
Rate Limit / Quota
Routing
Streaming / SSE
Redis / MQ
Advanced Observability
```

## 11. Automatic Advancement

User confirmed 2026-10-17: on successful review with no blocking issues, finish Code Review / testing / architecture checks, update progress and architecture/API docs, record technical debt / ADR if necessary, and **start the next task immediately**. Failed acceptance stays ACTIVE until fixed. Phase-to-Phase advancement must complete Phase Closeout first.

**Current task: P2-T10 ACTIVE. P2-T11 NOT STARTED.**


---

## 12. P2-T09 Actual Acceptance — 2026-10-17

实现 commit：`0ea97870ffab56d19d31b6675605d363a1cd4224`。

已实现：

```text
ProxyErrorType
ModelInvocationException(ProxyErrorType)
ProviderHttpClient.classifyResponseStatus
ProviderHttpClient.classifyNetworkFailure
GlobalExceptionHandler typed error handling
ModelProxyService local precondition classification
InvalidProviderResponseException → PROVIDER_INVALID_RESPONSE
```

真实 HTTP 映射：

| 场景 | HTTP | Code |
|---|---:|---|
| 无默认部署 / 部署被禁用 | 409 | MODEL_DEPLOYMENT_NOT_CONFIGURED / MODEL_DEPLOYMENT_DISABLED |
| Provider 400 | 502 | PROVIDER_BAD_REQUEST |
| Provider 401/403 | 502 | PROVIDER_AUTH_FAILED |
| Provider 429 | 503 | PROVIDER_RATE_LIMITED |
| Provider 5xx / 其他非成功状态 | 502 | PROVIDER_UNAVAILABLE |
| 连接失败 | 502 | PROVIDER_CONNECTION_ERROR |
| 超时 | 504 | PROVIDER_TIMEOUT |
| 无效成功响应结构 | 502 | PROVIDER_INVALID_RESPONSE |

本地引用异常还包括 APPLICATION_NOT_FOUND(404)、MODEL_DEPLOYMENT_NOT_FOUND(404)、PROVIDER_NOT_FOUND(404)、PROVIDER_TYPE_UNSUPPORTED(501)。

测试实现覆盖了本地 Mock Provider 状态 401/429/500、connection refused、真实 read timeout、其他状态码分类、无效响应、零出站前置失败、成功回归、无重试及响应内容脱敏。

**验收结论：P2-T09 DONE / ACCEPTED。** 仅核对了代码和测试覆盖；GitHub 未返回 CI statuses/workflow runs，未独立执行 Maven，TD-009 继续保留。

未新增阻塞性缺陷。保留 TD-011：Provider endpointUrl 的 SSRF / 网络目标防护；完整日志捕获与测试资源清理在 P2-T10 中进一步验证。

---

## 13. P2-T10 — End-to-End Integration Test（ACTIVE）

### Business Goal

> 通过**可重复、可执行**的系统级测试，证明 Phase 2 的核心链路从管理配置、API Key 生命周期，到真实 Runtime HTTP 代理以及成功/失败响应都按设计工作，为 P2-T11 Phase Closeout 提供测试证据。

P2-T08/P2-T09 已有局部 E2E 和 HTTP 测试。本任务是**整合、补齐缺口并实际运行测试**，不是重新写一套重复测试框架。

### Scope / Main Flow

```text
MySQL Testcontainers + Flyway V1–V6
↓
HTTP Basic 管理 API 创建 Team / Application
↓
Provider / Model / ModelDeployment（配置 remoteModelName、endpoint、可选 Credential）
↓
Application.defaultDeploymentId 绑定
↓
创建 ACTIVE API Key（明文只返回一次）
↓
POST /v1/invoke + Bearer API Key
↓
Runtime SecurityFilterChain → ApplicationIdentity
↓
ModelProxyService / ProviderAdapter / RestClient
↓
JVM-local Mock Provider HTTP
↓
InvokeResponse 或安全、稳定的失败 ApiErrorResponse
```

### Architecture

复用：

- `IntegrationTestBase` / MySQL Testcontainers / MockMvc
- 本地 JDK `HttpServer`（随机端口、可编排响应状态/响应体/延迟）
- P2-T08/P2-T09 已有 `RuntimeInvokeIntegrationTest`、`ProviderHttpClientHttpTest`、`ModelProxyServiceTest`
- 真实业务 Service / Mapper / SecurityFilterChain / Flyway，**不要把核心链路 Mock 掉**
- 允许在独立单元测试中使用 Mockito 验证细节；E2E 不以 Mocked ModelProxyService 充当真实执行

不添加 WireMock、MQ、Redis、Docker Compose Test Environment 等新依赖，除非现有测试组件有实际阻碍。

### Tasks

1. 先盘点已有测试，形成场景矩阵；识别 P2-T08/P2-T09 未覆盖的关键分支。
2. 补齐 Application API Key 轮换/撤销后的 Runtime 行为：ACTIVE 可用，REVOKED 401，替换 Key 后仍可调用。
3. 验证更改 Application 默认 Deployment 后，下一次请求确实选择新 Deployment；禁用或解绑后不能出站。
4. 验证 Credential 不泄露、无 Credential 时无上游 Authorization，客户端 API Key 不转发。
5. 覆盖 Provider 400 / 401 / 403 / 429 / 5xx、网络连接故障、读取超时、非法 JSON/choices 的错误契约；不得返回 fake success。
6. 回归管理面 / Runtime 双向身份隔离、Request 400 校验与正常 InvokeResponse。
7. 检查 Mock HTTP Server、Executor、端口、共享状态的测试清理和隔离；避免并行/跨测试污染与悬挂非守护线程。
8. **实际执行**完整测试，保存 Maven 命令与结果证据；若环境无法运行 Docker/Testcontainers，要明确记录限制并在验收前安排可运行环境复测。
9. 对发现的产品代码缺陷做最小修复；不要借测试任务扩建业务能力。

### Acceptance Criteria

- 可通过 `./mvnw test`（或仓库可用的等价 Maven 命令）重复执行；含真实 MySQL Testcontainers + 本地 Mock Provider
- 管理配置 → API Key → Runtime 成功响应的完整闭环可验证
- API Key revoked 立即 401；替换后新 Key 可用
- 切换 Deployment 后调用新的 remoteModelName / endpoint；禁用/解绑后零出站
- HTTP 成功/错误状态映射满足 P2-T09 的公开契约
- 上游 response body / Provider secret / 客户端 API Key 不泄漏到对外错误和日志
- 每次 Invoke 最多一次上游调用，没有隐式 retry/fallback
- 测试隔离与本地 Mock Server 清理可靠；不依赖外部真实 LLM 或公网
- 有**真实测试执行证据**（测试数、失败/跳过数及命令），不能只根据新增测试代码宣称绿灯
- 无新增不必要基础设施或数据库 Migration

### Knowledge Points / Interview

本任务只需掌握与业务密切相关的三点：跨层 E2E 测试边界、Mock Provider 与 Mock Service 的区别、如何验证认证/路由（单默认部署）/外部 HTTP / 失败脱敏。面试表达在 P2-T11 Closeout 系统整理。

### Boundary

不提前增加：

```text
Routing / ModelAlias / Weighted Load Balancing
Rate Limit / Quota / Cost / Usage
Retry / Fallback / Circuit Breaker
SSE / WebFlux
Redis / MQ / Microservices
```

本任务不新增功能性 schema migration；Phase 2 的核心目标仍是最简单的可运行、可验证单模型代理。

### Execution Gate

**P2-T10 ACTIVE，P2-T11 NOT STARTED。** 本任务验收通过且无阻塞问题后，自动启动 P2-T11 Phase Closeout；P2-T11 必须完成 Phase-level 代码、测试、架构、技术债、文档、知识点与面试表达检查，不能跳过。

---

## 14. P2-T10 Acceptance Review — 2026-10-17（未通过最终执行门）

已审查实现提交：`2943dce28313742acc608fa11aaf962e23aae3c0`。

代码覆盖检查结果：

- `RuntimeInvokeIntegrationTest` 新增 API Key revoked → Runtime 401、多个 ACTIVE Key 正常调用、切换默认 Deployment 后选择新 endpoint/model、解绑/禁用后不出站，以及 Provider 400 与安全日志捕获断言
- `RuntimeInvokeTimeoutIntegrationTest` 用单独测试属性 connect/read timeout=1s、独立本地 Mock Provider 延迟 3s，验证 Runtime HTTP 504 PROVIDER_TIMEOUT 及一次出站
- `ProviderHttpClientHttpTest` 补充 ExecutorService shutdownNow/awaitTermination 与 Mock Server handler 安全结束
- 既有管理配置、真实 Testcontainers MySQL、RestClient、Security、异常映射主链测试继续保留

目前没有发现需要在这轮立即修改的阻塞性测试代码问题。

**未通过最终验收的原因：** 本任务 Acceptance Criteria 明确要求实际运行 Maven 全量测试并记录统计。最新提交无 GitHub CI statuses/workflow runs，也未提供 Maven Surefire 报告/控制台结果；验收环境无 Maven/Docker，无法独立启动 MySQL Testcontainers。不能凭测试源码推断测试全部运行成功。

复验要求：

```bash
./mvnw test
```

在有 Docker 可用的 Java 21 开发环境执行。提交 Maven 命令退出码、`Tests run / Failures / Errors / Skipped` 统计、必要时测试报告或失败日志（务必脱敏），并处理实际失败项。建议一并验证独立 timeout Spring Context 能启动、资源清理无挂起线程。若出现偶发失败，应连续复跑关键 E2E 测试确认可靠性。

**阶段状态：P2-T10 继续 ACTIVE，P2-T11 NOT STARTED。** 收到可核验的真实测试执行结果且无阻塞问题后，可正式验收 T10 并按自动推进规则启动 T11。
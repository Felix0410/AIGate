# AIGate Current Phase

> 项目总控、开发导师、Code Review 的共享执行基线。实际代码和已验收记录为事实来源。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T09 — Provider Error Mapping（ACTIVE）**

Next Planned Task: **P2-T10 — End-to-End Integration Test（NOT STARTED）**

> P2-T08 已完成验收，按 2026-10-17 自动推进规则直接启动 P2-T09。当前仅处理代理错误语义，不加入重试、熔断、缓存或路由。

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
| P2-T09 | Provider Error Mapping | **ACTIVE** |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
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

## 4. P2-T09 — Business Goal

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

**Current task: P2-T09 ACTIVE. P2-T10 NOT STARTED.**

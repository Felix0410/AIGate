# AIGate Progress

## 1. Current Status

**Phase 2 — Model Registry & Single Model Proxy：ACTIVE**

Current Task: **P2-T09 — Provider Error Mapping（ACTIVE）**

Next Planned: **P2-T10 — End-to-End Integration Test（NOT STARTED）**

P2-T08 已于 2026-10-17 验收通过，按用户新规则自动推进 P2-T09。

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
| P2-T09 | Provider Error Mapping | **ACTIVE** |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

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

**当前 P2-T08 DONE / ACCEPTED；P2-T09 ACTIVE；P2-T10 NOT STARTED。**


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

Acceptance: focused mapping unit tests plus real Mock Provider failure tests; current `P2-T09 ACTIVE`. Next `P2-T10 NOT STARTED`.

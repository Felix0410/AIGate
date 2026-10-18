# AIGate API Design

## 1. 当前基线

当前生产 API 内容以 **Phase 2 完成并于 2026-10-18 CLOSED 的代码基线** 为准；Phase 3 已启动 P3-T01（需求范围定义），尚未批准或实现新增 API。

认证边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

P2-T06 定义统一 Runtime 模型契约，P2-T07 实现 Provider Adapter；P2-T08 已实际接通生产 `POST /v1/invoke` 同步非流式模型代理主链并完成验收。

---

## 2. Runtime Authentication

```http
Authorization: Bearer <AIGATE_API_KEY>
```

认证成功后：

```text
ApplicationIdentity
- applicationId
- applicationName
- teamId
```

Client 不需要、也不应声明 applicationId。

---

## 3. Unified Runtime API Contract

统一设计路径：

```text
POST /v1/invoke
```

当前状态：

```text
Request / Response Contract
→ IMPLEMENTED

Production Controller / execution
→ IMPLEMENTED / ACCEPTED (P2-T08)
```

P2-T06 当时未制造 fake model success；P2-T08 已通过真实本地 HTTP Mock Provider 响应验证真实模型代理 Happy Path。

---

## 4. InvokeRequest

```json
{
  "messages": [
    {
      "role": "system",
      "content": "You are a coding assistant."
    },
    {
      "role": "user",
      "content": "Explain Java HashMap."
    }
  ],
  "temperature": 0.7,
  "maxTokens": 500
}
```

字段：

```text
messages      required
temperature   optional
maxTokens     optional
```

Validation：

```text
messages → non-null / non-empty
temperature → 0.0..2.0 when present
maxTokens → > 0 when present
```

Request 不提供：

```text
applicationId
providerId
modelId
deploymentId
```

---

## 5. InvokeMessage

字段：

```text
role
content
```

role 仅支持：

```text
system
user
assistant
```

content：non-blank。

未知 role 无法被反序列化为 MessageRole。

当前不支持：

```text
tool
function
developer
multimodal
image
audio
JSON schema
```

---

## 6. InvokeResponse

```json
{
  "content": "HashMap is...",
  "model": "some-model",
  "finishReason": "stop"
}
```

字段固定为：

```text
content
model
finishReason
```

当前不包含：

```text
usage
cost
requestId
providerResponse
deploymentId
routingMetadata
```

---

## 7. Client / Provider Protocol Boundary

```text
InvokeRequest
→ ProviderAdapter
→ Provider protocol

Provider response
→ ProviderAdapter
→ InvokeResponse
```

不把 OpenAI SDK DTO 直接暴露给 Runtime Client。

---

## 8. Authentication Failure

```text
missing Authorization
non-Bearer
malformed API Key
unknown keyId
revoked Key
wrong secret
missing Application
→ 401 UNAUTHORIZED
```

---

## 9. Validation Error Contract

当未来 `POST /v1/invoke` Controller 接入 `@Valid` 后：

```text
Bean Validation
→ 400 VALIDATION_ERROR

非法 enum / JSON
→ 400 INVALID_REQUEST
```

P2-T08 已通过 MockMvc 集成测试验证 Invoke 的 Validation 400 及无 API Key 的 401；正常请求可通过真实 HTTP Mock Provider 返回 InvokeResponse。

---

## 10. Existing Management API

```text
/api/teams
/api/employees
/api/applications
/api/providers
/api/models
/api/model-deployments
/api/applications/{applicationId}/api-keys
```

均继续使用 HTTP Basic。

---

## 11. Next API Work

P2-T07 **已完成并验收**：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
OpenAI-Compatible provider protocol conversion
```

P2-T08 **DONE / ACCEPTED**：

```text
ApplicationIdentity
→ Application.defaultDeploymentId
→ ModelDeployment
→ CredentialService
→ ProviderAdapter
→ RestClient
→ Provider
→ InvokeResponse
```

P2-T08 已接通生产 `POST /v1/invoke`；P2-T09 正在补齐 Provider 失败映射。

---

## 12. API Design Principles

- Runtime 身份来自 Application API Key
- Client 不传内部 Application / Deployment selector
- AIGate Contract 与 Provider Protocol 分离
- 不暴露 Provider SDK DTO
- DTO 只包含当前主流程需要字段
- 不为未实现的 Provider 调用伪造成功响应
- 后续通过 Provider Adapter 承担协议转换


---

## 13. P2-T07 实际 Provider Protocol Mapping

Provider Adapter 已实现：

```text
InvokeRequest.messages
→ OpenAI-compatible messages

InvokeRequest.temperature
→ temperature（null 时省略）

InvokeRequest.maxTokens
→ max_tokens（null 时省略）

ModelDeployment.remoteModelName
→ model
```

Provider 成功响应：

```text
choices[0].message.content
→ InvokeResponse.content

response.model
→ InvokeResponse.model

choices[0].finish_reason
→ InvokeResponse.finishReason
```

已支持忽略上游额外字段，且会拒绝无效 JSON / 空 choices / 缺失关键内容。P2-T07 不发送实际 HTTP 请求。

## 14. P2-T08 正式 Endpoint（已实现 / 代码验收）

```http
POST /v1/invoke
Authorization: Bearer <APPLICATION_API_KEY>
Content-Type: application/json
```

最小 Request：

```json
{
  "messages": [
    {"role": "user", "content": "Explain HashMap"}
  ],
  "temperature": 0.7,
  "maxTokens": 200
}
```

成功 Response：

```json
{
  "content": "HashMap is...",
  "model": "remote-model-name",
  "finishReason": "stop"
}
```

请求不携带 applicationId / deploymentId / providerId / modelId。身份来自 Runtime SecurityContext；模型部署来自 Application.defaultDeploymentId。

### Upstream 约定

`ModelDeployment.endpointUrl` 是 Provider API base URL，比如 `https://provider.example.com/v1`。

上游 POST：

```text
{endpointUrl 去尾斜杠}/chat/completions
```

- 请求 Body 由 ProviderAdapter 生成
- Provider Credential 有值时解密后使用 `Authorization: Bearer <provider-secret>`
- Provider Credential 为 NULL 时不附加 Authorization
- 客户端 Application API Key **绝不转发**给上游
- 设置有界连接超时和读取超时，默认不重试

### Phase Scope

- P2-T08：真实 HTTP 200 Happy Path、默认 Deployment 检查、Runtime Controller 接线与基本测试（已实现/验收）
- P2-T09：Provider 4xx/429/5xx、timeout、无效响应等标准 HTTP 错误语义（ACTIVE）
- P2-T10：完整 MySQL Testcontainers + Mock Provider E2E 测试矩阵

P2-T08 的实现与测试代码已审查；GitHub 无 CI status/workflow run，不声称 CI 绿灯。P2-T09/P2-T10 仍待实现验收。


---

## 15. P2-T08 Actual Invoke API Status

Production endpoint:

```http
POST /v1/invoke
Authorization: Bearer <AIGATE_APPLICATION_API_KEY>
Content-Type: application/json
```

Controller uses `@Valid @RequestBody InvokeRequest` and `ApplicationIdentity` from Spring Security, **not** applicationId supplied by Client.

Happy Path actual behavior:

```text
ApplicationIdentity.applicationId
→ Application.defaultDeploymentId
→ ModelDeployment (enabled)
→ Provider.type → Adapter
→ CredentialService.decrypt (if present)
→ RestClient POST {endpointUrl}/chat/completions
→ InvokeResponse(content, model, finishReason)
```

Configured `endpointUrl` is an API base URL. Provider Credential becomes a separate upstream Bearer token. If null, no Provider Authorization header. Client Application API Key is never forwarded upstream.

RestClient connect timeout default 3 seconds; read timeout default 60 seconds, configurable. No auto retries.

Actual test classes:

```text
RuntimeInvokeIntegrationTest
ProviderHttpClientHttpTest
ModelProxyServiceTest
```

Verified code paths: successful local HTTP mock response, credentials separation, no default Deployment / disabled Deployment rejection before outbound, API Key 401, bad request 400, JSON conversion. No GitHub CI run/status evidence for actual test execution.

## 16. P2-T09 Provider Failure Contract — DONE / ACCEPTED

**已解决**：P2-T09 使用 `ProxyErrorType`、`ModelInvocationException`、`GlobalExceptionHandler` 将本地配置和上游失败转换为安全且固定的错误响应。

已实现并验收的 public error mapping：

| Problem | Actual AIGate HTTP / code |
|---|---|
| Missing default Deployment | 409 MODEL_DEPLOYMENT_NOT_CONFIGURED |
| Disabled default Deployment | 409 MODEL_DEPLOYMENT_DISABLED |
| Upstream 400 | 502 PROVIDER_BAD_REQUEST |
| Upstream 401 or 403 | 502 PROVIDER_AUTH_FAILED |
| Upstream 429 | 503 PROVIDER_RATE_LIMITED |
| Upstream 5xx | 502 PROVIDER_UNAVAILABLE |
| Upstream connection error | 502 PROVIDER_CONNECTION_ERROR |
| Provider response timeout | 504 PROVIDER_TIMEOUT |
| Invalid successful Provider JSON | 502 PROVIDER_INVALID_RESPONSE |

这些是 **P2-T09 已实现的行为**。Provider HTTP 401/403 不等于 AIGate API Key 401；Provider 429 不等于 AIGate 自身限额。额外实现：无匹配 ProviderAdapter → 501 PROVIDER_TYPE_UNSUPPORTED，引用资源缺失 → 404 对应错误码；其他未明确分类的上游 HTTP 错误 → 502 PROVIDER_UNAVAILABLE。

Error body continues to use `ApiErrorResponse` with a stable code, safe message and timestamp; never expose upstream raw response, credentials, internal endpoint URL, or stacktrace. No retry/fallback in this phase.

## 17. Production Security Follow-up

**TD-011 Provider Endpoint URL / SSRF Hardening**: admin-configured endpointUrl is used for server-side outbound calls. Before internet-facing production or untrusted management tenancy, restrict schemes / destinations and internal/metadata network access according to policy; keep localhost test fixtures usable.

P2-T09 已完成 failure mapping，不是完整网络访问策略。P2-T10 正在开展系统级回归与完整 E2E 测试。


---

## 18. P2-T09 Error Contract Acceptance

Source commit: `0ea97870ffab56d19d31b6675605d363a1cd4224`.

`POST /v1/invoke` 的错误响应统一沿用：

```json
{
  "code": "PROVIDER_AUTH_FAILED",
  "message": "Upstream provider authentication failed",
  "timestamp": "...",
  "errors": null
}
```

Status 为 502。上游返回 401 时，AIGate 不会返回客户端 Authentication 401；该认证语义属于 Provider 与 AIGate 之间，而不是 Client 与 AIGate 之间。

成功响应仍为：

```json
{
  "content": "HashMap is...",
  "model": "remote-model",
  "finishReason": "stop"
}
```

`ProxyErrorType` 是对外错误码/状态/固定安全文案的单一映射点。错误处理不将 Provider 原始错误体、Authorization、Provider Credential、内部 endpointUrl 或异常堆栈写入 Runtime HTTP 响应；已分类的异常日志只记录 code，不记录原始异常链。

实际代码与测试覆盖验收通过；最新 GitHub 提交无 CI statuses/workflow runs，本次不宣称自动测试已运行成功。TD-009 保留。

## 19. P2-T10 Active Testing Contract

现在对 P2-T10 冻结以下跨模块测试语义：

- 先通过 Management HTTP Basic 创建 Team / Provider / Model / Deployment / Application，并签发 API Key
- 使用真正 API Key 调用 Runtime `POST /v1/invoke`；验证 SecurityContext 中 Application 身份与默认 Deployment 的解析
- ACTIVE Key 成功，REVOKED Key 401，手动替换 Key 后正常
- Application 切换默认 Deployment 后，后续请求应命中新 Deployment
- Credential 为 null 时上游无 Authorization，提供密文时使用独立 Provider Bearer，永不转发 Application API Key
- Provider HTTP 400/401/403/429/5xx、网络/超时、无效响应保持第 16 节已实现的 HTTP/code 契约
- 无默认 Deployment/disabled 时 409 且零出站，Request 错误 400
- Mock HTTP Server 随测试可靠清理，没有错误重试，没有硬编码成功
- 真正执行 Maven 测试并保留命令、测试数、失败与跳过信息

P2-T10 不改变公开 API Contract、不新增 Migration。P2-T11 才进行完整 Phase 2 Closeout。


---

## Phase 2 Closeout — 当前 API 契约摘要（2026-10-18）

- 管理面 `/api/**` 当前使用 HTTP Basic；Runtime `/v1/**` 使用 Application API Key Bearer，身份由服务端认证生成，客户端不传 Application/Deployment/Provider selector。
- `POST /v1/invoke` 已正式实现：`InvokeRequest(messages, temperature?, maxTokens?)` → `InvokeResponse(content, model, finishReason)`。仅同步非流式。
- Runtime 默认部署来自 `Application.defaultDeploymentId`，必须 enabled；支持 OpenAI-Compatible，endpointUrl 解释为 API base URL 并拼接 `/chat/completions`。
- 入站认证失败 401；无默认部署/禁用 409；Provider HTTP 400/401/403/5xx 等映射到 502；Provider 429 映射 503；超时 504；上游成功响应结构非法 502。详细稳定错误码与消息以本文件错误契约章节及 `ProxyErrorType` 为准。
- Application API Key 和 Provider Credential 严格隔离；不透传客户端 Key，不返回 Provider 原始错误 Body。
- API 暂不包含 streaming/SSE、用量、计费、别名路由、配额或自动重试。文中较早的“待实现”描述仅代表历史任务状态，不代表当前运行情况。


---

## Phase 2 Closed / Phase 3 API Baseline（2026-10-18）

Phase 2 的现有公开接口仍为管理面 `/api/**` 以及同步 Runtime `POST /v1/invoke`。请求、响应与错误契约详见本文件各节；历史“P2-T09 ACTIVE / P2-T10 NEXT”的描述已经过期，仅作阶段演进记录。

Phase 3 当前只开启 P3-T01 业务目标定义，**尚无新的 Invoke Record / Usage / Quota / Routing API 被批准或实现**。只有 P3-T01 确定业务问题和最小流程后，才会在本文件注明新增接口的计划与实现状态。

---

## Phase 3 — 调用历史管理 API（P3-T04 **IMPLEMENTED** / P3-T05 **未实现**）

### 20.1 已实现：Management Invocation History Query（P3-T04 DONE）

```http
GET /api/applications/{applicationId}/invocations?limit=20
Authorization: Basic <management-credentials>
```

- **认证**：管理面现有 HTTP Basic，Runtime Application API Key 不可访问此管理接口。
- **limit**：可选 query param，默认 20，合法范围 1～100（含两端）。非法值返回 400 `INVALID_REQUEST`，消息固定为 `"Invalid invocation query limit"`。
- **排序**：按 `id DESC`，最新记录在前。
- **无记录**：返回空数组 `[]`，HTTP 200。
- **不存在的 Application**：返回 404 `APPLICATION_NOT_FOUND`，复用现有 `ApplicationService.getApplicationById`。
- **成功响应字段**：每项仅含 `id, applicationId, status, errorCode, durationMs, createdAt` 六个字段。`errorCode` 即使为 null 也作为字段输出。
- **数据隔离**：只返回指定 Application 的记录，不跨 Application 混入。
- **不提供**：分页对象/游标/总数/聚合/导出/全文搜索/跨 Application 查询。

### 20.2 异常响应速查

| 场景 | HTTP | code | message |
|---|---|---|---|
| limit=0/-1/101/abc/空/1.5 | 400 | INVALID_REQUEST | Invalid invocation query limit |
| Application 不存在 | 404 | APPLICATION_NOT_FOUND | Application not found |
| 未认证 | 401 | 沿用现有 Security 契约 | |

### 20.3 与 P3-T05 的边界

- P3-T04 当前只返回 `invocation_record` 表中已存在的记录。
- **FAILED 记录的自动写入尚未实现**（P3-T05 范围）。
- 若表中已有 FAILED 记录（如手动插入），查询 API 可正常返回其字段。
- `POST /v1/invoke` 现有 Request/Response **不变**；客户端不知道也无需知道调用记录写入。
- 本数据是尽力保留的排障历史，不表示官方计费用量。

相关冻结设计见 `docs/current-phase.md` 和 `docs/adr/ADR-003-invocation-recording-persistence.md`。
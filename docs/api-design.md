# AIGate API Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T07 验收完成、P2-T08 启动后的实现与冻结计划** 为准。

认证边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

P2-T06 已定义统一 Runtime 模型契约，P2-T07 已实现 Provider Adapter 纯协议转换；生产 `POST /v1/invoke` handler 尚未接通，P2-T08 正在建设。

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
→ NOT WIRED YET
```

这是有意的阶段边界：P2-T06 不制造 fake model success。

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

P2-T06 当前通过 Contract Test 验证 DTO 层语义；HTTP handler 尚未落地，因此暂不声称 Invoke 错误路径已经通过 MockMvc 验证。P2-T08 需要接入 Controller 并验证实际 HTTP 校验行为。

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

P2-T08 **ACTIVE**：

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

P2-T08 需要真正接通生产 `POST /v1/invoke`。

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

## 14. P2-T08 正式 Endpoint（正在实现，尚非已上线事实）

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

- P2-T08：真实 HTTP 200 Happy Path、默认 Deployment 检查、Runtime Controller 接线与基本测试
- P2-T09：Provider 4xx/429/5xx、timeout、无效响应等标准 HTTP 错误语义
- P2-T10：完整 MySQL Testcontainers + Mock Provider E2E 测试矩阵

这些计划在对应任务实现并验收前不得写成“已通过”的事实。

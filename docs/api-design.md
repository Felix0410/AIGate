# AIGate API Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T06 完成后的实现** 为准。

认证边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

P2-T06 已定义统一 Runtime 模型契约，但生产 `POST /v1/invoke` handler 尚未接通。

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

P2-T06 当前通过 Contract Test 验证 DTO 层语义；HTTP handler 尚未落地，因此暂不声称 Invoke 错误路径已经通过 MockMvc 验证。

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

P2-T07：

```text
ProviderAdapter
OpenAI-Compatible provider protocol conversion
```

P2-T08：

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

届时再真正接通生产 `POST /v1/invoke`。

---

## 12. API Design Principles

- Runtime 身份来自 Application API Key
- Client 不传内部 Application / Deployment selector
- AIGate Contract 与 Provider Protocol 分离
- 不暴露 Provider SDK DTO
- DTO 只包含当前主流程需要字段
- 不为未实现的 Provider 调用伪造成功响应
- 后续通过 Provider Adapter 承担协议转换

# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T06 — Unified Model Contract（ACTIVE）**

Next Planned Task: **P2-T07 — Provider Adapter（NOT STARTED）**

> 用户已确认启动 P2-T06。当前只定义 AIGate Runtime 的统一请求/响应契约，不提前实现 Provider Adapter、RestClient 或真实模型调用。

---

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | **ACTIVE** |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. Business Goal

P2-T05 已经能够回答：

```text
“是谁在调用 Runtime？”
→ ApplicationIdentity
```

P2-T06 要回答：

```text
“这个 Application 可以向 AIGate 提交什么模型请求？”
“未来 AIGate 对 Client 返回什么统一结果？”
```

目标是建立一个很小、稳定、AIGate 自己控制的模型调用契约。

---

## 4. Client Contract Positioning

Phase 2 不承诺完整 OpenAI-Compatible Client API。

当前采用：

```text
Client
→ AIGate-native Invoke Contract

AIGate
→ Provider Adapter
→ OpenAI-Compatible Provider Protocol
```

原因：

- 当前只需要打通最小模型调用主流程
- 不让客户端提前绑定 Provider 协议
- 不提前加入 model selector / routing 语义
- 后续 Provider Adapter 可以独立处理上游格式差异

---

## 5. Frozen Runtime Path

统一 Runtime 路径冻结为：

```text
POST /v1/invoke
```

认证继续使用 P2-T05：

```http
Authorization: Bearer <AIGATE_API_KEY>
```

Runtime Client 不传：

```text
applicationId
providerId
modelId
deploymentId
```

Application 身份来自 SecurityContext；模型目标未来来自 `Application.defaultDeploymentId`。

---

## 6. InvokeRequest

最小字段：

```text
messages
temperature?
maxTokens?
```

示例：

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

### Validation

至少要求：

```text
messages
→ non-null
→ non-empty

each message
→ @Valid

temperature
→ optional
→ if present: 0.0 <= value <= 2.0

maxTokens
→ optional
→ if present: > 0
```

不在本阶段加入复杂 token/model-specific validation。

---

## 7. InvokeMessage

字段：

```text
role
content
```

role 只允许：

```text
system
user
assistant
```

建议使用 Java enum 表达封闭集合，例如：

```text
MessageRole.SYSTEM
MessageRole.USER
MessageRole.ASSISTANT
```

JSON 对外建议保持小写值。

content：

```text
@NotBlank
```

本阶段不支持：

```text
tool
function
image
audio
multimodal parts
JSON schema
developer role
```

---

## 8. InvokeResponse

统一响应最小字段：

```text
content
model
finishReason
```

示例：

```json
{
  "content": "HashMap is...",
  "model": "Mock Chat Model",
  "finishReason": "stop"
}
```

当前不包含：

```text
usage
cost
requestId
attempts
provider raw response
routing metadata
```

Usage / Cost / Ledger 属于后续阶段。

---

## 9. Important Boundary: Contract != Provider Protocol

P2-T06 的 DTO 是 AIGate 内部统一模型契约。

不要直接复用：

```text
OpenAI SDK DTO
OpenAI provider JSON classes
第三方 Provider SDK request/response
```

未来 P2-T07：

```text
InvokeRequest
↓
OpenAiCompatibleProviderAdapter
↓
Provider-specific request

Provider-specific response
↓
Adapter
↓
InvokeResponse
```

这样 Client Contract 与 Provider Contract 解耦。

---

## 10. ApplicationIdentity Usage

P2-T06 如果增加 Runtime Controller，身份只能从 Spring Security 获取：

```text
Authentication
↓
ApplicationIdentity principal
```

不要：

```text
request.applicationId
header X-Application-Id
query applicationId
```

因为调用者是谁已经由 API Key 认证决定。

---

## 11. No Fake Model Success

P2-T06 不应该为了“让 Controller 返回 200”而制造假模型响应。

禁止：

```text
return "hello"
echo request content
hard-coded model result
fake Provider success
```

这会把临时 Stub 混成真实业务语义。

因此本任务的核心交付是：

```text
InvokeRequest
InvokeMessage
MessageRole
InvokeResponse
validation
contract tests
POST /v1/invoke 的正式 API 契约
```

生产成功执行链在 P2-T08 `ModelProxyService` 可用后接通。

如果实现 Controller 边界，不得伪造成功响应；可以只完成可编译的契约层并将真正 handler wiring 留到 P2-T08。

---

## 12. Package / Responsibility Guidance

建议保持简单：

```text
runtime/
  dto/
    InvokeRequest
    InvokeMessage
    InvokeResponse
    MessageRole
```

是否增加 Controller，根据实现是否能在不制造假业务行为的前提下保持应用可启动来决定。

不要创建：

```text
巨大 domain hierarchy
Prompt aggregate
Conversation entity
Request persistence table
Command Bus
CQRS
```

---

## 13. Testing Focus

P2-T06 至少验证 DTO / JSON / Validation：

- 合法 messages 可反序列化
- system / user / assistant role 可用
- 非法 role → 400 INVALID_REQUEST 或统一 JSON parse error
- messages 缺失 / 空 → 400 VALIDATION_ERROR
- message.content blank → 400 VALIDATION_ERROR
- temperature 合法边界
- temperature 越界 → 400 VALIDATION_ERROR
- maxTokens > 0
- maxTokens <= 0 → 400 VALIDATION_ERROR
- request 中不存在 applicationId/providerId/modelId/deploymentId 业务入口
- InvokeResponse 只定义 content/model/finishReason
- 不提前依赖 Provider-specific DTO

如果生产 `POST /v1/invoke` Controller 在本任务落地，则还应验证：

- 无 API Key → 401
- ACTIVE API Key 可到达 Controller 边界
- Controller 获取到正确 ApplicationIdentity
- 不产生伪造模型成功结果

---

## 14. Existing Runtime Identity

P2-T05 已完成：

```text
/v1/**
→ Application API Key
→ ApplicationIdentity
→ SecurityContext
```

ApplicationIdentity：

```text
applicationId
applicationName
teamId
```

P2-T06 不重复实现认证逻辑。

---

## 15. Explicitly Deferred

P2-T06 不实现：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
RestClient
真实 outbound HTTP
Credential decrypt for invocation
ModelProxyService
Provider error mapping
Retry / Fallback / Circuit Breaker
Streaming / SSE
Usage / Cost
Routing
Redis
MQ
```

---

## 16. Acceptance Gate

当前只执行：

**P2-T06 — Unified Model Contract**

完成实现、测试、开发导师 Code Review 后回总控验收。

**不得自动进入 P2-T07。**

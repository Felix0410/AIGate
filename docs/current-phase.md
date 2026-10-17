# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T06 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T07 — Provider Adapter（NOT STARTED）**

> P2-T06 已通过总控验收。在用户明确确认前，不得自动开始 P2-T07。

---

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | **DONE / ACCEPTED** |
| P2-T07 | Provider Adapter | **NOT STARTED** |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T06 — Unified Model Contract

业务目标：

> 定义 AIGate 自己控制的最小模型调用请求/响应契约，为后续 Provider Adapter 和 Single Model Proxy 提供稳定边界。

当前已落地：

```text
runtime.dto
├── InvokeRequest
├── InvokeMessage
├── MessageRole
└── InvokeResponse
```

本任务没有实现 Provider Adapter、RestClient、ModelProxyService 或真实模型调用。

---

## 4. Client Contract Positioning

Phase 2 当前采用：

```text
Client
→ AIGate-native Invoke Contract

AIGate
→ Provider Adapter（P2-T07）
→ Provider Protocol
```

Client Contract 与 Provider Contract 不共用 DTO。

没有引入 OpenAI SDK DTO 作为 AIGate 对外 API。

---

## 5. InvokeRequest

当前字段：

```text
messages
temperature?
maxTokens?
```

Java：

```text
InvokeRequest
├── List<InvokeMessage> messages
├── Double temperature
└── Integer maxTokens
```

Validation：

```text
messages
→ @NotEmpty
→ @Valid

temperature
→ optional
→ 0.0 <= value <= 2.0

maxTokens
→ optional
→ > 0
```

Request 明确不包含：

```text
applicationId
providerId
modelId
deploymentId
```

Application 身份继续来自 P2-T05 的 SecurityContext。

---

## 6. InvokeMessage / MessageRole

InvokeMessage：

```text
role
content
```

Validation：

```text
role → @NotNull
content → @NotBlank
```

MessageRole 只包含：

```text
system
user
assistant
```

Java 内部使用：

```text
SYSTEM
USER
ASSISTANT
```

JSON 使用小写值。

当前不支持：

```text
tool
function
developer
image
audio
multimodal parts
JSON schema
```

---

## 7. InvokeResponse

当前最小字段：

```text
content
model
finishReason
```

明确不包含：

```text
usage
cost
requestId
providerResponse
deploymentId
routingMetadata
```

Usage / Cost / Ledger 属于后续阶段。

---

## 8. Runtime Endpoint Status

统一 Runtime 路径设计仍是：

```text
POST /v1/invoke
```

但 P2-T06 **没有创建生产 Controller**。

原因：

> 当前还没有 ModelProxyService / ProviderAdapter / outbound HTTP。为了避免硬编码或 echo 一个假模型结果，P2-T06 只落地正式 DTO 契约、序列化和 Validation。

因此当前真实状态：

```text
/v1/** Runtime Authentication
→ 已实现

Invoke DTO Contract
→ 已实现

POST /v1/invoke production handler
→ 尚未接通
```

真实成功链将在 P2-T08 代理能力具备后接通。

---

## 9. P2-T06 Test Coverage

InvokeContractTest 已覆盖：

- 合法 Request 反序列化
- system / user / assistant 三种 role
- role 序列化为小写
- unknown role 被 Jackson 拒绝
- messages 缺失 / 空列表校验失败
- role 缺失嵌套校验失败
- content null / 空串 / 空白校验失败
- temperature null / 0.0 / 0.7 / 2.0 合法
- temperature -0.1 / 2.1 非法
- maxTokens null / 1 / 500 合法
- maxTokens 0 / -1 非法
- InvokeRequest 不包含内部资源 ID 字段
- InvokeResponse 只包含 content / model / finishReason

测试只使用 Jackson 3 + Jakarta Validation，不依赖 HTTP / MySQL / Provider。

GitHub 最新 commit 仍无 CI status / workflow run；TD-009 继续保留。

---

## 10. Existing Runtime Identity

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

P2-T06 没有重复实现认证逻辑。

---

## 11. Next Planned Task

**P2-T07 — Provider Adapter**

下一步才开始解决：

```text
AIGate InvokeRequest
↓
ProviderAdapter
↓
Provider-specific Request

Provider-specific Response
↓
ProviderAdapter
↓
AIGate InvokeResponse
```

P2-T07 仍不负责完整 outbound proxy orchestration；真正 HTTP 调用主链属于 P2-T08。

---

## 12. Explicitly Deferred

```text
RestClient Provider 调用
ModelProxyService
真实 outbound HTTP
Provider Error Mapping
Retry / Fallback / Circuit Breaker
Streaming / SSE
Usage / Cost
Routing
Redis
MQ
```

---

## 13. Acceptance Gate

P2-T06 已完成并验收。

**当前等待用户确认是否开始 P2-T07。**

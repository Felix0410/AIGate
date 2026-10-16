# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前任务：

**P2-T06 — Unified Model Contract（ACTIVE）**

下一计划任务：

**P2-T07 — Provider Adapter（NOT STARTED）**

---

## 2. Phase 2 Task Status

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

## 3. 当前已有 Runtime 基础

```text
Application API Key
↓
/v1/** Runtime Authentication
↓
ApplicationIdentity
↓
SecurityContext
```

Application 当前也已经拥有：

```text
defaultDeploymentId
```

但还没有统一模型请求协议和真实 Provider 调用。

---

## 4. P2-T06 Goal

目标：

> 定义 AIGate 自己控制的最小模型调用请求/响应契约，为后续 Provider Adapter 和 Single Model Proxy 提供稳定边界。

正式 Runtime 路径冻结：

```text
POST /v1/invoke
```

Client 不传 applicationId/providerId/modelId/deploymentId。

---

## 5. Planned Contract

### Request

```text
InvokeRequest
- messages
- temperature?
- maxTokens?
```

### Message

```text
InvokeMessage
- role
- content
```

Role：

```text
system
user
assistant
```

### Response

```text
InvokeResponse
- content
- model
- finishReason
```

---

## 6. Validation

计划最小校验：

```text
messages → non-null / non-empty
message.content → non-blank
role → closed enum
temperature → optional, 0.0..2.0
maxTokens → optional, > 0
```

不加入 provider-specific validation。

---

## 7. Contract Boundary

Client contract：

```text
AIGate InvokeRequest / InvokeResponse
```

Provider protocol：

```text
P2-T07 ProviderAdapter
```

两者不直接共用 DTO。

不要引入：

```text
OpenAI SDK DTO as public API
provider raw JSON as runtime API
model/deployment selector in client request
```

---

## 8. No Fake Success

P2-T06 不为凑 200 响应伪造模型结果。

真实成功链：

```text
POST /v1/invoke
→ ModelProxyService
→ ProviderAdapter
→ RestClient
→ Provider
```

要到 P2-T08 才打通。

当前先把契约、序列化和校验做正确。

---

## 9. Acceptance Focus

至少验证：

- InvokeRequest / InvokeMessage / InvokeResponse 契约
- role 只有 system/user/assistant
- messages 必填且非空
- content 非空白
- temperature 边界
- maxTokens 正数
- JSON 非法 role 有明确 400
- Runtime request 不包含内部 application/provider/model/deployment selector
- DTO 不依赖 Provider SDK
- 不提前实现 ProviderAdapter / RestClient / ModelProxyService
- 不伪造模型成功结果

---

## 10. Current Technical Debt

继续保留 TD-001 ~ TD-010。

P2-T06 不新增持久化表，不新增 Flyway migration。

---

## 11. Execution Gate

当前只执行：

**P2-T06 — Unified Model Contract**

完成实现、测试、开发导师 Code Review 后回总控验收。

**不得自动进入 P2-T07。**

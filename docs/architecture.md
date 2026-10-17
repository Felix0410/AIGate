# AIGate Architecture

## 1. 当前阶段

**Phase 2 — Model Registry & Single Model Proxy**

当前执行状态：

```text
P2-T01 Model Registry Schema
→ COMPLETED
P2-T02 Provider Credential Protection
→ COMPLETED / ACCEPTED
P2-T03 Application Default Deployment
→ COMPLETED / ACCEPTED
P2-T04 Application API Key Lifecycle
→ COMPLETED / ACCEPTED
P2-T05 Runtime Authentication
→ COMPLETED / ACCEPTED
P2-T06 Unified Model Contract
→ COMPLETED / ACCEPTED
P2-T07 Provider Adapter
→ NOT STARTED
```

---

## 2. 当前系统形态

AIGate 继续采用：**模块化单体 + 单 MySQL**。

当前没有拆微服务，也没有引入 Redis / MQ / Nacos / Spring Cloud。

---

## 3. 当前身份与模型关系

```text
Team
├── Employee
└── Application
      └── 1:N ApplicationApiKey

Provider ───┐
            ├── ModelDeployment
Model ──────┘

Application
  ↓ defaultDeploymentId
ModelDeployment
```

---

## 4. Management Plane 与 Runtime Plane

```text
Management
/api/**
→ HTTP Basic

Runtime
/v1/**
→ Application API Key
```

两者仍在一个 Spring Boot 进程中，但认证机制已隔离。

---

## 5. Runtime Authentication

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
lookup by keyId
↓
ACTIVE
↓
SHA-256 + MessageDigest.isEqual
↓
ApplicationIdentity
↓
SecurityContext
```

ApplicationIdentity：

```text
applicationId
applicationName
teamId
```

不携带任何 API Key secret/hash。

---

## 6. Unified Model Contract

P2-T06 新增：

```text
runtime.dto
├── InvokeRequest
├── InvokeMessage
├── MessageRole
└── InvokeResponse
```

Client 与 AIGate 的契约由 AIGate 自己控制，不直接复用 OpenAI / Provider SDK DTO。

---

## 7. Invoke Request Boundary

```text
InvokeRequest
├── messages
├── temperature?
└── maxTokens?

InvokeMessage
├── role
└── content
```

Role 仅允许：

```text
system
user
assistant
```

Validation：

```text
messages → non-empty + nested validation
role → non-null
content → non-blank
temperature → 0.0..2.0
maxTokens → > 0
```

Request 不包含：

```text
applicationId
providerId
modelId
deploymentId
```

Application 身份来自 SecurityContext；模型目标后续从 Application.defaultDeploymentId 获取。

---

## 8. Invoke Response Boundary

```text
InvokeResponse
├── content
├── model
└── finishReason
```

当前明确不加入：

```text
usage
cost
requestId
provider raw response
deploymentId
routing metadata
```

---

## 9. Client Contract 与 Provider Contract 分离

```text
Client
↓
AIGate InvokeRequest
↓
ProviderAdapter
↓
Provider-specific Request
↓
Provider
↓
Provider-specific Response
↓
ProviderAdapter
↓
AIGate InvokeResponse
```

P2-T06 只完成 AIGate 侧统一 DTO；P2-T07 才建立 Provider Adapter。

---

## 10. Runtime Endpoint Wiring 状态

设计路径：

```text
POST /v1/invoke
```

当前尚未创建生产 handler。

原因：

```text
ModelProxyService
ProviderAdapter
RestClient
```

尚未具备。

为了避免 hard-coded / echo / fake model result，P2-T06 只完成契约，不伪造 200 成功响应。

因此当前真实状态：

```text
Runtime Authentication
→ DONE

Unified Contract
→ DONE

Production /v1/invoke execution
→ NOT WIRED
```

真正调用链预计在 P2-T08 接通。

---

## 11. Secret Strategies

Application API Key：

```text
high-entropy secret
→ SHA-256
→ verification only
```

Provider Credential：

```text
Provider Secret
→ AES-256-GCM
→ reversible for upstream call
```

---

## 12. Testing Architecture

P2-T06 Contract Test：

```text
Jackson 3
+
Jakarta Validation
↓
Invoke Contract
```

覆盖三种 role、unknown role 拒绝、messages / nested message 校验、temperature/maxTokens 边界、内部 selector 不暴露，以及 Response 最小字段。

GitHub 当前仍无 CI Test Gate。

---

## 13. 当前架构演进

```text
Phase 1 Identity Foundation
↓
P2-T01 Model Registry
↓
P2-T02 Provider Credential Protection
↓
P2-T03 Application -> default ModelDeployment
↓
P2-T04 Application API Key Lifecycle
↓
P2-T05 Runtime Authentication
↓
P2-T06 Unified Model Contract
```

下一步：

```text
P2-T07 Provider Adapter
```

---

## 14. ADR

当前已有：

```text
ADR-001 Phase 1 Authentication Baseline
ADR-002 Application Runtime Authentication
```

P2-T06 没有新增重大架构决策，不新增 ADR。

---

## 15. 当前技术债

继续保留 TD-001 ~ TD-010。

当前 Runtime 仍未具备真实模型调用；这是任务顺序中的未完成功能，不记为技术债。

没有为了 Contract 引入 Conversation persistence、Prompt aggregate、OpenAI SDK 或复杂领域层。

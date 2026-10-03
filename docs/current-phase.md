# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。
> 当阶段切换时直接更新本文件，不为每个 Phase 继续增加新的交接文档。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T01 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T02 — Provider Credential Protection（NOT STARTED）**

> P2-T01 已完成并通过开发导师 Code Review。按照项目规则，在用户明确确认前不得自动开始 P2-T02。

---

## 2. Phase 2 Business Goal

让一个 Application 使用自己的 AIGate API Key，通过 AIGate 安全调用一个 AI Model，同时 Application 不持有 Provider Secret。

目标主流程：

```text
Application
  ↓
AIGate API Key
  ↓
AIGate
  ↓
Application.defaultDeployment
  ↓
ModelDeployment
  ↓
ProviderAdapter
  ↓
Provider
```

Phase 2 只做 **single-model, non-streaming proxy**。

---

## 3. P2-T01 Closure — Model Registry Schema

状态：**DONE**

已落地：

```text
Provider
Model
ModelDeployment
```

当前实际关系：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

关键事实：

- `Model` 不直接属于 `Provider`
- `ModelDeployment` 同时关联 `provider_id` 与 `model_id`
- `ProviderType` 当前只有 `OPENAI_COMPATIBLE`
- `ModelDeployment` 已包含 `endpoint_url / remote_model_name / encrypted_credential / enabled`
- `encrypted_credential` 当前允许 NULL；真正的加密写入/读取属于 P2-T02，尚未开始
- Provider / Model / ModelDeployment 已完成基础 CRUD
- 名称唯一性由 Service 预检查 + MySQL UNIQUE 双层保护
- Deployment 创建/更新时会检查 Provider / Model 是否存在
- 删除仍被 Deployment 引用的 Provider / Model 会由 FK RESTRICT 阻止，并映射为 `409 RESOURCE_CONFLICT`
- 非法 `ProviderType` JSON 已映射为 `400 INVALID_REQUEST`

Flyway 已新增：

```text
V4__create_model_registry.sql
```

P2-T01 集成测试已覆盖核心场景：

- Provider 创建、重复名称、枚举持久化、非法枚举输入
- Model 创建、重复名称
- Deployment 创建
- Provider / Model 不存在时 404
- Deployment 名称重复 409
- 同名更新不误判自身
- endpointUrl 非法时 400
- Provider / Model 被 Deployment 引用时删除返回 409

---

## 4. Frozen Domain Model for Remaining Phase 2

### ApplicationApiKey

```text
Application 1:N ApplicationApiKey
```

关键设计：

- 一把 Key 属于一个 Application
- 一个 Application 可以有多把 Key
- 生命周期只做 `ACTIVE -> REVOKED`
- Key 格式概念上：`aig_live_<keyId>.<secret>`
- 数据库存 `keyId / prefix / SHA-256 hash`，不保存明文 secret
- 明文 Key 只在创建时返回一次
- Phase 2 不做自动过期、自动轮换、JWT、OAuth2

### Provider

表示模型服务提供方 / 协议类别。

当前实际字段：

```text
id
name
type
createdAt
updatedAt
```

当前只支持：

```text
OPENAI_COMPATIBLE
```

### Model

表示逻辑模型本身。

当前实际字段：

```text
id
name
createdAt
updatedAt
```

**Model 不直接属于 Provider。**

### ModelDeployment

表示真正可调用的模型实例，同时关联 Provider 和 Model。

当前实际字段：

```text
id
name
providerId
modelId
endpointUrl
remoteModelName
encryptedCredential
enabled
createdAt
updatedAt
```

运行时真正选择的是 `ModelDeployment`。

### Application.defaultDeploymentId

P2-T03 计划让 Application 临时绑定一个默认 Deployment：

```text
Application
  ↓
defaultDeploymentId
```

后续 Routing 阶段再演进为：

```text
Application -> ModelAlias -> Route -> ModelDeployment
```

当前不要提前实现 ModelAlias / Route。

---

## 5. Credential Strategy

### Application API Key

只需要验证，不需要恢复原文：

```text
high-entropy random secret
→ SHA-256
→ DB stores hash only
```

### Provider Credential

运行时必须恢复原文调用 Provider：

```text
Provider Secret
→ AES-GCM
→ encryptedCredential in MySQL
```

Master Key 来自环境变量：

```text
AIGATE_MASTER_KEY
```

建议密文使用版本化 envelope：

```text
v1:<iv>:<ciphertext+tag>
```

Phase 2 不引入 Vault / KMS / Secret Manager。

---

## 6. Security Boundary

管理面：

```text
/api/**
→ HTTP Basic
```

运行时：

```text
/v1/**
→ Application API Key
```

Runtime Header：

```http
Authorization: Bearer <AIGATE_API_KEY>
```

Bearer credential 在这里不是 JWT。

---

## 7. Runtime Contract

Planned endpoint：

```text
POST /v1/invoke
```

Minimal Request：

```text
messages
temperature?
maxTokens?
```

Message 只支持：

```text
system
user
assistant
```

Request 不携带 `providerId / modelId / deploymentId`。

Minimal Response：

```text
content
model
finishReason
```

Phase 2 不实现正式 Usage / Cost / Ledger。

---

## 8. Planned Runtime Flow

```text
POST /v1/invoke
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
ApplicationIdentity
↓
ModelProxyService
↓
Application.defaultDeployment
↓
check deployment.enabled
↓
load Provider + Model
↓
CredentialService.decrypt()
↓
ProviderAdapterRegistry
↓
OpenAICompatibleProviderAdapter
↓
RestClient
↓
Provider
↓
Unified Response
```

---

## 9. HTTP / Provider Decisions

Phase 2 使用：

```text
Spring RestClient
```

必须配置基本：

```text
connect timeout
read timeout
```

Provider Adapter 只做：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAICompatibleProviderAdapter
```

Provider Error 至少统一为：

```text
PROVIDER_BAD_REQUEST
PROVIDER_AUTH_ERROR
PROVIDER_RATE_LIMITED
PROVIDER_TIMEOUT
PROVIDER_UNAVAILABLE
PROVIDER_INVALID_RESPONSE
```

当前不做 Retry / Fallback / Circuit Breaker。

---

## 10. Database Migration Plan

已完成：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

后续计划：

```text
V5__add_application_default_deployment.sql
V6__create_application_api_key.sql
```

已经执行过的 migration 不修改。

---

## 11. Task Order / Gate

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **NOT STARTED** |
| P2-T03 | Application Default Deployment | NOT STARTED |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

**当前执行门：等待用户确认是否开始 P2-T02。**

---

## 12. Explicitly Deferred

Phase 2 禁止无真实问题提前引入：

```text
WebFlux
SSE Streaming
Redis
ModelAlias
Route
Weighted Routing
Gray Release
Policy
Data Classification
RPM / TPM
Quota
Retry
Fallback
Circuit Breaker
Resilience4j / Sentinel
RocketMQ
Usage Ledger
Nacos
Spring Cloud
Microservices
Vault / KMS
OAuth / OIDC
Tool Calling
Multimodal
JSON Schema
```

---

## 13. Phase 2 Acceptance Criteria

### Business

- Application 可以拥有 API Key
- Application 可以绑定默认 Deployment
- Application 可以通过 AIGate 调用模型

### Security

- ApiKey 明文不落库
- Provider Credential 明文不落库
- Provider Secret 不返回 Client
- revoked Key 无法调用
- Management / Runtime 使用不同认证方式

### Runtime

- Provider / Model / Deployment 可配置
- disabled Deployment 无法调用
- OpenAI-Compatible Adapter 工作正常
- RestClient 能完成真实出站 HTTP
- Provider Error 可以统一映射
- timeout 可控

### Test

- MySQL Testcontainers
- Mock Provider
- MockMvc
- 完整验证一次 Application -> AIGate -> Provider -> AIGate -> Application 链路

---

## 14. Architecture Rule

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

不要为了丰富技术栈提前加入技术。

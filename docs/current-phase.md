# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。
> 当阶段切换时，直接更新本文件，不为每个 Phase 继续增加新的交接文档。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T01 — Model Registry Schema**

## 2. Business Goal

让一个 Application 使用自己的 AIGate API Key，通过 AIGate 安全调用一个 AI Model，同时 Application 不持有 Provider Secret。

最小主流程：

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

## 3. Frozen Domain Model

### ApplicationApiKey

关系：

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

最小字段概念：

```text
id
name
type
createdAt
updatedAt
```

Phase 2 只支持：

```text
OPENAI_COMPATIBLE
```

### Model

表示逻辑模型本身。

最小字段概念：

```text
id
name
createdAt
updatedAt
```

**Model 不直接属于 Provider。**

### ModelDeployment

表示真正可调用的模型实例，同时关联 Provider 和 Model。

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

最小字段概念：

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

Phase 2 临时让 Application 绑定一个默认 Deployment：

```text
Application
  ↓
defaultDeploymentId
```

这是阶段性方案。后续 Routing 阶段会演进为：

```text
Application -> ModelAlias -> Route -> ModelDeployment
```

当前不要提前实现 ModelAlias / Route。

## 4. Credential Strategy

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

建议密文使用版本化 envelope，例如：

```text
v1:<iv>:<ciphertext+tag>
```

Phase 2 不引入 Vault / KMS / Secret Manager。

## 5. Security Boundary

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

这里的 Bearer credential 不是 JWT。

API Key 验证成功后形成 `ApplicationIdentity`，至少包含：

```text
applicationId
applicationName
teamId
```

后续 Proxy 逻辑只依赖 ApplicationIdentity，不关心 Key 的解析和 Hash 细节。

## 6. Runtime Contract

### Endpoint

```text
POST /v1/invoke
```

### Minimal Request

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

### Minimal Response

```text
content
model
finishReason
```

Phase 2 不实现正式 Usage / Cost / Ledger。

## 7. Runtime Flow

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
ProviderAdapter maps response
↓
Unified Response
```

## 8. Provider Adapter

Phase 2 只做内部普通 Java 抽象：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAICompatibleProviderAdapter
```

通过 `Provider.type` 选择 Adapter。

不要实现：

- SPI
- 动态 Jar
- 插件市场
- 多 Provider 体系

## 9. HTTP Client

Phase 2 使用：

```text
Spring RestClient
```

原因：当前是 Spring MVC + non-streaming，同步调用最简单。

必须配置基本：

```text
connect timeout
read timeout
```

暂不做：

```text
retry
fallback
circuit breaker
bulkhead
```

## 10. Provider Error Taxonomy

至少统一为：

```text
PROVIDER_BAD_REQUEST
PROVIDER_AUTH_ERROR
PROVIDER_RATE_LIMITED
PROVIDER_TIMEOUT
PROVIDER_UNAVAILABLE
PROVIDER_INVALID_RESPONSE
```

原则：Client Error、AIGate Error、Provider Error 必须可区分。

## 11. Database Migration Plan

历史 migration 不修改：

```text
V1 Team
V2 Employee
V3 Application
```

Phase 2 建议：

```text
V4__create_model_registry.sql
V5__add_application_default_deployment.sql
V6__create_application_api_key.sql
```

核心约束：

```text
provider.name UNIQUE
model.name UNIQUE
model_deployment.name UNIQUE
api_key.key_id UNIQUE
model_deployment.provider_id FK
model_deployment.model_id FK
application.default_deployment_id FK
application_api_key.application_id FK
```

Provider Credential 允许为空，以支持 MockLLM / 无认证私有服务。

## 12. Deletion / Disable Strategy

- ApiKey 不删除，使用 `REVOKED`
- ModelDeployment 优先 `enabled=false`
- Provider / Model 被 Deployment 引用时禁止删除，返回 409
- 当前不全局引入 soft delete

## 13. Hot Path Decision

Phase 2 **允许 Runtime 每次请求访问 MySQL**。

当前不要引入：

```text
Redis
Caffeine Snapshot
Config Push
Local Runtime Snapshot
```

最终 Gateway hot path 不访问 MySQL，但必须等真实性能 / 多节点问题出现后再演进。

## 14. Testing Baseline

测试链：

```text
MockMvc
↓
Spring Security
↓
API Key Auth
↓
ModelProxyService
↓
MySQL Testcontainers
↓
ProviderAdapter
↓
Test MockLLM HTTP Server
```

核心场景至少覆盖：

- 正确 API Key 调用成功
- 无 / 错误 / revoked API Key 返回 401
- Application 无 default Deployment
- Deployment disabled
- Provider 200
- Provider 429 -> PROVIDER_RATE_LIMITED
- Provider timeout -> PROVIDER_TIMEOUT
- Provider 5xx -> PROVIDER_UNAVAILABLE
- invalid response -> PROVIDER_INVALID_RESPONSE
- 创建 API Key 后数据库无明文 secret
- Provider Credential 数据库无明文
- Provider / Model 被引用删除返回 409

## 15. Task Order

严格按顺序推进：

```text
P2-T01 Model Registry Schema
P2-T02 Provider Credential Protection
P2-T03 Application Default Deployment
P2-T04 Application API Key Lifecycle
P2-T05 Runtime Authentication
P2-T06 Unified Model Contract
P2-T07 Provider Adapter
P2-T08 RestClient + Single Model Proxy
P2-T09 Provider Error Mapping
P2-T10 End-to-End Integration Test
P2-T11 Phase Closeout
```

当前只执行：

```text
P2-T01 — Model Registry Schema
```

P2-T01 只实现 Provider / Model / ModelDeployment 的最小 schema、Flyway、基础 CRUD 和约束，不提前实现后续任务。

## 16. Explicitly Deferred

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

## 17. Acceptance Criteria

Phase 2 完成必须满足：

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

## 18. Architecture Rule

整个 Phase 2 继续遵循：

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

不要为了丰富技术栈提前加入技术。

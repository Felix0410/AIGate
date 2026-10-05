# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。
> 当阶段切换时直接更新本文件，不为每个 Phase 继续增加新的交接文档。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T03 — Application Default Deployment（ACTIVE）**

Next Planned Task: **P2-T04 — Application API Key Lifecycle（NOT STARTED）**

> 用户已确认继续推进。当前只实现 Application 与默认 ModelDeployment 的阶段性绑定，不提前进入 API Key。

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

## 3. Completed Tasks

### P2-T01 — Model Registry Schema

状态：**DONE**

已完成：

- Provider / Model / ModelDeployment
- `Provider` 与 `Model` 在 `ModelDeployment` 汇合
- `ProviderType.OPENAI_COMPATIBLE`
- V4 Flyway migration
- CRUD、UNIQUE、FK、ON DELETE RESTRICT
- Model Registry 核心集成测试

### P2-T02 — Provider Credential Protection

状态：**DONE / ACCEPTED**

已实现：

```text
credential input
↓
CredentialService.encrypt()
↓
AES-256-GCM
↓
v1:<base64-iv>:<base64-ciphertext+tag>
↓
model_deployment.encrypted_credential
```

并已验证：明文不落库、普通 API 不暴露 credential、错误密钥/篡改密文失败、null credential 合法。

---

## 4. Current Task — P2-T03 Application Default Deployment

### Business Problem

P2-T01 已经有可调用目标 `ModelDeployment`，但 Application 当前与任何 Deployment 没有关系。

如果现在直接做 Runtime Proxy，就无法回答：

> 某个 Application 发起模型请求时，AIGate 应该调用哪个 Deployment？

当前还没有进入 Routing 阶段，因此 P2-T03 用最简单的阶段性方案解决：

```text
Application
  ↓
defaultDeploymentId
  ↓
ModelDeployment
```

这不是最终路由模型，而是为了让 Phase 2 的单模型 Happy Path 能继续向前推进。

### Minimal Scope

新增 Flyway migration：

```text
V5__add_application_default_deployment.sql
```

在 `application` 增加：

```text
default_deployment_id BIGINT NULL
```

关系：

```text
Application N ---- 1 ModelDeployment
```

外键目标：

```text
application.default_deployment_id
→ model_deployment.id
```

当前建议允许 NULL，因为：

- Phase 1 已存在的 Application 没有默认 Deployment
- 创建 Application 时不应被迫立即选择模型
- “Application 尚未配置模型”是合法的管理状态

### Delete Semantics

默认采用真实 FK 保护：

```text
ON DELETE RESTRICT
```

如果某个 ModelDeployment 已被 Application 设为默认 Deployment，则不能直接物理删除该 Deployment。

正确处理顺序应是：

```text
先解除/切换 Application.defaultDeployment
↓
再删除 ModelDeployment
```

不要使用：

```text
ON DELETE CASCADE
```

也不要因为删除 Deployment 自动删除 Application。

### API Scope

P2-T03 只需要让管理面能够配置默认 Deployment。

可以沿用 Application 的完整更新模型，或增加一个简单的显式配置入口；开发导师应优先选择与当前项目 API 风格最一致、代码最简单的方案。

无论采用哪一种形式，必须满足：

- 绑定前检查 Application 存在
- 非 null `defaultDeploymentId` 必须对应已存在 ModelDeployment
- 可以将 defaultDeployment 清空为 null
- Application Response 能让管理端知道当前 `defaultDeploymentId`
- 不暴露 ModelDeployment credential

### Explicit Non-Goals

P2-T03 不做：

```text
ModelAlias
Route
Weighted Routing
Gray Release
Application-Model 多对多权限
按请求传 deploymentId
Runtime Proxy
API Key
```

### Acceptance Focus

P2-T03 至少需要证明：

- V5 migration 可从现有 V1~V4 schema 平滑升级
- 旧 Application 在迁移后仍然有效，defaultDeploymentId 为 null
- Application 可绑定存在的 ModelDeployment
- Application 可切换默认 Deployment
- Application 可清空默认 Deployment
- 绑定不存在 Deployment 返回明确 404
- 删除被 Application 引用的 ModelDeployment 返回 409 RESOURCE_CONFLICT
- Application Response 正确返回 defaultDeploymentId
- 集成测试覆盖真实 MySQL FK 行为

---

## 5. Frozen Domain Model for Remaining Phase 2

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

### Provider / Model / ModelDeployment

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

运行时真正选择的是 `ModelDeployment`。

### Application.defaultDeploymentId

当前 P2-T03 实现：

```text
Application
  ↓
defaultDeploymentId
  ↓
ModelDeployment
```

这是明确的阶段性设计。

未来 Routing 出现后再演进为：

```text
Application -> ModelAlias -> Route -> ModelDeployment
```

当前不要提前实现未来结构。

---

## 6. Security Boundary

当前真正已实现：

```text
/api/**
→ HTTP Basic

Provider Credential at rest
→ AES-GCM encrypted
```

计划但尚未实现：

```text
/v1/**
→ Application API Key
```

---

## 7. Planned Runtime Contract

```text
POST /v1/invoke
```

Minimal Request：

```text
messages
temperature?
maxTokens?
```

Minimal Response：

```text
content
model
finishReason
```

Runtime API 尚未实现。

---

## 8. Planned Runtime Flow

```text
POST /v1/invoke
↓
ApiKeyAuthenticationFilter
↓
ApplicationIdentity
↓
ModelProxyService
↓
Application.defaultDeployment
↓
CredentialService.decrypt()
↓
ProviderAdapter
↓
RestClient
↓
Provider
```

---

## 9. Database Migration State

已完成：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

当前任务：

```text
V5__add_application_default_deployment.sql
```

后续计划：

```text
V6__create_application_api_key.sql
```

已执行 migration 不允许修改。

---

## 10. Task Order / Gate

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **DONE** |
| P2-T03 | Application Default Deployment | **ACTIVE** |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

**当前只执行 P2-T03。完成实现、测试、Code Review 和总控验收后，再等待用户确认 P2-T04。**

---

## 11. Explicitly Deferred

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

## 12. Phase 2 Acceptance Criteria

Phase 2 整体尚未完成。当前进度：

```text
P2-T01 ✅
P2-T02 ✅
P2-T03 🟢
P2-T04 ~ P2-T11 ⏸
```

---

## 13. Architecture Rule

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

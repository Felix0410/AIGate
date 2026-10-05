# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。
> 当阶段切换时直接更新本文件，不为每个 Phase 继续增加新的交接文档。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T02 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T03 — Application Default Deployment（NOT STARTED）**

> P2-T02 已通过总控验收。按照项目规则，在用户明确确认前不得自动开始 P2-T03。

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

业务目标：

> Provider Credential 可以由 AIGate 保存并在运行时恢复，但不以明文落库，也不通过普通管理 API 暴露。

当前实际链路：

```text
credential input
↓
ModelDeploymentService
↓
CredentialService.encrypt()
↓
AES/GCM/NoPadding
↓
v1:<base64-iv>:<base64-ciphertext+tag>
↓
model_deployment.encrypted_credential
```

恢复链路：

```text
encrypted_credential
↓
CredentialService.decrypt()
↓
plain Provider Credential
```

当前实际安全参数：

```text
AES-256
GCM
IV = 12 bytes random
Authentication Tag = 128 bits
Master Key = Base64 encoded 32 bytes
```

Master Key 来源：

```text
AIGATE_MASTER_KEY
```

关键事实：

- Master Key 不在数据库中保存
- Master Key 未写入 application.yaml
- 相同 credential 多次加密产生不同密文
- credential 明文不落库
- `ModelDeploymentResponse` 不包含 `credential` 或 `encryptedCredential`
- `credential = null` 合法，可支持无需认证的 Provider / MockLLM
- 错误 Master Key 解密失败
- 被篡改密文因 GCM authentication 失败
- 无效 Master Key 长度启动/构造时失败
- 加密/解密异常消息不包含 Provider Secret

PUT 当前保持项目既有“完整更新”语义：

```text
credential = null
→ 清空 encrypted_credential
```

这是当前明确、已有测试覆盖的行为。

P2-T02 没有新增数据库 migration，直接复用 V4 已存在的：

```text
model_deployment.encrypted_credential
```

测试已覆盖：

- encrypt → decrypt 可还原
- 同一明文两次加密密文不同
- 密文篡改解密失败
- 错误 Master Key 解密失败
- Master Key 长度错误失败
- null credential
- API 响应不泄露 credential
- 数据库实际保存密文而非明文
- credential 更新后重新加密
- PUT null 清空 credential

验收限制：

- GitHub 当前仍无 CI status / workflow run，自动测试门禁缺失继续记为 `TD-009`
- 本次验收基于仓库实现、测试代码与安全边界检查，不声称存在 GitHub CI 通过证明

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

### Provider / Model / ModelDeployment

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
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

## 5. Security Boundary

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

## 6. Planned Runtime Contract

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

## 7. Planned Runtime Flow

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

## 8. Database Migration State

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

P2-T02 无 schema 变化，因此没有为了任务数量人为增加 migration。

---

## 9. Task Order / Gate

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **DONE** |
| P2-T03 | Application Default Deployment | **NOT STARTED** |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

**当前执行门：等待用户明确确认是否开始 P2-T03。**

---

## 10. Explicitly Deferred

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

## 11. Phase 2 Acceptance Criteria

Phase 2 整体尚未完成。当前只确认：

```text
P2-T01 ✅
P2-T02 ✅
P2-T03 ~ P2-T11 ⏸
```

---

## 12. Architecture Rule

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

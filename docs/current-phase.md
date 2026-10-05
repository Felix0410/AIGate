# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T04 — Application API Key Lifecycle（ACTIVE）**

Next Planned Task: **P2-T05 — Runtime Authentication（NOT STARTED）**

> 用户已确认启动 P2-T04。当前只实现 Application API Key 的生命周期，不提前把 Key 接入 `/v1/**` Spring Security 认证链。

---

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | **ACTIVE** |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. Current Task — P2-T04 Application API Key Lifecycle

### Business Goal

Phase 1 的 HTTP Basic 解决的是管理 API 的人/管理端认证。

P2-T04 要建立另一类身份凭证：

```text
Application
↓
Application API Key
↓
未来 Runtime /v1/**
```

API Key 代表机器 Application，而不是 Employee。

本任务只解决：

> 如何安全签发、保存、展示元数据和撤销 Application API Key。

真正的请求认证与 SecurityContext 建立属于 P2-T05。

---

## 4. Frozen Domain Model

关系：

```text
Application 1 ---- N ApplicationApiKey
```

最小字段：

```text
id
applicationId
name
keyId
keyPrefix
keyHash
status
createdAt
revokedAt
```

状态只做：

```text
ACTIVE
REVOKED
```

不做：

```text
EXPIRED
SUSPENDED
COMPROMISED
automatic rotation
TTL
```

---

## 5. Key Format / Generation

概念格式冻结为：

```text
aig_live_<keyId>.<secret>
```

其中：

```text
keyId
→ 非秘密标识，用于未来快速定位数据库记录

secret
→ 真正的 bearer secret
```

生成要求：

- 使用 `SecureRandom`
- secret 至少 256-bit 随机熵
- 建议使用 URL-safe Base64 无 padding 编码
- 每次创建生成全新 keyId + secret

明文完整 Key：

```text
只在创建成功响应中返回一次
```

之后普通 GET / LIST 不允许再次返回完整 Key。

---

## 6. Hash Strategy

Application API Key 与 Provider Credential 不同。

API Key 运行时只需要验证，不需要恢复原文，因此：

```text
secret
↓
SHA-256
↓
key_hash
```

数据库不得保存：

```text
完整 API Key
secret 明文
```

这里不使用 BCrypt / Argon2，原因是 secret 由服务器生成并具有高随机熵，不是低熵人类密码。

P2-T05 验证时流程计划为：

```text
parse keyId
↓
lookup row by keyId
↓
check ACTIVE
↓
hash supplied secret
↓
constant-time compare
```

但认证逻辑本任务不实现。

---

## 7. Database Plan

新增：

```text
V6__create_application_api_key.sql
```

建议表：

```text
application_api_key
```

核心约束：

```text
id BIGINT PK AUTO_INCREMENT
application_id BIGINT NOT NULL
name VARCHAR(100) NOT NULL
key_id VARCHAR(...) NOT NULL UNIQUE
key_prefix VARCHAR(...) NOT NULL
key_hash CHAR(64) NOT NULL
status VARCHAR(20) NOT NULL
created_at TIMESTAMP NOT NULL
revoked_at TIMESTAMP NULL
```

索引：

```text
idx_application_api_key_application_id
UNIQUE(key_id)
```

### Application 删除语义

本表是 Application 生命周期内的从属凭证。

P2-T04 推荐：

```text
application_api_key.application_id
→ application.id
→ ON DELETE CASCADE
```

这是对项目通常 `RESTRICT` 原则的有意识例外：

- ApiKey 没有脱离 Application 独立存在的意义
- Application 被删除后所有其 Key 必须立即失效
- ApiKey 本身不提供物理删除接口
- 如果使用 RESTRICT，Application 一旦拥有 Key 将无法删除，因为 revoked row 仍需保留

不要把 CASCADE 扩展成全局默认策略；这里只用于强生命周期从属关系。

---

## 8. Management API Scope

Base Path：

```text
/api/applications/{applicationId}/api-keys
```

### Create

```text
POST /api/applications/{applicationId}/api-keys
```

Request 最小字段：

```text
name
```

Create Response 可以返回：

```text
id
applicationId
name
keyId
keyPrefix
status
createdAt
revokedAt
apiKey   ← 完整明文，只在这一次出现
```

### List Metadata

```text
GET /api/applications/{applicationId}/api-keys
```

只能返回元数据：

```text
id
applicationId
name
keyId
keyPrefix
status
createdAt
revokedAt
```

明确不得返回：

```text
apiKey
secret
keyHash
```

### Revoke

```text
POST /api/applications/{applicationId}/api-keys/{id}/revoke
```

语义：

```text
ACTIVE
→ REVOKED
→ revokedAt = now
```

记录保留，不物理删除。

重复 revoke 建议保持幂等：已 REVOKED 再 revoke 仍返回当前 REVOKED 元数据，不制造额外状态。

---

## 9. Error Semantics

至少处理：

```text
Application 不存在
→ 404 APPLICATION_NOT_FOUND

ApiKey 不存在 / 不属于该 Application
→ 404 API_KEY_NOT_FOUND

Create name validation error
→ 400 VALIDATION_ERROR
```

不要在错误消息或日志中输出完整 API Key / secret / keyHash。

---

## 10. Acceptance Focus

P2-T04 至少证明：

- Application 可以创建多把 API Key
- 创建成功时完整 Key 只返回一次
- 数据库不保存完整 Key / secret 明文
- DB 保存 SHA-256 hash
- keyId 唯一且可用于未来查找
- list 只返回元数据，不暴露 keyHash / secret
- ACTIVE Key 可以 revoke
- revoke 后 row 保留，状态为 REVOKED，revokedAt 非空
- 重复 revoke 行为稳定/幂等
- 不存在 Application 返回 404
- 不存在或不属于该 Application 的 Key 返回 404
- 删除 Application 后其从属 Key 不再存在（若采用冻结 CASCADE 方案）
- 使用真实 MySQL Testcontainers 验证存储与 FK 行为

---

## 11. Existing Phase 2 Facts

### Application Default Deployment

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

### Model Registry

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

### Provider Credential Protection

```text
Provider Credential
→ AES-256-GCM
→ encryptedCredential
```

### Current Security

```text
/api/**
→ HTTP Basic
```

`/v1/**` Application API Key Authentication 尚未实现。

---

## 12. Explicitly Deferred

P2-T04 不实现：

```text
ApiKeyAuthenticationFilter
SecurityContext ApplicationIdentity
/v1/** runtime authentication
API Key expiration
API Key automatic rotation
JWT
OAuth2 / OIDC
Redis API Key cache
Rate Limit / Quota
Routing
Runtime Proxy
```

---

## 13. Execution Gate

当前只执行：

```text
P2-T04 — Application API Key Lifecycle
```

完成实现、测试、开发导师 Code Review 后回总控验收。

**不得自动进入 P2-T05。**

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

# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T04 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T05 — Runtime Authentication（NOT STARTED）**

> P2-T04 已通过总控验收。在用户明确确认前，不得自动开始 P2-T05。

---

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | **DONE / ACCEPTED** |
| P2-T05 | Runtime Authentication | **NOT STARTED** |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T04 — Application API Key Lifecycle

业务目标：

> 为 Application 建立可签发、可撤销、明文不可恢复的机器访问凭证生命周期，为 P2-T05 Runtime Authentication 提供数据基础。

当前实际关系：

```text
Application 1 ---- N ApplicationApiKey
```

### Database

Flyway 已新增：

```text
V6__create_application_api_key.sql
```

当前表：

```text
application_api_key

id
application_id
name
key_id
key_prefix
key_hash
status
created_at
revoked_at
```

关键约束：

```text
key_id UNIQUE
application_id INDEX
application_id FK -> application.id ON DELETE CASCADE
```

CASCADE 是强生命周期从属凭证的局部例外：Application 删除后，其 API Key 必须一并失效/删除。

### Key Format

当前格式：

```text
aig_live_<keyId>.<secret>
```

生成策略：

```text
keyId  = SecureRandom 16 bytes
secret = SecureRandom 32 bytes
编码   = URL-safe Base64 without padding
```

其中 secret 具备 256-bit 随机熵。

### Storage Strategy

```text
secret
↓
SHA-256
↓
64-char hex key_hash
↓
MySQL
```

数据库不保存：

```text
完整 API Key
secret 明文
```

完整明文 Key 只在 Create Response 中返回一次。

### Lifecycle

当前状态只有：

```text
ACTIVE
REVOKED
```

撤销：

```text
ACTIVE
→ REVOKED
→ revokedAt = now
```

记录不物理删除。

重复 revoke 保持幂等：已 REVOKED 时直接返回原记录，不重写 revokedAt。

### Management API

```text
POST /api/applications/{applicationId}/api-keys
→ create

GET /api/applications/{applicationId}/api-keys
→ list metadata

POST /api/applications/{applicationId}/api-keys/{id}/revoke
→ revoke
```

Create Response 可以包含一次性字段：

```text
apiKey
```

普通 metadata response 不包含：

```text
apiKey
secret
keyHash
```

### Error Semantics

```text
Application 不存在
→ 404 APPLICATION_NOT_FOUND

ApiKey 不存在 / 不属于该 Application
→ 404 API_KEY_NOT_FOUND

name 校验失败
→ 400 VALIDATION_ERROR
```

---

## 4. P2-T04 Test Coverage

集成测试已覆盖：

- 创建 Key：ACTIVE、revokedAt=null、完整 Key 格式正确
- 同一 Application 创建多把 Key，keyId / apiKey 不同
- 数据库 keyHash 不等于 secret / 完整 Key，并等于 SHA-256(secret)
- List 不泄露 apiKey / secret / keyHash
- 无 Key Application 返回空列表
- Application 不存在时 Create / List 返回 404
- name 为空返回 400
- ACTIVE → REVOKED
- revoke 后 row 仍存在
- 重复 revoke 幂等，revokedAt 不变化
- 不存在 Key 返回 404 API_KEY_NOT_FOUND
- 跨 Application revoke 返回 404，原 Key 保持 ACTIVE
- 删除 Application 后 Key 由真实 MySQL FK CASCADE 删除

测试继续走：

```text
MockMvc
→ HTTP Basic
→ Controller
→ Service
→ MyBatis-Plus
→ MySQL Testcontainer
```

GitHub 当前没有 CI status / workflow run；TD-009 继续保留。

---

## 5. Existing Phase 2 Facts

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

### Application Default Deployment

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

### Current Security

当前已实现：

```text
/api/**
→ HTTP Basic
```

当前已经存在 Application API Key 数据，但它还没有接入 Spring Security Runtime 认证链。

---

## 6. Next Planned Task — P2-T05

**P2-T05 — Runtime Authentication**

冻结目标：

```text
Authorization: Bearer <AIGATE_API_KEY>
↓
parse keyId
↓
lookup ApplicationApiKey by keyId
↓
check ACTIVE
↓
SHA-256(supplied secret)
↓
constant-time compare
↓
ApplicationIdentity
↓
Spring Security Authentication / SecurityContext
```

计划边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

P2-T05 尚未启动。

---

## 7. Explicitly Deferred

```text
API Key expiration
automatic rotation
JWT
OAuth2 / OIDC
Redis API Key cache
Rate Limit / Quota
Routing
Runtime Proxy
WebFlux
SSE
MQ
Microservices
```

---

## 8. Architecture Rule

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

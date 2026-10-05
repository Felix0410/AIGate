# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前任务：

**P2-T04 — Application API Key Lifecycle（ACTIVE）**

下一计划任务：

**P2-T05 — Runtime Authentication（NOT STARTED）**

---

## 2. Phase 2 Task Status

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

## 3. 当前已完成能力

### P2-T01 Model Registry

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

### P2-T02 Provider Credential Protection

```text
Provider Credential
→ AES-256-GCM
→ encryptedCredential
→ MySQL
```

### P2-T03 Application Default Deployment

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

已支持绑定、切换、解绑以及 FK RESTRICT。

Flyway 当前：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
V5 → Application.default_deployment_id
```

---

## 4. 当前任务 P2-T04

目标：

> 为 Application 建立可签发、可撤销、明文不可恢复的机器访问凭证生命周期，为 P2-T05 Runtime Authentication 提供数据基础。

冻结关系：

```text
Application 1:N ApplicationApiKey
```

计划状态：

```text
ACTIVE
REVOKED
```

Key 概念格式：

```text
aig_live_<keyId>.<secret>
```

存储策略：

```text
完整明文 Key
→ 只在 create response 返回一次

secret
→ SHA-256
→ keyHash
→ DB
```

计划新增 Flyway：

```text
V6__create_application_api_key.sql
```

建议表字段：

```text
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

其中：

```text
key_id UNIQUE
application_id INDEX
```

Application API Key 是 Application 的生命周期从属凭证，当前冻结外键删除语义：

```text
application_api_key.application_id
→ application.id
→ ON DELETE CASCADE
```

这是针对强从属凭证的局部例外，不改变项目其他业务引用默认使用 RESTRICT 的原则。

---

## 5. P2-T04 Management API Scope

Base Path：

```text
/api/applications/{applicationId}/api-keys
```

只实现：

```text
POST /api/applications/{applicationId}/api-keys
→ create

GET /api/applications/{applicationId}/api-keys
→ list metadata

POST /api/applications/{applicationId}/api-keys/{id}/revoke
→ revoke
```

Create 时完整 Key 只返回一次。

List / revoke response 不返回：

```text
secret
完整 apiKey
keyHash
```

revoke：

```text
ACTIVE → REVOKED
revokedAt = now
```

记录不物理删除；重复 revoke 应保持稳定/幂等。

---

## 6. Current Security State

已经实现：

```text
/api/**
→ HTTP Basic

Provider Credential at rest
→ AES-GCM encrypted
```

P2-T04 只建立 API Key 数据生命周期。

尚未实现：

```text
/v1/**
→ Application API Key Authentication
```

该能力属于 P2-T05。

---

## 7. P2-T04 Acceptance Focus

至少验证：

- Application 可以创建多把 Key
- 每把 Key 的完整明文只在创建时返回
- DB 无完整 Key / secret 明文
- DB 保存 SHA-256 hash
- keyId 唯一
- list 不暴露 keyHash / secret
- ACTIVE 可 revoke
- revoke row 保留且 revokedAt 非空
- 重复 revoke 行为稳定
- Application 不存在 404
- Key 不存在 / 不属于 Application 404
- Application 删除时从属 Key 随之失效/删除
- Testcontainers MySQL 验证真实存储与 FK 行为

---

## 8. 当前技术债

继续保留：

- TD-001 HTTP Basic 为临时管理面认证
- TD-002 暂无 Role / Permission
- TD-003 暂无统一事务设计
- TD-004 跨模块存在少量 Mapper 直接依赖
- TD-005 Error Code 使用字符串
- TD-006 ApiErrorResponse 缺少 traceId / requestId / path
- TD-007 单 Organization 假设下的唯一约束
- TD-008 测试聚焦关键链路
- TD-009 暂无 CI Test Gate
- TD-010 Provider Master Key Rotation 尚未设计

P2-T04 不提前增加 API Key expiration / automatic rotation 等技术债；这些能力当前不属于需求。

---

## 9. Execution Gate

当前只执行：

**P2-T04 — Application API Key Lifecycle**

完成实现、测试和开发导师 Code Review 后，回总控验收。

**未验收前不得进入 P2-T05。**

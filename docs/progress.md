# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前执行门：

**P2-T04 COMPLETED / WAITING FOR USER CONFIRMATION**

下一计划任务：

**P2-T05 — Runtime Authentication（NOT STARTED）**

---

## 2. Phase 2 Task Status

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

### P2-T04 Application API Key Lifecycle

新增：

```text
Application 1:N ApplicationApiKey
```

Flyway：

```text
V6__create_application_api_key.sql
```

API Key 格式：

```text
aig_live_<keyId>.<secret>
```

生成：

- keyId：SecureRandom 16 bytes
- secret：SecureRandom 32 bytes
- URL-safe Base64 without padding

存储：

```text
secret
→ SHA-256
→ key_hash
```

完整 Key 只在创建响应返回一次。

生命周期：

```text
ACTIVE → REVOKED
```

当前管理 API：

```text
POST /api/applications/{applicationId}/api-keys
GET  /api/applications/{applicationId}/api-keys
POST /api/applications/{applicationId}/api-keys/{id}/revoke
```

Application 删除时，其 Key 通过 FK `ON DELETE CASCADE` 一并删除。

---

## 4. 当前数据库

MySQL 8.4。

Flyway：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
V5 → Application.default_deployment_id
V6 → ApplicationApiKey
```

V6 关键结构：

```text
application_api_key
- id PK
- application_id FK -> application.id ON DELETE CASCADE
- name
- key_id UNIQUE
- key_prefix
- key_hash CHAR(64)
- status
- created_at
- revoked_at
```

---

## 5. Security 状态

当前已经实现：

```text
/api/**
→ HTTP Basic

Provider Credential at rest
→ AES-GCM

Application API Key at rest
→ SHA-256(secret)
```

尚未实现：

```text
/v1/**
→ Application API Key Authentication
```

该能力属于 P2-T05。

---

## 6. Testing

P2-T04 集成测试覆盖：

- 创建 Key
- 同 Application 多 Key
- DB 不保存明文 secret / 完整 Key
- DB hash 与 SHA-256(secret) 一致
- List 不泄露敏感字段
- 空列表
- Application 不存在 404
- name validation
- ACTIVE → REVOKED
- revoke row 保留
- 重复 revoke 幂等
- API_KEY_NOT_FOUND
- 跨 Application 隔离
- Application 删除级联删除 Key

测试使用真实 MySQL Testcontainers。

GitHub 当前仍无 CI status / workflow run，因此 TD-009 继续保留。

---

## 7. 当前技术债

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

API Key expiration / automatic rotation 当前是明确非需求，不单独记为技术债。

---

## 8. P2-T04 Acceptance Result

- [x] Application 1:N ApplicationApiKey
- [x] V6 migration
- [x] keyId UNIQUE
- [x] SecureRandom 高熵 secret
- [x] SHA-256 hash 存储
- [x] 完整 Key 只在创建响应出现
- [x] List 不暴露 apiKey / secret / keyHash
- [x] ACTIVE → REVOKED
- [x] revoke row 保留
- [x] 重复 revoke 幂等
- [x] API_KEY_NOT_FOUND
- [x] 跨 Application 隔离
- [x] Application 删除 CASCADE Key
- [x] Testcontainers 覆盖真实 DB 行为
- [x] 未提前实现 Runtime Authentication

结论：

**P2-T04 — Application API Key Lifecycle：COMPLETED / ACCEPTED**

当前停在执行门，等待用户确认是否进入 P2-T05。

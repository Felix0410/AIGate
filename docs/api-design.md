# AIGate API Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T04 完成后的实现** 为准。

管理 API：

```text
/api/**
→ HTTP Basic
```

Runtime `/v1/**` 尚未实现。

---

## 2. Application API

Base Path：

```text
/api/applications
```

Application 当前支持：

```text
POST   /api/applications
GET    /api/applications/{id}
GET    /api/applications
PUT    /api/applications/{id}
DELETE /api/applications/{id}
```

Create / Update 字段：

```text
name
teamId
defaultDeploymentId?
```

---

## 3. Application API Key Management

Base Path：

```text
/api/applications/{applicationId}/api-keys
```

### Create

```text
POST /api/applications/{applicationId}/api-keys
```

Request：

```json
{
  "name": "dev-agent-primary"
}
```

成功响应：

```text
id
applicationId
name
keyId
keyPrefix
status
createdAt
revokedAt
apiKey
```

其中 `apiKey` 是完整明文 Key，**只在创建响应中返回一次**。

当前 Key 格式：

```text
aig_live_<keyId>.<secret>
```

---

## 4. List API Key Metadata

```text
GET /api/applications/{applicationId}/api-keys
```

返回：

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

明确不返回：

```text
apiKey
secret
keyHash
```

因此完整 Key 无法通过普通管理 GET 再次恢复。

Application 存在但没有 Key：

```text
200 []
```

---

## 5. Revoke API Key

```text
POST /api/applications/{applicationId}/api-keys/{id}/revoke
```

状态变化：

```text
ACTIVE
→ REVOKED
→ revokedAt = now
```

撤销不删除记录。

重复 revoke：

```text
REVOKED
→ 再次调用
→ 200
→ 保持原 revokedAt
```

当前为幂等操作。

---

## 6. API Key Error Semantics

```text
400 VALIDATION_ERROR
→ name 为空或超过限制

404 APPLICATION_NOT_FOUND
→ 路径中的 Application 不存在

404 API_KEY_NOT_FOUND
→ Key 不存在
→ 或 Key 不属于路径中的 Application
```

跨 Application 操作不会泄露 Key 是否属于其他 Application，统一返回 API_KEY_NOT_FOUND。

---

## 7. API Key Secret Boundary

API Key 仅在创建时作为明文输出：

```text
Create Response
→ apiKey
```

持久化：

```text
secret
→ SHA-256
→ keyHash
```

数据库与普通 API 都不暴露：

```text
secret
完整 apiKey
keyHash
```

---

## 8. Application Delete Semantics

删除 Application：

```text
DELETE /api/applications/{id}
```

数据库使用：

```text
application_api_key.application_id
→ application.id
→ ON DELETE CASCADE
```

因此 Application 删除后其所有 API Key 一并删除/失效。

---

## 9. ModelDeployment Credential Boundary

P2-T02 规则继续有效：

Create / Update 可输入：

```text
credential?
```

普通 Response 不包含：

```text
credential
encryptedCredential
```

---

## 10. Current Error Contract

当前相关错误包括：

```text
400 VALIDATION_ERROR
400 INVALID_REQUEST
401 UNAUTHORIZED
403 FORBIDDEN

404 TEAM_NOT_FOUND
404 APPLICATION_NOT_FOUND
404 PROVIDER_NOT_FOUND
404 MODEL_NOT_FOUND
404 MODEL_DEPLOYMENT_NOT_FOUND
404 API_KEY_NOT_FOUND

409 APPLICATION_NAME_ALREADY_EXISTS
409 PROVIDER_NAME_ALREADY_EXISTS
409 MODEL_NAME_ALREADY_EXISTS
409 MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS
409 RESOURCE_CONFLICT

500 INTERNAL_SERVER_ERROR
```

---

## 11. Planned Runtime Authentication

P2-T04 只创建和管理 API Key。

尚未实现：

```text
Authorization: Bearer <AIGATE_API_KEY>
↓
/v1/**
```

该能力属于 P2-T05。

计划验证链路：

```text
parse keyId
↓
lookup ApplicationApiKey
↓
check ACTIVE
↓
SHA-256(secret)
↓
constant-time compare
↓
ApplicationIdentity
↓
SecurityContext
```

---

## 12. API Design Principles

- Controller 只做请求/响应转换
- DTO 与 Entity 分离
- Secret 只在必要输入/一次性创建输出边界出现
- hash 不通过 API 暴露
- revoke 使用状态流转而不是删除
- 路径中的 Application 与 Key 归属一起校验
- Runtime 请求未来不直接携带内部 deploymentId

# AIGate API Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T05 完成后的实现** 为准。

当前认证边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

正式 Runtime 业务端点 `POST /v1/invoke` 尚未实现，但 `/v1/**` Security Chain 已经建立。

---

## 2. Management API Authentication

管理 API：

```text
/api/**
```

继续使用：

```text
HTTP Basic
```

Swagger：

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

继续公开。

Application API Key 不能替代 HTTP Basic 访问 Management API。

---

## 3. Runtime Authentication Contract

Runtime 路径：

```text
/v1/**
```

认证 Header：

```http
Authorization: Bearer <AIGATE_API_KEY>
```

Bearer 在这里表示 bearer credential，不是 JWT。

API Key 当前格式：

```text
aig_live_<keyId>.<secret>
```

Runtime 不接受 HTTP Basic 作为替代认证。

---

## 4. Runtime Authentication Result

认证成功后 Spring Security principal：

```text
ApplicationIdentity

applicationId
applicationName
teamId
```

不会暴露：

```text
apiKey
secret
keyHash
keyId
Authorization header
```

后续 Runtime API 应从 Authentication / SecurityContext 获取 ApplicationIdentity，而不是让 Client 传 applicationId。

---

## 5. Runtime Authentication Failure

以下情况统一：

```text
missing Authorization
non-Bearer scheme
malformed API Key
unknown keyId
revoked Key
wrong secret
missing Application
→ 401 UNAUTHORIZED
```

统一错误响应继续为：

```json
{
  "code": "UNAUTHORIZED",
  "message": "Authentication required",
  "timestamp": "...",
  "errors": null
}
```

不向未认证调用方返回：

```text
API_KEY_NOT_FOUND
KEY_REVOKED
WRONG_SECRET
```

---

## 6. Application API Key Management

管理端点继续存在：

```text
POST /api/applications/{applicationId}/api-keys
GET  /api/applications/{applicationId}/api-keys
POST /api/applications/{applicationId}/api-keys/{id}/revoke
```

Create Response 中完整 API Key 只返回一次。

List / Revoke 不返回：

```text
apiKey
secret
keyHash
```

---

## 7. API Key Verification

数据库查询：

```text
SELECT by key_id
```

验证：

```text
status == ACTIVE
↓
SHA-256(supplied secret)
↓
MessageDigest.isEqual(...)
```

不会扫描所有 API Key Hash。

---

## 8. Existing Application Configuration

Application 当前管理字段：

```text
name
teamId
defaultDeploymentId?
```

Runtime 后续会基于认证出的 Application 读取：

```text
Application.defaultDeploymentId
```

Client Runtime Request 不需要提供内部 Deployment ID。

---

## 9. ModelDeployment Credential Boundary

Create / Update 可输入：

```text
credential?
```

普通 Response 不包含：

```text
credential
encryptedCredential
```

Provider Secret 与 Application API Key 是两种不同凭证体系。

---

## 10. Current Error Contract

管理面相关：

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

409 *_ALREADY_EXISTS
409 RESOURCE_CONFLICT

500 INTERNAL_SERVER_ERROR
```

Runtime Authentication：

```text
所有凭证认证失败
→ 401 UNAUTHORIZED
```

---

## 11. Planned Runtime Business API

下一任务 P2-T06 才正式实现：

```text
POST /v1/invoke
```

计划最小 Request：

```text
messages
temperature?
maxTokens?
```

计划最小 Response：

```text
content
model
finishReason
```

P2-T05 没有提前实现模型调用协议。

---

## 12. API Design Principles

- Management 与 Runtime 身份机制隔离
- Runtime 身份来自 API Key，不由 Client 直接声明 applicationId
- Secret 不进入业务 DTO / principal
- 认证失败不泄露凭证内部状态
- Controller 不重新解析 API Key
- 后续 Runtime 业务只依赖 ApplicationIdentity
- 当前不使用 JWT / OAuth2

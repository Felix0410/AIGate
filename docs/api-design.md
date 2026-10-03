# AIGate API Design

## 1. 文档目的

本文档记录 AIGate 当前 API 的实际设计约定。

当前内容以 **Phase 2 / P2-T01 完成后的实现** 为准。

---

## 2. API Base Path

当前管理 API 使用：

```text
/api
```

当前资源：

```text
/api/teams
/api/employees
/api/applications
/api/providers
/api/models
/api/model-deployments
```

当前 `/api/**` 继续使用 HTTP Basic。

Phase 2 计划中的 Runtime API：

```text
/v1/**
```

尚未实现。

---

## 3. 已有基础资源

### Team

```text
id
name
createdAt
updatedAt
```

### Employee

```text
id
name
email
teamId
createdAt
updatedAt
```

### Application

```text
id
name
teamId
createdAt
updatedAt
```

---

## 4. Provider API

Base Path：

```text
/api/providers
```

支持：

```text
POST   /api/providers
GET    /api/providers/{id}
GET    /api/providers
PUT    /api/providers/{id}
DELETE /api/providers/{id}
```

Provider 当前字段：

```text
id
name
type
createdAt
updatedAt
```

`type` 当前只支持：

```text
OPENAI_COMPATIBLE
```

典型错误：

```text
400 VALIDATION_ERROR
400 INVALID_REQUEST
404 PROVIDER_NOT_FOUND
409 PROVIDER_NAME_ALREADY_EXISTS
409 RESOURCE_CONFLICT
```

`INVALID_REQUEST` 可用于无法解析的 ProviderType，例如传入未知枚举值。

---

## 5. Model API

Base Path：

```text
/api/models
```

支持：

```text
POST   /api/models
GET    /api/models/{id}
GET    /api/models
PUT    /api/models/{id}
DELETE /api/models/{id}
```

Model 当前字段：

```text
id
name
createdAt
updatedAt
```

典型错误：

```text
400 VALIDATION_ERROR
404 MODEL_NOT_FOUND
409 MODEL_NAME_ALREADY_EXISTS
409 RESOURCE_CONFLICT
```

Model 不直接暴露 providerId，因为当前领域设计中 Model 不直接属于 Provider。

---

## 6. ModelDeployment API

Base Path：

```text
/api/model-deployments
```

支持：

```text
POST   /api/model-deployments
GET    /api/model-deployments/{id}
GET    /api/model-deployments
PUT    /api/model-deployments/{id}
DELETE /api/model-deployments/{id}
```

当前请求主要字段：

```text
name
providerId
modelId
endpointUrl
remoteModelName
enabled
```

当前响应主要字段：

```text
id
name
providerId
modelId
endpointUrl
remoteModelName
enabled
createdAt
updatedAt
```

**encryptedCredential 当前不会通过普通 Response 暴露。**

典型错误：

```text
400 VALIDATION_ERROR
404 PROVIDER_NOT_FOUND
404 MODEL_NOT_FOUND
404 MODEL_DEPLOYMENT_NOT_FOUND
409 MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS
```

Provider / Model 被 Deployment 引用时删除会返回：

```text
409 RESOURCE_CONFLICT
```

---

## 7. Validation Rules

继续使用 Jakarta Bean Validation。

P2-T01 主要规则：

### Provider

```text
name
→ @NotBlank
→ @Size(max = 100)

type
→ required ProviderType
```

### Model

```text
name
→ @NotBlank
→ @Size(max = 100)
```

### ModelDeployment

```text
name
→ @NotBlank
→ @Size(max = 100)

providerId
→ @NotNull

modelId
→ @NotNull

endpointUrl
→ @NotBlank
→ @Size(max = 500)

remoteModelName
→ @NotBlank
→ @Size(max = 255)

enabled
→ @NotNull
```

字段校验失败：

```text
400 VALIDATION_ERROR
```

JSON 无法反序列化为请求 DTO：

```text
400 INVALID_REQUEST
```

---

## 8. Error Response Format

统一错误响应仍使用：

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2026-01-01T00:00:00Z",
  "errors": null
}
```

---

## 9. 当前 HTTP Status 与 Error Code

### 400 Bad Request

```text
VALIDATION_ERROR
INVALID_REQUEST
```

区别：

```text
VALIDATION_ERROR
→ JSON 已成功绑定 DTO，但字段约束不满足

INVALID_REQUEST
→ JSON 无法绑定成有效请求模型，例如未知枚举值
```

### 401 Unauthorized

```text
UNAUTHORIZED
```

当前实际用于 HTTP Basic 认证失败。

### 403 Forbidden

```text
FORBIDDEN
```

### 404 Not Found

```text
TEAM_NOT_FOUND
EMPLOYEE_NOT_FOUND
APPLICATION_NOT_FOUND
PROVIDER_NOT_FOUND
MODEL_NOT_FOUND
MODEL_DEPLOYMENT_NOT_FOUND
RESOURCE_NOT_FOUND
```

### 409 Conflict

```text
TEAM_NAME_ALREADY_EXISTS
EMAIL_ALREADY_EXISTS
APPLICATION_NAME_ALREADY_EXISTS
PROVIDER_NAME_ALREADY_EXISTS
MODEL_NAME_ALREADY_EXISTS
MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS
RESOURCE_CONFLICT
```

### 500 Internal Server Error

```text
INTERNAL_SERVER_ERROR
```

只用于未预料到的内部错误。

---

## 10. Error Handling 分层

### Spring Security 层

```text
RestAuthenticationEntryPoint
→ 401

RestAccessDeniedHandler
→ 403
```

### Spring MVC 层

当前 GlobalExceptionHandler 处理包括：

```text
MethodArgumentNotValidException
HttpMessageNotReadableException
ResourceNotFoundException
ConflictException
DuplicateKeyException
DataIntegrityViolationException
NoResourceFoundException
Exception
```

---

## 11. Unique Constraint 策略

继续采用：

```text
Service 预检查
↓
明确业务错误码

Database UNIQUE
↓
并发场景最终保证数据完整性
```

P2-T01 新增：

```text
provider.name
model.name
model_deployment.name
```

---

## 12. Authentication

当前真实状态：

```text
/api/**
→ HTTP Basic
```

公开路径：

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

Phase 2 计划：

```text
/v1/**
→ Application API Key
```

但该能力属于 P2-T05，目前 **NOT STARTED**。

---

## 13. API Design Principles

继续遵循：

- Controller 不承载业务逻辑
- DTO 与 Entity 分离
- Service 负责业务预检查
- Database Constraint 负责最终完整性
- 可预期问题不返回 500
- Secret / Credential 不通过普通查询接口暴露

---

## 14. Phase 2 Runtime API Planned Contract

以下是冻结计划，不是当前已实现 API：

```text
POST /v1/invoke
```

计划使用：

```text
Authorization: Bearer <AIGATE_API_KEY>
```

最小请求：

```text
messages
temperature?
maxTokens?
```

最小响应：

```text
content
model
finishReason
```

Runtime API 将在后续任务中实现，当前 P2-T01 收尾不会提前加入。


# AIGate API Design

## 1. 文档目的

本文档记录 AIGate 当前 API 的设计约定。

目标：

- 保持接口语义一致
- 固定 HTTP Status 与业务错误码
- 避免不同模块各自定义不同风格
- 为后续接口扩展提供统一基线

当前内容以 Phase 1 实际实现为准。

---

## 2. API Base Path

当前业务 API 统一使用：

```text
/api
```

当前资源：

```text
/api/teams
/api/employees
/api/applications
```

---

## 3. 当前资源模型

### 3.1 Team

```text
Team
├── id
├── name
├── createdAt
└── updatedAt
```

### 3.2 Employee

```text
Employee
├── id
├── name
├── email
├── teamId
├── createdAt
└── updatedAt
```

### 3.3 Application

```text
Application
├── id
├── name
├── teamId
├── createdAt
└── updatedAt
```

---

## 4. Team API

### Create Team

```http
POST /api/teams
```

Request:

```json
{
  "name": "Backend Team"
}
```

Success:

```text
200 OK
```

Response:

```json
{
  "id": 1,
  "name": "Backend Team",
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### Get Team

```http
GET /api/teams/{id}
```

Success:

```text
200 OK
```

Not Found:

```text
404
TEAM_NOT_FOUND
```

---

### List Teams

```http
GET /api/teams
```

Success:

```text
200 OK
```

Response:

```json
[
  {
    "id": 1,
    "name": "Backend Team",
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

---

### Update Team

```http
PUT /api/teams/{id}
```

Request:

```json
{
  "name": "Platform Team"
}
```

当前 PUT 语义：

> 完整更新所有可修改字段。

当前未实现 PATCH。

---

### Delete Team

```http
DELETE /api/teams/{id}
```

Success:

```text
200 OK
```

如果 Team 不存在：

```text
404
TEAM_NOT_FOUND
```

如果 Team 仍被 Employee 或 Application 引用：

```text
409
RESOURCE_CONFLICT
```

---

## 5. Employee API

### Create Employee

```http
POST /api/employees
```

Request:

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "teamId": 1
}
```

Success:

```text
200 OK
```

可能错误：

```text
400 VALIDATION_ERROR
404 TEAM_NOT_FOUND
409 EMAIL_ALREADY_EXISTS
```

---

### Get Employee

```http
GET /api/employees/{id}
```

可能错误：

```text
404 EMPLOYEE_NOT_FOUND
```

---

### List Employees

```http
GET /api/employees
```

Success:

```text
200 OK
```

---

### Update Employee

```http
PUT /api/employees/{id}
```

Request:

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "teamId": 1
}
```

当前 PUT 会完整更新：

```text
name
email
teamId
```

可能错误：

```text
404 EMPLOYEE_NOT_FOUND
404 TEAM_NOT_FOUND
409 EMAIL_ALREADY_EXISTS
```

---

### Delete Employee

```http
DELETE /api/employees/{id}
```

如果资源不存在：

```text
404 EMPLOYEE_NOT_FOUND
```

---

## 6. Application API

### Create Application

```http
POST /api/applications
```

Request:

```json
{
  "name": "aigate-web",
  "teamId": 1
}
```

Success:

```text
200 OK
```

可能错误：

```text
400 VALIDATION_ERROR
404 TEAM_NOT_FOUND
409 APPLICATION_NAME_ALREADY_EXISTS
```

---

### Get Application

```http
GET /api/applications/{id}
```

可能错误：

```text
404 APPLICATION_NOT_FOUND
```

---

### List Applications

```http
GET /api/applications
```

---

### Update Application

```http
PUT /api/applications/{id}
```

Request:

```json
{
  "name": "aigate-admin",
  "teamId": 1
}
```

可能错误：

```text
404 APPLICATION_NOT_FOUND
404 TEAM_NOT_FOUND
409 APPLICATION_NAME_ALREADY_EXISTS
```

---

### Delete Application

```http
DELETE /api/applications/{id}
```

如果资源不存在：

```text
404 APPLICATION_NOT_FOUND
```

---

## 7. Validation Rules

当前请求使用 Jakarta Bean Validation。

### Team

```text
name
→ @NotBlank
→ @Size(max = 100)
```

### Employee

```text
name
→ @NotBlank
→ @Size(max = 100)

email
→ @NotBlank
→ @Email
→ @Size(max = 255)

teamId
→ @NotNull
```

### Application

```text
name
→ @NotBlank
→ @Size(max = 100)

teamId
→ @NotNull
```

校验失败：

```text
400 VALIDATION_ERROR
```

---

## 8. Error Response Format

所有统一错误响应使用：

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2026-01-01T00:00:00Z",
  "errors": null
}
```

Validation Error 示例：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "timestamp": "...",
  "errors": {
    "name": "must not be blank"
  }
}
```

---

## 9. HTTP Status 与 Error Code

### 400 Bad Request

用于请求参数不合法。

```text
VALIDATION_ERROR
```

---

### 401 Unauthorized

用于未认证或认证失败。

```text
UNAUTHORIZED
```

例如：

```text
未提供 Basic Auth
错误用户名
错误密码
```

---

### 403 Forbidden

用于：

> 已认证，但没有访问权限。

当前 Phase 1 尚未引入 Role / Authority，因此暂时没有真实业务场景触发。

错误码：

```text
FORBIDDEN
```

---

### 404 Not Found

有两类 404。

#### 业务资源不存在

```text
TEAM_NOT_FOUND
EMPLOYEE_NOT_FOUND
APPLICATION_NOT_FOUND
```

例如：

```text
GET /api/teams/999
```

API 存在，但业务对象不存在。

#### HTTP Resource 不存在

```text
RESOURCE_NOT_FOUND
```

例如访问不存在的 URL / 静态资源。

这类错误与业务资源不存在需要区分。

---

### 409 Conflict

用于当前请求与系统当前状态发生冲突。

业务级冲突：

```text
TEAM_NAME_ALREADY_EXISTS
EMAIL_ALREADY_EXISTS
APPLICATION_NAME_ALREADY_EXISTS
```

数据库完整性冲突：

```text
RESOURCE_CONFLICT
```

例如：

```text
删除仍然被其他数据引用的 Team
```

---

### 500 Internal Server Error

```text
INTERNAL_SERVER_ERROR
```

只用于：

> 系统未预料到的内部错误。

已经能够明确识别的业务冲突不应该返回 500。

---

## 10. Error Handling 分层

当前错误处理分为两层。

### Spring Security 层

位于 Controller 之前：

```text
Security Filter Chain
↓
Controller
```

因此：

```text
401
403
```

由：

```text
RestAuthenticationEntryPoint
RestAccessDeniedHandler
```

处理。

---

### Spring MVC 层

业务 / 参数 / 数据库异常由：

```text
GlobalExceptionHandler
```

统一处理。

包括：

```text
MethodArgumentNotValidException
ResourceNotFoundException
ConflictException
DuplicateKeyException
DataIntegrityViolationException
NoResourceFoundException
Exception
```

---

## 11. Unique Constraint 策略

唯一性采用两层保护：

```text
Service 预检查
↓
返回明确业务错误

Database UNIQUE
↓
作为并发情况下最终数据完整性保证
```

例如 Employee email：

```text
Service
→ EMAIL_ALREADY_EXISTS

并发绕过 Service 检查
→ MySQL UNIQUE
→ RESOURCE_CONFLICT
```

数据库 UNIQUE 不允许因为已有 Service 检查而删除。

---

## 12. Authentication

当前 `/api/**` 使用：

```text
HTTP Basic
```

公开路径：

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

当前没有：

```text
JWT
OAuth2
OIDC
API Key
Role
Permission
```

---

## 13. API Design Principles

当前遵循：

### 13.1 Controller 不承载业务逻辑

Controller 负责：

```text
接收请求
↓
参数绑定 / Validation
↓
调用 Service
↓
转换 Response
```

业务规则放在 Service。

### 13.2 DTO 与 Entity 分离

HTTP API 不直接暴露数据库 Entity 作为请求 / 响应模型。

### 13.3 数据库约束不能被 Service 校验替代

Service 提供友好业务错误。

数据库负责最终数据完整性。

### 13.4 500 只代表未知错误

可预期业务问题应该映射为：

```text
400 / 404 / 409
```

而不是统一 500。

---

## 14. 当前暂不处理的问题

Phase 1 暂不处理：

- API Versioning
- Pagination
- Sorting
- Filtering
- PATCH
- Batch API
- Idempotency Key
- Rate Limit Header
- API Key Authentication
- Trace ID
- Request ID
- OpenAPI 细粒度 Annotation


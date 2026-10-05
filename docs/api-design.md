# AIGate API Design

## 1. 文档目的

本文档记录 AIGate 当前 API 的实际设计约定。

当前内容以 **Phase 2 / P2-T02 完成后的实现** 为准。

---

## 2. API Base Path

当前管理 API：

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

当前 `/api/**` 使用 HTTP Basic。

Runtime `/v1/**` 尚未实现。

---

## 3. Provider / Model / ModelDeployment

### Provider

```text
POST   /api/providers
GET    /api/providers/{id}
GET    /api/providers
PUT    /api/providers/{id}
DELETE /api/providers/{id}
```

### Model

```text
POST   /api/models
GET    /api/models/{id}
GET    /api/models
PUT    /api/models/{id}
DELETE /api/models/{id}
```

### ModelDeployment

```text
POST   /api/model-deployments
GET    /api/model-deployments/{id}
GET    /api/model-deployments
PUT    /api/model-deployments/{id}
DELETE /api/model-deployments/{id}
```

---

## 4. ModelDeployment Request

P2-T02 后，请求可包含：

```text
name
providerId
modelId
endpointUrl
remoteModelName
enabled
credential?
```

其中：

```text
credential
```

是 Provider Secret 的输入字段。

它只用于创建/更新时交给 `CredentialService` 加密，不作为数据库明文字段保存。

### Create

```http
POST /api/model-deployments
```

示例：

```json
{
  "name": "openai-prod",
  "providerId": 1,
  "modelId": 1,
  "endpointUrl": "https://example.com/v1",
  "remoteModelName": "model-x",
  "enabled": true,
  "credential": "provider-secret"
}
```

`credential` 可以为 null。

### Update

```http
PUT /api/model-deployments/{id}
```

当前 PUT 仍是完整更新语义。

因此：

```text
credential = new value
→ 替换旧 credential，并重新加密

credential = null
→ 清空原 encrypted credential
```

如果未来希望支持“其他字段更新但 credential 保持不变”，应重新设计 API 语义，例如专门 Credential endpoint 或 PATCH；当前阶段不提前增加。

---

## 5. ModelDeployment Response Secret Boundary

普通 Response 只包含：

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

明确不包含：

```text
credential
encryptedCredential
```

因此：

> Provider Secret 只允许输入，不允许通过普通 CRUD Response 查询回来。

数据库密文也不暴露给客户端。

---

## 6. Validation / Security Notes

现有字段继续使用 Jakarta Bean Validation。

credential 当前可为 null，没有引入“所有 Provider 必须配置 Secret”的错误规则。

原因：

- MockLLM 可以无认证
- 私有 Provider 可以无 credential

Provider Credential 的 Master Key 不通过 HTTP API 管理。

Master Key 来自：

```text
AIGATE_MASTER_KEY
```

---

## 7. Error Contract

现有错误语义继续有效：

```text
400 VALIDATION_ERROR
400 INVALID_REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 *_NOT_FOUND
409 *_ALREADY_EXISTS
409 RESOURCE_CONFLICT
500 INTERNAL_SERVER_ERROR
```

CredentialService 的加密/解密失败当前属于内部配置/数据完整性问题，没有新增对外业务 Error Code。

错误消息不得包含 Provider Credential 明文。

---

## 8. Authentication

当前真实状态：

```text
/api/**
→ HTTP Basic
```

尚未实现：

```text
/v1/**
→ Application API Key
```

不要把 Provider Credential Encryption 与 Application Runtime Authentication 混为一谈。

---

## 9. API Design Principles

继续遵循：

- Controller 不承载业务逻辑
- DTO 与 Entity 分离
- Secret 只在需要的输入边界出现
- Secret 不通过普通 Response 返回
- 数据库密文也不作为 API 数据暴露
- PUT 当前是完整更新
- 可预期业务错误不统一返回 500

---

## 10. Planned Runtime API

以下仍是冻结计划，不是当前已实现 API：

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

Runtime API 仍属于后续任务。

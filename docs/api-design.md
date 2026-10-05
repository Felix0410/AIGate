# AIGate API Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T03 完成后的实现** 为准。

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

支持：

```text
POST   /api/applications
GET    /api/applications/{id}
GET    /api/applications
PUT    /api/applications/{id}
DELETE /api/applications/{id}
```

### Create / Update Request

当前字段：

```text
name
teamId
defaultDeploymentId?
```

`defaultDeploymentId` 可为 null。

示例：

```json
{
  "name": "dev-agent",
  "teamId": 1,
  "defaultDeploymentId": 10
}
```

不指定模型部署：

```json
{
  "name": "dev-agent",
  "teamId": 1,
  "defaultDeploymentId": null
}
```

### Response

当前返回：

```text
id
name
teamId
defaultDeploymentId
createdAt
updatedAt
```

---

## 3. Application Default Deployment Semantics

创建 / 更新时：

```text
defaultDeploymentId = existing id
→ 绑定成功

defaultDeploymentId = missing id
→ 404 MODEL_DEPLOYMENT_NOT_FOUND

defaultDeploymentId = null
→ 不绑定 / 解绑
```

当前 PUT 是完整更新语义，因此：

```text
PUT defaultDeploymentId = null
→ 清空现有绑定
```

更新可以直接从 Deployment A 切换到 Deployment B。

---

## 4. Deployment Delete Conflict

如果某个 ModelDeployment 正被 Application 作为 default Deployment 引用：

```http
DELETE /api/model-deployments/{id}
```

返回：

```text
409 RESOURCE_CONFLICT
```

底层由 MySQL `ON DELETE RESTRICT` 保证最终数据完整性。

---

## 5. ModelDeployment Credential Boundary

P2-T02 规则继续有效。

Create / Update 可输入：

```text
credential?
```

普通 Response 不包含：

```text
credential
encryptedCredential
```

Provider Secret 不通过普通 CRUD API 回显。

---

## 6. Error Contract

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
409 APPLICATION_NAME_ALREADY_EXISTS
409 PROVIDER_NAME_ALREADY_EXISTS
409 MODEL_NAME_ALREADY_EXISTS
409 MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS
409 RESOURCE_CONFLICT
500 INTERNAL_SERVER_ERROR
```

P2-T03 没有新增新的错误码类型，复用 `MODEL_DEPLOYMENT_NOT_FOUND` 与 `RESOURCE_CONFLICT`。

---

## 7. 当前 API Design Principles

- Controller 只做请求/响应转换
- DTO 与 Entity 分离
- Service 提供明确业务预检查
- Database FK / UNIQUE 做最终完整性保护
- PUT 当前是完整更新
- Secret 不通过普通 Response 暴露
- Client 不直接通过 Runtime request 传 deploymentId；当前绑定发生在 Application 管理配置中

---

## 8. Planned Runtime API

尚未实现：

```text
POST /v1/invoke
```

未来 Runtime 会根据认证出的 Application 读取：

```text
Application.defaultDeploymentId
```

从而找到 ModelDeployment。

客户端 Runtime 请求不需要知道内部 Deployment ID。

Application API Key Authentication 仍属于后续 P2-T04 / P2-T05。

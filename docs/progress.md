# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前执行门：

**P2-T03 COMPLETED / WAITING FOR USER CONFIRMATION**

下一计划任务：

**P2-T04 — Application API Key Lifecycle（NOT STARTED）**

---

## 2. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | **DONE** |
| P2-T04 | Application API Key Lifecycle | **NOT STARTED** |
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

已完成 CRUD、UNIQUE、FK、ON DELETE RESTRICT 与关键集成测试。

### P2-T02 Provider Credential Protection

```text
Provider Credential
→ AES-256-GCM
→ encryptedCredential
→ MySQL
```

已完成加密/解密、密文落库、Secret 不回显、篡改检测、错误 Master Key 测试。

### P2-T03 Application Default Deployment

已新增：

```text
Application
  ↓
defaultDeploymentId
  ↓
ModelDeployment
```

实际行为：

- Application 可以不绑定 Deployment
- 创建时可以绑定存在的 Deployment
- 更新时可以切换 Deployment
- PUT `defaultDeploymentId = null` 可以解绑
- 不存在的 Deployment 返回 `404 MODEL_DEPLOYMENT_NOT_FOUND`
- 被 Application 引用的 Deployment 删除返回 `409 RESOURCE_CONFLICT`
- Application Response 返回 `defaultDeploymentId`

Flyway：

```text
V5__add_application_default_deployment.sql
```

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
```

V5 关键结构：

```text
application.default_deployment_id BIGINT NULL
FK -> model_deployment.id
ON DELETE RESTRICT
```

---

## 5. Security 状态

当前已经实现：

```text
/api/**
→ HTTP Basic

Provider Credential at rest
→ AES-GCM encrypted
```

尚未实现：

```text
/v1/**
→ Application API Key
```

Application API Key 属于 P2-T04 / P2-T05。

---

## 6. Testing

P2-T03 新增 Application 集成测试覆盖：

- defaultDeploymentId = null
- 绑定存在 Deployment
- 不存在 Deployment 404
- 切换默认 Deployment
- 清空绑定
- 真实 FK RESTRICT 删除冲突 409

测试继续依赖 Testcontainers MySQL。

GitHub 当前仍没有 CI status / workflow run，因此 TD-009 继续保留。

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

P2-T03 的 `Application.defaultDeploymentId` 是有意识的阶段性耦合，不单独记为技术债；后续 Routing 出现真实需求后再演进。

---

## 8. P2-T03 Acceptance Result

- [x] V5 新增 nullable default_deployment_id
- [x] FK 指向 model_deployment
- [x] ON DELETE RESTRICT
- [x] Application 可无默认 Deployment
- [x] Application 可绑定默认 Deployment
- [x] Application 可切换默认 Deployment
- [x] Application 可清空默认 Deployment
- [x] 缺失 Deployment 返回 404
- [x] 删除被引用 Deployment 返回 409
- [x] Application Response 返回 defaultDeploymentId
- [x] Testcontainers 集成测试覆盖关键行为
- [x] 未提前引入 Routing / ModelAlias / API Key / Runtime Proxy

结论：

**P2-T03 — Application Default Deployment：COMPLETED / ACCEPTED**

当前停在执行门，等待用户确认是否进入 P2-T04。

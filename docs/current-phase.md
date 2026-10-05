# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T03 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T04 — Application API Key Lifecycle（NOT STARTED）**

> P2-T03 已通过总控验收。在用户明确确认前，不得自动开始 P2-T04。

---

## 2. Completed Tasks

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | **DONE / ACCEPTED** |
| P2-T04 | Application API Key Lifecycle | **NOT STARTED** |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T03 — Application Default Deployment

业务目的：在 Routing 尚未出现之前，让 AIGate 能明确知道某个 Application 默认应该调用哪个 ModelDeployment。

当前实际关系：

```text
Application
  ↓ nullable defaultDeploymentId
ModelDeployment
```

这是 Phase 2 的阶段性绑定。后续 Routing 阶段再演进为：

```text
Application -> ModelAlias -> Route -> ModelDeployment
```

当前不要提前实现 ModelAlias / Route。

### Database

Flyway 已新增：

```text
V5__add_application_default_deployment.sql
```

实际 schema：

```text
application.default_deployment_id BIGINT NULL
    FK -> model_deployment.id
    ON DELETE RESTRICT
```

关键语义：

- 允许 NULL：Application 可以先创建、后配置模型
- FK：不能绑定不存在的 Deployment
- RESTRICT：被 Application 引用的 Deployment 不能直接删除

### Application Behavior

Application Create / Update 请求现在可包含：

```text
defaultDeploymentId?
```

Application Response 返回：

```text
defaultDeploymentId
```

当前行为：

```text
Create defaultDeploymentId = null
→ 允许创建

Create/Update existing deploymentId
→ 成功绑定

Create/Update missing deploymentId
→ 404 MODEL_DEPLOYMENT_NOT_FOUND

PUT deployment A -> deployment B
→ 切换默认 Deployment

PUT defaultDeploymentId = null
→ 解绑

DELETE referenced ModelDeployment
→ 409 RESOURCE_CONFLICT
```

`Application.defaultDeploymentId` 使用 `FieldStrategy.ALWAYS`，以保持当前项目 PUT 的完整更新语义，使 null 能真正写回数据库完成解绑。

### Testing

Application 集成测试已覆盖：

- 不指定 defaultDeploymentId 创建成功
- 绑定存在 Deployment
- 绑定不存在 Deployment 返回 404
- 切换默认 Deployment
- PUT null 解绑
- 删除被 Application 引用的 Deployment 返回 409

测试继续走：

```text
MockMvc
→ Spring Security
→ Controller
→ Service
→ MyBatis-Plus
→ MySQL Testcontainer
```

GitHub 当前仍无 CI status / workflow run；自动测试门禁缺失继续属于 TD-009。

---

## 4. Existing Phase 2 Facts

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
→ model_deployment.encrypted_credential
```

Master Key：

```text
AIGATE_MASTER_KEY
```

### Current Security

```text
/api/**
→ HTTP Basic
```

Runtime Application API Key 尚未实现。

---

## 5. Next Planned Task

**P2-T04 — Application API Key Lifecycle**

冻结方向：

```text
Application 1:N ApplicationApiKey
```

计划只实现：

```text
CREATE
ACTIVE
REVOKED
```

Key 明文只创建时返回一次，数据库保存可查询 identifier/prefix + SHA-256 hash，不保存完整明文。

**当前 P2-T04 尚未启动。**

---

## 6. Explicitly Deferred

```text
WebFlux
SSE Streaming
Redis
ModelAlias
Route
Weighted Routing
Gray Release
Policy
Quota / RPM / TPM
Retry / Fallback / Circuit Breaker
RocketMQ
Usage Ledger
Nacos
Spring Cloud
Microservices
Vault / KMS
OAuth / OIDC
Tool Calling
Multimodal
```

---

## 7. Architecture Rule

```text
业务问题
→ 最简单可运行方案
→ 打通主流程
→ 验证
→ 暴露真实问题
→ 再优化架构
```

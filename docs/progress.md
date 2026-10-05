# AIGate Progress

## 1. 当前项目状态

当前阶段：

**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前任务：

**P2-T03 — Application Default Deployment（ACTIVE）**

下一计划任务：

**P2-T04 — Application API Key Lifecycle（NOT STARTED）**

Phase 1 已完成并验收通过。
P2-T01 已完成。
P2-T02 已通过总控验收。

---

## 2. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **DONE** |
| P2-T03 | Application Default Deployment | **ACTIVE** |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

当前只执行 P2-T03，不提前进入 P2-T04。

---

## 3. 已完成能力

### P2-T01 Model Registry

已建立：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

Provider / Model / ModelDeployment 已完成基础 CRUD、唯一约束、外键约束和核心集成测试。

### P2-T02 Provider Credential Protection

已实现：

```text
Plain Provider Credential
↓
CredentialService
↓
AES/GCM/NoPadding
↓
v1:<iv>:<ciphertext+tag>
↓
model_deployment.encrypted_credential
```

当前行为：

- credential 明文不落库
- 普通 Response 不返回 credential
- null credential 合法
- 错误 Master Key / 被篡改密文无法成功解密
- PUT credential=null 会清空旧 credential

---

## 4. 当前任务 P2-T03

目标：

> 在还没有 Routing 的情况下，为每个 Application 提供一个最简单、明确的默认模型调用目标，使后续 single-model proxy 能确定应该调用哪个 ModelDeployment。

阶段性关系：

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

计划数据库变化：

```text
V5__add_application_default_deployment.sql
```

计划字段：

```text
application.default_deployment_id BIGINT NULL
```

设计原则：

- `default_deployment_id` 允许 NULL
- 旧 Application 迁移后继续有效
- 非 null 值必须引用已存在的 ModelDeployment
- FK 使用 `ON DELETE RESTRICT`
- Application 可以绑定、切换、清空默认 Deployment
- 删除正在被 Application 引用的 Deployment 应返回 409 RESOURCE_CONFLICT
- 不提前实现 ModelAlias / Route / 多 Deployment 路由

P2-T03 验收重点：

- V5 migration 从现有 schema 正常升级
- Application 可绑定存在的 Deployment
- Application 可切换 Deployment
- Application 可清空绑定
- 不存在 Deployment 返回 404
- Application Response 返回 defaultDeploymentId
- 真实 MySQL FK 删除保护有集成测试

---

## 5. 当前数据库

MySQL 8.4。

已完成 Flyway：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

当前任务计划新增：

```text
V5 → Application.default_deployment_id
```

P2-T04 才计划：

```text
V6 → Application API Key
```

---

## 6. Security 状态

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

Application API Key 属于后续 P2-T04 / P2-T05。

---

## 7. Testing

当前测试基础继续是：

- Spring Boot Test
- MockMvc
- Spring Security Test
- Testcontainers
- MySQL 8.4

P2-T03 重点增加 Application ↔ ModelDeployment 关系与 FK 删除语义测试。

GitHub 当前仍无 CI status / workflow run，因此自动测试门禁缺失继续保留为 TD-009。

---

## 8. 当前架构

继续保持：

**模块化单体**

当前主要模块：

```text
team
employee
application
provider
model
deployment
credential
common
config
security
```

P2-T03 只增加 Application 与 Deployment 的简单关系，不引入 Route 模块。

---

## 9. 当前技术债

### TD-001 HTTP Basic 是临时管理面认证方案
最终管理身份体系尚未确定。

### TD-002 暂无 Role / Permission
等真实授权需求出现后再实现。

### TD-003 Service 暂无统一事务设计
出现真实多表原子操作后再明确事务边界。

### TD-004 跨模块存在少量 Mapper 依赖
当前保持简单。

### TD-005 Error Code 使用字符串
规模扩大后考虑统一管理。

### TD-006 ApiErrorResponse 可观测性不足
缺少 traceId / requestId / path。

### TD-007 当前唯一约束基于单 Organization 假设
未来 Multi-Tenant 时重新评估。

### TD-008 测试覆盖关键链路，不追求完整覆盖率
随复杂度逐步增加。

### TD-009 暂无 CI Test Gate
GitHub 当前无自动测试状态检查。

### TD-010 Provider Master Key Rotation 尚未设计
当前一个 `AIGATE_MASTER_KEY` 负责 v1 Credential 解密，未来真实轮换需求出现后再设计迁移 / 多版本读取策略。

---

## 10. Current Execution Gate

**P2-T03 — Application Default Deployment：ACTIVE**

当前只实现本任务。完成实现、测试和 Code Review 后，再回到总控验收；未经过用户确认不得进入 P2-T04。

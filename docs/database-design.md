# AIGate Database Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T03 完成后的实际实现** 为准。

数据库：**MySQL 8.4**

Flyway：

```text
V1 → team
V2 → employee
V3 → application
V4 → provider / model / model_deployment
V5 → application.default_deployment_id
```

已执行 migration 不修改，后续变化继续新增 migration。

---

## 2. 当前领域关系

```text
Team 1 ---- N Employee
Team 1 ---- N Application

Provider 1 ---- N ModelDeployment
Model    1 ---- N ModelDeployment

Application N ---- 0..1 ModelDeployment
        via default_deployment_id
```

注意：多个 Application 可以暂时指向同一个默认 Deployment；一个 Application 当前最多配置一个默认 Deployment。

---

## 3. application

当前字段：

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
team_id BIGINT NOT NULL FK -> team.id
default_deployment_id BIGINT NULL FK -> model_deployment.id
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

`default_deployment_id` 在 V5 新增。

外键：

```text
FOREIGN KEY (default_deployment_id)
REFERENCES model_deployment(id)
ON DELETE RESTRICT
```

为什么 nullable：

```text
Application 可以先创建
模型绑定可以稍后完成
```

为什么 RESTRICT：

```text
Application 仍引用 Deployment
→ 不允许直接删除 Deployment
```

避免 Application 留下无效运行配置。

---

## 4. model_deployment

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
provider_id BIGINT NOT NULL FK
model_id BIGINT NOT NULL FK
endpoint_url VARCHAR(500) NOT NULL
remote_model_name VARCHAR(255) NOT NULL
encrypted_credential TEXT NULL
enabled BOOLEAN NOT NULL DEFAULT TRUE
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

外键：

```text
provider_id → provider.id ON DELETE RESTRICT
model_id    → model.id    ON DELETE RESTRICT
```

P2-T02 已启用 `encrypted_credential` 的 AES-GCM 密文语义。

---

## 5. P2-T03 数据完整性策略

Application 绑定默认 Deployment 时采用两层保护：

```text
Service
→ 检查 ModelDeployment 是否存在
→ 不存在返回 MODEL_DEPLOYMENT_NOT_FOUND

Database FK
→ 最终保证不能保存非法 deployment id
```

删除被 Application 引用的 Deployment：

```text
MySQL FK RESTRICT
→ DataIntegrityViolationException
→ 409 RESOURCE_CONFLICT
```

---

## 6. NULL 更新语义

当前 Application PUT 是完整更新。

因此：

```text
defaultDeploymentId = null
→ application.default_deployment_id = NULL
```

Java Entity 对该字段使用 MyBatis-Plus `FieldStrategy.ALWAYS`，确保 null 不被更新策略跳过。

---

## 7. UNIQUE / FK Strategy

主要 UNIQUE：

```text
team.name
employee.email
application.name
provider.name
model.name
model_deployment.name
```

真实 FK：

```text
employee.team_id → team.id
application.team_id → team.id
application.default_deployment_id → model_deployment.id
model_deployment.provider_id → provider.id
model_deployment.model_id → model.id
```

数据库继续作为最终完整性边界。

---

## 8. 当前不做

```text
Application ↔ Route 关系表
ModelAlias
Weighted target
多默认 Deployment
Soft Delete Framework
Multi-Tenant
Redis Runtime Snapshot
```

这些等后续真实 Routing / 多节点问题出现后再设计。

---

## 9. 下一计划 Migration

P2-T04 计划新增：

```text
V6__create_application_api_key.sql
```

当前尚未启动，不把计划结构写成已实现事实。

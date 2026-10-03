# AIGate Database Design

## 1. 文档目的

本文档记录 AIGate 当前实际数据库设计。

目标：

- 描述现有表结构
- 固定表之间的关系
- 记录 UNIQUE / FK / INDEX 等约束
- 解释数据库层承担的数据完整性职责
- 为后续 migration 演进提供基线

当前内容以 **Phase 2 / P2-T01 完成后的实际实现** 为准。

---

## 2. 当前数据库

数据库：**MySQL 8.4**

Schema：

```text
aigate
```

当前 Flyway migration：

```text
V1 → team
V2 → employee
V3 → application
V4 → provider / model / model_deployment
```

原则：

> 已执行 migration 不允许修改，后续变化必须新增 migration。

---

## 3. 当前领域关系

Identity：

```text
Team 1 ---- N Employee
Team 1 ---- N Application
```

Model Registry：

```text
Provider 1 ---- N ModelDeployment
Model    1 ---- N ModelDeployment
```

因此：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

重要：`Model` 不直接属于 `Provider`。

---

## 4. team

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(100) | NOT NULL, UNIQUE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

---

## 5. employee

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| team_id | BIGINT | NOT NULL, FK |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

约束：

```text
FOREIGN KEY (team_id)
REFERENCES team(id)
ON DELETE RESTRICT
```

索引：`idx_employee_team_id`

---

## 6. application

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(100) | NOT NULL, UNIQUE |
| team_id | BIGINT | NOT NULL, FK |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

约束：

```text
FOREIGN KEY (team_id)
REFERENCES team(id)
ON DELETE RESTRICT
```

索引：`idx_application_team_id`

`default_deployment_id` 尚未加入；该变化属于 P2-T03。

---

## 7. provider

P2-T01 新增。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Provider ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | Provider 名称 |
| type | VARCHAR(50) | NOT NULL | Provider 协议类型 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

当前 `type` 在 Java 中对应：

```text
ProviderType.OPENAI_COMPATIBLE
```

---

## 8. model

P2-T01 新增。

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(100) | NOT NULL, UNIQUE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

Model 当前只表示逻辑模型，不直接保存 Provider 关系。

---

## 9. model_deployment

P2-T01 新增。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Deployment ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | Deployment 名称 |
| provider_id | BIGINT | NOT NULL, FK | 所属 Provider |
| model_id | BIGINT | NOT NULL, FK | 对应逻辑 Model |
| endpoint_url | VARCHAR(500) | NOT NULL | 实际调用地址 |
| remote_model_name | VARCHAR(255) | NOT NULL | 上游模型标识 |
| encrypted_credential | TEXT | NULL | Provider Credential 密文预留 |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

外键：

```text
model_deployment.provider_id
→ provider.id
→ ON DELETE RESTRICT

model_deployment.model_id
→ model.id
→ ON DELETE RESTRICT
```

索引：

```text
idx_model_deployment_provider_id
idx_model_deployment_model_id
```

注意：`encrypted_credential` 字段已经存在，但 P2-T02 尚未开始，因此当前不能把“Credential 已安全加密存储”当作已完成事实。

---

## 10. UNIQUE 策略

当前主要唯一约束：

```text
team.name
employee.email
application.name
provider.name
model.name
model_deployment.name
```

采用两层保护：

```text
Service 预检查
→ 明确业务错误

Database UNIQUE
→ 并发情况下最终保证数据完整性
```

---

## 11. FK / ON DELETE RESTRICT

当前真实外键包括：

```text
employee.team_id → team.id
application.team_id → team.id
model_deployment.provider_id → provider.id
model_deployment.model_id → model.id
```

统一采用 RESTRICT 思路：

> 仍被业务对象引用的资源不能被隐式级联删除。

因此删除被 Deployment 引用的 Provider / Model 时，数据库拒绝删除，应用层映射为 `409 RESOURCE_CONFLICT`。

---

## 12. 时间字段

继续统一使用：

```text
created_at
updated_at
```

数据库类型：`TIMESTAMP`

Java 对应：`Instant`

数据库负责默认创建与更新时间。

---

## 13. 数据完整性职责

数据库负责最终保证：

```text
PRIMARY KEY
NOT NULL
UNIQUE
FOREIGN KEY
```

应用层负责：

```text
业务语义
友好错误码
存在性预检查
唯一性预检查
```

两者不能互相替代。

---

## 14. 当前删除策略

当前没有全局软删除。

P2-T01 中：

- Provider / Model / ModelDeployment CRUD 当前仍允许物理删除
- Provider / Model 若被 Deployment 引用则由 FK RESTRICT 阻止
- Deployment 已有 `enabled` 字段，用于未来“保留配置但禁止调用”的业务语义

ApiKey 的 `REVOKED` 策略属于 P2-T04，尚未实现。

---

## 15. 当前不做的数据库能力

当前不引入：

```text
Multi-Tenant
UUID / Snowflake
Soft Delete Framework
分库分表
读写分离
Redis Runtime Snapshot
```

必须等真实问题出现后再演进。

---

## 16. 后续计划 Migration

当前冻结计划：

```text
V5__add_application_default_deployment.sql
V6__create_application_api_key.sql
```

但 P2-T02 尚未启动，后续 migration 只有进入对应任务后才实施。

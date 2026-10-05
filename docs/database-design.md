# AIGate Database Design

## 1. 文档目的

本文档记录 AIGate 当前实际数据库设计。

当前内容以 **Phase 2 / P2-T02 完成后的实际实现** 为准。

---

## 2. 当前数据库

数据库：**MySQL 8.4**

当前 Flyway migration：

```text
V1 → team
V2 → employee
V3 → application
V4 → provider / model / model_deployment
```

原则：已执行 migration 不允许修改，后续变化必须新增 migration。

---

## 3. 当前领域关系

```text
Team 1 ---- N Employee
Team 1 ---- N Application

Provider 1 ---- N ModelDeployment
Model    1 ---- N ModelDeployment
```

重要：`Model` 不直接属于 `Provider`。

---

## 4. Identity Tables

### team

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

### employee

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL
email VARCHAR(255) NOT NULL UNIQUE
team_id BIGINT NOT NULL FK -> team.id
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

### application

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
team_id BIGINT NOT NULL FK -> team.id
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

`default_deployment_id` 尚未加入，属于 P2-T03。

---

## 5. provider

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
type VARCHAR(50) NOT NULL
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

当前 Java enum：

```text
ProviderType.OPENAI_COMPATIBLE
```

---

## 6. model

```text
id BIGINT PK AUTO_INCREMENT
name VARCHAR(100) NOT NULL UNIQUE
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

Model 只表示逻辑模型。

---

## 7. model_deployment

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

索引：

```text
idx_model_deployment_provider_id
idx_model_deployment_model_id
```

---

## 8. encrypted_credential 当前真实语义

P2-T02 已正式启用 `model_deployment.encrypted_credential`。

数据库只保存：

```text
v1:<base64(iv)>:<base64(ciphertext+tag)>
```

不保存 Provider Credential 明文。

当前加密方案：

```text
AES-256-GCM
IV = 12 random bytes
Tag = 128 bits
```

Master Key：

```text
AIGATE_MASTER_KEY
```

Master Key 不存 MySQL。

### nullable

`encrypted_credential` 允许 NULL。

表示该 Deployment 当前不需要 Provider Credential。

### update null

当前 PUT 是完整更新，因此：

```text
credential = null
→ encrypted_credential = NULL
```

MyBatis-Plus Entity 使用 `FieldStrategy.ALWAYS` 保证 null 更新不会被跳过。

---

## 9. 为什么 P2-T02 没有 V5 Migration

P2-T02 没有改变数据库 schema。

V4 已经建立：

```text
model_deployment.encrypted_credential TEXT NULL
```

因此本任务只增加：

```text
应用层加密 / 解密行为
```

不需要新增空洞 migration。

下一计划 migration 仍为：

```text
V5__add_application_default_deployment.sql
```

只有进入 P2-T03 后才实施。

---

## 10. UNIQUE / FK Strategy

主要 UNIQUE：

```text
team.name
employee.email
application.name
provider.name
model.name
model_deployment.name
```

数据库继续作为最终完整性边界。

Provider / Model 若被 ModelDeployment 引用，删除由 FK RESTRICT 阻止并映射为 409 RESOURCE_CONFLICT。

---

## 11. 删除策略

当前没有全局 Soft Delete。

- Provider / Model / Deployment 当前使用物理删除
- Provider / Model 被引用时禁止删除
- Deployment 使用 `enabled` 表达保留配置但禁止未来调用
- ApplicationApiKey 的 REVOKED 策略尚未实现

---

## 12. 时间与主键

Java 时间类型：

```text
Instant
```

数据库：

```text
TIMESTAMP
```

主键继续：

```text
BIGINT AUTO_INCREMENT
```

当前不引入 UUID / Snowflake。

---

## 13. 当前不做

```text
Multi-Tenant
Soft Delete Framework
分库分表
读写分离
Redis Runtime Snapshot
Vault / KMS 数据模型
Credential Key Ring
```

Master Key Rotation 属于后续真实需求驱动的架构演进。

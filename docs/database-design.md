# AIGate Database Design

## 1. 当前基线

**当前代码基线：Phase 3 / P3-T02 实现提交 `b790b2a`；V7 已提交代码，尚待实际 Maven 测试运行证据完成任务验收。**

数据库：**MySQL 8.4**

Flyway：

```text
V1 → team
V2 → employee
V3 → application
V4 → provider / model / model_deployment
V5 → application.default_deployment_id
V6 → application_api_key
V7 → invocation_record
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

Application 1 ---- N ApplicationApiKey
Application 1 ---- N InvocationRecord
```

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

`default_deployment_id` 使用：

```text
ON DELETE RESTRICT
```

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

---

## 5. application_api_key

P2-T04 新增。

```text
id BIGINT PK AUTO_INCREMENT
application_id BIGINT NOT NULL
name VARCHAR(100) NOT NULL
key_id VARCHAR(64) NOT NULL UNIQUE
key_prefix VARCHAR(100) NOT NULL
key_hash CHAR(64) NOT NULL
status VARCHAR(20) NOT NULL
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
revoked_at TIMESTAMP NULL
```

索引：

```text
UNIQUE(key_id)
INDEX(application_id)
```

外键：

```text
application_id
→ application.id
→ ON DELETE CASCADE
```

---

## 5.5 invocation_record

P3-T02 新增。

```text
id BIGINT PK AUTO_INCREMENT
application_id BIGINT NOT NULL
status VARCHAR(16) NOT NULL
error_code VARCHAR(64) NULL
duration_ms BIGINT NOT NULL
created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
```

CHECK 约束：

```text
status IN ('SUCCESS', 'FAILED')
duration_ms >= 0
```

索引：

```text
INDEX(application_id, id)   -- 复合索引，支持按 Application 分页查最新记录
```

外键：

```text
application_id
→ application.id
→ ON DELETE CASCADE
```

业务状态仅 `SUCCESS` 和 `FAILED`。`error_code` 仅在 `FAILED` 时有意义，但不提前增加数据库级的状态/错误码关联约束。

与 `application_api_key` 一致使用 `ON DELETE CASCADE`：InvocationRecord 是 Application 的调用历史从属记录，Application 删除后记录无独立业务价值。

---

## 6. API Key Storage Semantics

完整 Key：

```text
aig_live_<keyId>.<secret>
```

数据库不保存完整 Key，也不保存 secret 明文。

存储流程：

```text
secret
↓
SHA-256
↓
64-char lowercase hex
↓
key_hash CHAR(64)
```

`key_id` 是非秘密定位符，为 P2-T05 运行时认证的快速查询预留。

`key_prefix` 只用于管理展示，不具备认证能力。

---

## 7. API Key Lifecycle Data

当前 status：

```text
ACTIVE
REVOKED
```

创建：

```text
status = ACTIVE
revoked_at = NULL
```

撤销：

```text
status = REVOKED
revoked_at = current time
```

撤销不删除 row。

---

## 8. 为什么 ApplicationApiKey 使用 CASCADE

项目多数业务引用使用 RESTRICT，例如：

```text
Application.defaultDeploymentId -> ModelDeployment
ModelDeployment.providerId -> Provider
ModelDeployment.modelId -> Model
```

但 API Key 是 Application 的强生命周期从属凭证：

```text
Application 删除
→ Key 已无业务主体
→ 所有 Key 必须失效
```

因此这里使用：

```text
ON DELETE CASCADE
```

这是局部例外，不是全局删除策略变化。

---

## 9. 当前 UNIQUE / FK

主要 UNIQUE：

```text
team.name
employee.email
application.name
provider.name
model.name
model_deployment.name
application_api_key.key_id
```

主要 FK：

```text
employee.team_id → team.id RESTRICT
application.team_id → team.id RESTRICT
application.default_deployment_id → model_deployment.id RESTRICT
model_deployment.provider_id → provider.id RESTRICT
model_deployment.model_id → model.id RESTRICT
application_api_key.application_id → application.id CASCADE
invocation_record.application_id → application.id CASCADE
```

---

## 10. 当前不做

```text
API Key expiration column
automatic rotation metadata
last_used_at
Redis API Key cache
Multi-Tenant
Soft Delete Framework
分库分表
```

这些等真实需求出现后再设计。


---

## Phase 2 Closeout — 历史数据库基线（2026-10-18）

**Phase 2 关闭时的 Flyway 数据库版本为 V1–V6，不包含 InvocationRecord。**

当时已实现的主要关系：Application.defaultDeploymentId → ModelDeployment、ModelDeployment → Provider/Model，以及 Application → ApplicationApiKey。Runtime 通过默认部署查找 Provider；入站 API Key 哈希存储，上游 Credential 使用 AES-GCM 密文保存。

**V7 属于 Phase 3 的 P3-T02 实现，不得追溯记入 Phase 2。**

## Phase 3 / P3-T02 — 当前实现待执行验收

`V7__create_invocation_record.sql` 已提交代码，定义 invocation_record 与 Application 的 CASCADE 外键、SUCCESS/FAILED 和非负耗时 CHECK、复合索引 (application_id,id)。

Entity/Mapper 和 MySQL Testcontainers 数据库集成测试源码已提交；静态 Code Review 通过。

**当前仅待新的 Maven 实际执行结果**。之前 Phase 2 的 138 个测试通过记录不能替代 V7 后的回归测试。P3-T02 尚未标记 ACCEPTED，业务层仍未开始向本表写入 Invoke 历史；P3-T03/P3-T04 等任务未启动。

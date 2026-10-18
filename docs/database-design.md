# AIGate Database Design

## 1. 当前基线

当前内容以 **Phase 2 / P2-T04 完成后的实际实现** 为准。

数据库：**MySQL 8.4**

Flyway：

```text
V1 → team
V2 → employee
V3 → application
V4 → provider / model / model_deployment
V5 → application.default_deployment_id
V6 → application_api_key
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

## Phase 2 Closeout — Schema 与调用关系核对（2026-10-18）

当前 Flyway 数据库基线为 V1–V6：Team、Employee、Application、Provider、Model、ModelDeployment 和 ApplicationApiKey 等数据模型。运行时的核心外键链为 `application.default_deployment_id → model_deployment.id`，Deployment 关联 Provider 和逻辑 Model；API Key 记录关联 Application。

- `model_deployment` 保存 `endpoint_url`（API base URL）、`remote_model_name`、`encrypted_credential` 与 `enabled`；Credential 加密保存，只有真实出站调用时才解密。
- `application_api_key` 存 `key_id`、secret hash、状态等信息；明文 Secret 仅创建时展示，不可从数据库还原。新建 Key 不自动撤销旧 ACTIVE Key，显式撤销后 Runtime 认证拒绝。
- Application 默认部署可以更新，Runtime 每次根据当前 DB 配置解析，不需要 Redis 才能正确切换。
- 本阶段不新增业务 schema 迁移。以 Flyway SQL 文件和 Entity/Mapper 为实际字段与约束的最终核验来源；不要把计划中的 Key 自动轮换/过期、配额、用量统计写作现有表字段。

---

## Phase 3 — V7 Invocation Record 数据模型（**PLANNED / NOT IMPLEMENTED**）

**当前真实数据库仍为 Flyway V1–V6。P3-T02 才实现 V7。**

新表名：`invocation_record`。

| Column | Type | Constraints / Meaning |
|---|---|---|
| `id` | BIGINT | PRIMARY KEY AUTO_INCREMENT |
| `application_id` | BIGINT | NOT NULL, FK `application.id` ON DELETE CASCADE |
| `status` | VARCHAR(16) | NOT NULL, SUCCESS/FAILED |
| `error_code` | VARCHAR(64) | NULL；正常调用为空 |
| `duration_ms` | BIGINT | NOT NULL, >= 0 |
| `created_at` | TIMESTAMP(6) | NOT NULL, 记录时间（UTC 语义） |

- 复合索引：`INDEX idx_invocation_app_id (application_id, id)`（具体最终索引名称以 migration 为准）；面向按 Application 最新 1..100 条查询。
- 计划 FK 为 CASCADE，因为本表只是 Application 从属的排障记录，不是合规审计/计费历史。删除 Application 时不能被调用历史意外 RESTRICT。
- P3-T03 起才写真实成功记录；P3-T05 起才记录失败；P3-T06 完成 30 天保留/清理。
- **严禁敏感字段**：Prompt、回复正文、请求/响应原文、Client API Key、Provider Credential、原始上游错误/URL。
- 不引入 DeploymentId、Token Usage、Cost、Batch/Outbox 字段；这些都不属于本阶段最简可见性问题。
- 记录写入 best-effort，不改变模型调用结果；表内数据不能充当完整计费账本。

只有 P3-T02 实现代码并经总控验收后，才可将 V7 状态标记为 IMPLEMENTED。

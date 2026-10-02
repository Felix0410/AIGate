
# AIGate Database Design

## 1. 文档目的

本文档记录 AIGate 当前数据库设计。

当前目标：

- 描述现有表结构
- 固定表之间的关系
- 记录 UNIQUE / FK / INDEX 等约束
- 解释数据库层承担的数据完整性职责
- 为后续 migration 演进提供基线

当前内容以 Phase 1 实际实现为准。

---

## 2. 当前数据库

数据库：

**MySQL 8.4**

当前 Schema：

```text
aigate
```

数据库结构通过 Flyway 管理。

当前 migration：

```text
V1 → team
V2 → employee
V3 → application
```

原则：

> 已经执行过的 migration 不允许修改。

后续数据库变化必须新增 migration。

例如：

```text
V4__xxx.sql
V5__xxx.sql
```

---

## 3. 当前领域关系

Phase 1 当前关系：

```text
Team
├── Employee
└── Application
```

具体关系：

```text
Team 1 ---- N Employee
Team 1 ---- N Application
```

即：

- 一个 Team 可以有多个 Employee
- 一个 Team 可以有多个 Application
- 一个 Employee 必须属于一个 Team
- 一个 Application 必须属于一个 Team

---

## 4. team 表

表：

```text
team
```

字段：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Team ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | Team 名称 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

当前约束：

```text
PRIMARY KEY (id)
UNIQUE (name)
```

---

## 5. employee 表

表：

```text
employee
```

字段：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Employee ID |
| name | VARCHAR(100) | NOT NULL | 员工名称 |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 邮箱 |
| team_id | BIGINT | NOT NULL, FK | 所属 Team |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

当前约束：

```text
PRIMARY KEY (id)
UNIQUE (email)
FOREIGN KEY (team_id)
    REFERENCES team(id)
    ON DELETE RESTRICT
```

索引：

```text
idx_employee_team_id
```

---

## 6. application 表

表：

```text
application
```

字段：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Application ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | Application 名称 |
| team_id | BIGINT | NOT NULL, FK | 所属 Team |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

当前约束：

```text
PRIMARY KEY (id)
UNIQUE (name)
FOREIGN KEY (team_id)
    REFERENCES team(id)
    ON DELETE RESTRICT
```

索引：

```text
idx_application_team_id
```

---

## 7. 外键设计

当前两个外键：

```text
employee.team_id
→ team.id

application.team_id
→ team.id
```

数据库层使用真实 Foreign Key。

原因：

> Employee 和 Application 在 Phase 1 中都不能脱离 Team 独立存在。

因此数据库必须保证：

```text
不存在的 team_id
→ 无法写入

仍然被引用的 Team
→ 无法删除
```

---

## 8. ON DELETE RESTRICT

当前 FK 使用：

```text
ON DELETE RESTRICT
```

例如：

```text
Team 1
└── Employee 10
```

此时：

```text
DELETE Team 1
```

数据库会拒绝。


原因：

如果允许直接删除 Team，则可能出现：

```text
Employee.team_id
→ 指向不存在的 Team
```

或者被迫自动删除相关数据。

当前 Phase 1 不接受这种隐式副作用。

---

## 9. 为什么不用 ON DELETE CASCADE

当前没有采用：

```text
ON DELETE CASCADE
```

因为删除 Team 时自动删除：

```text
Employee
Application
```

风险过高。

Team 属于核心业务归属实体。

删除 Team 不应该自动大范围删除业务数据。

因此当前选择：

```text
RESTRICT
```

让应用显式处理冲突。

---

## 10. 为什么不用 SET NULL

当前没有采用：

```text
ON DELETE SET NULL
```

因为 Phase 1 业务规则明确：

```text
Employee 必须属于 Team
Application 必须属于 Team
```

所以：

```text
team_id
```

不能为 NULL。

---

## 11. UNIQUE 策略

当前唯一字段：

```text
team.name
employee.email
application.name
```

数据库层保留 UNIQUE。

应用层 Service 也会提前检查。

因此当前采用两层保护：

```text
Service
→ 提前发现业务冲突
→ 返回明确 Error Code

Database UNIQUE
→ 并发情况下最终保护数据完整性
```

例如：

```text
Request A
Request B
```

同时检查：

```text
email 不存在
```

两边都可能通过 Service 检查。

此时最终仍由数据库：

```text
UNIQUE(email)
```

阻止重复数据。

因此：

> Service 检查不能替代数据库 UNIQUE。

---

## 12. 索引设计

当前显式索引：

```text
employee.team_id
application.team_id
```

原因：

这两个字段是常见关系查询字段。

例如未来可能出现：

```text
查询某 Team 下所有 Employee
查询某 Team 下所有 Application
```

因此提前保留 FK 关联字段索引是合理的。

---

## 13. 时间字段

当前使用：

```text
created_at
updated_at
```

类型：

```text
TIMESTAMP
```

数据库负责：

```text
created_at
→ DEFAULT CURRENT_TIMESTAMP

updated_at
→ DEFAULT CURRENT_TIMESTAMP
→ ON UPDATE CURRENT_TIMESTAMP
```

Java 层对应：

```text
Instant
```

当前时间策略：

```text
数据库持久化统一 UTC 语义
应用层使用 Instant
```

避免在核心模型中混入本地时区语义。

---

## 14. 数据完整性职责

当前数据库负责最终保证：

```text
NOT NULL
UNIQUE
FOREIGN KEY
PRIMARY KEY
```

应用层负责：

```text
业务语义
友好错误码
预检查
```

两层职责不同。

例如：

```text
Employee email 重复
```

正常情况下：

```text
Service
→ EMAIL_ALREADY_EXISTS
```

并发冲突情况下：

```text
Database UNIQUE
→ DuplicateKeyException
→ RESOURCE_CONFLICT
```

---

## 15. 数据库异常与 API

数据库异常不会直接暴露给客户端。

当前转换关系：

```text
DuplicateKeyException
→ 409 RESOURCE_CONFLICT

DataIntegrityViolationException
→ 409 RESOURCE_CONFLICT
```

例如：

```text
删除仍被引用的 Team
```

数据库：

```text
FK violation
```

API：

```text
409 RESOURCE_CONFLICT
```

---

## 16. 当前不使用软删除

Phase 1 当前采用物理删除。

即：

```text
DELETE FROM ...
```

当前没有：

```text
deleted
deleted_at
logic delete
```

原因：

当前业务尚未出现：

- 数据恢复需求
- 审计保留要求
- 法规保留要求
- 历史查询需求

因此不提前引入软删除复杂度。

如果未来出现真实需求，再重新评估。

---

## 17. 当前不做多租户

当前数据库模型基于：

```text
Single Organization
```

所以目前没有：

```text
organization_id
tenant_id
```

未来如果引入 Multi-Tenant，需要重点重新评估：

```text
Team 唯一约束
Employee email 唯一约束
Application name 唯一约束
所有查询的数据隔离
索引设计
Security Context
```

这会属于重大架构变化，应记录 ADR。

---

## 18. 当前不使用 UUID

当前主键采用：

```text
BIGINT AUTO_INCREMENT
```

原因：

- 当前单体 + 单数据库
- 简单
- 索引友好
- 易于调试
- 尚未出现分布式 ID 需求

未来只有在出现：

```text
多数据库
分布式写入
离线 ID 生成
跨系统全局唯一 ID
```

等真实问题后，再考虑 UUID / Snowflake 等方案。

---

## 19. 当前不做分库分表

当前数据规模尚未形成分库分表问题。

因此当前：

```text
单 MySQL
单 Schema
```

足够。

不提前加入：

```text
ShardingSphere
分库
分表
读写分离
```

---


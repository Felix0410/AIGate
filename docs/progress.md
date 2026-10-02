# AIGate Progress

## 1. 当前项目状态

当前阶段：

**Phase 1 — Foundation & Core Identity**

状态：

**COMPLETED**

当前项目已经完成 AIGate 最基础的身份与归属模型：

```text
Team
├── Employee
└── Application
```

当前主流程已经打通：

```text
HTTP Request
↓
Spring Security
↓
Controller
↓
Validation
↓
Service
↓
MyBatis-Plus
↓
MySQL
```

---

## 2. Phase 1 Business Goal

Phase 1 的目标是建立 AIGate 的基础业务实体与工程骨架，使系统具备：

- Team 管理
- Employee 管理
- Application 管理
- Team 与 Employee / Application 的归属关系
- 基础参数校验
- 稳定 API Error Contract
- 最小 API 安全边界
- 数据库版本管理
- 自动化集成测试


---

## 3. Phase 1 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P1-T01 | Spring Boot 项目初始化 | DONE |
| P1-T02 | MySQL 开发环境 | DONE |
| P1-T03 | Flyway 数据库版本管理 | DONE |
| P1-T04 | Team 最小业务闭环 | DONE |
| P1-T05 | Employee CRUD + Team 关系 | DONE |
| P1-T06 | Application CRUD + Team 关系 | DONE |
| P1-T07 | Validation + Error Handling | DONE |
| P1-T08 | Minimal Spring Security | DONE |
| P1-T09 | Integration Test + Testcontainers | DONE |

---

## 4. 当前已完成能力

### 4.1 Team

已支持：

- 创建 Team
- 查询单个 Team
- 查询 Team 列表
- 更新 Team
- 删除 Team
- Team name 唯一性检查
- 删除被 Employee / Application 引用的 Team 时返回 409

---

### 4.2 Employee

已支持：

- 创建 Employee
- 查询单个 Employee
- 查询 Employee 列表
- 更新 Employee
- 删除 Employee
- Employee 必须关联已存在 Team
- email 唯一性检查

---

### 4.3 Application

已支持：

- 创建 Application
- 查询单个 Application
- 查询 Application 列表
- 更新 Application
- 删除 Application
- Application 必须关联已存在 Team
- Application name 唯一性检查

---

## 5. 数据库

当前数据库：

**MySQL 8.4**

当前 Flyway Migration：

```text
V1 → Team
V2 → Employee
V3 → Application
```

当前主要约束：

- Team name UNIQUE
- Employee email UNIQUE
- Application name UNIQUE
- Employee.team_id FK → Team.id
- Application.team_id FK → Team.id
- Team 删除使用 ON DELETE RESTRICT

---

## 6. API Error Contract

当前已经稳定的主要错误语义：

```text
400
VALIDATION_ERROR

401
UNAUTHORIZED

403
FORBIDDEN

404
TEAM_NOT_FOUND
EMPLOYEE_NOT_FOUND
APPLICATION_NOT_FOUND
RESOURCE_NOT_FOUND

409
TEAM_NAME_ALREADY_EXISTS
EMAIL_ALREADY_EXISTS
APPLICATION_NAME_ALREADY_EXISTS
RESOURCE_CONFLICT

500
INTERNAL_SERVER_ERROR
```

---

## 7. Security

当前安全方案：

```text
HTTP Basic
```

规则：

```text
/api/**
→ authenticated

/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
→ permitAll
```

当前账号通过环境变量提供。


---

## 8. Testing

当前集成测试使用：

- Spring Boot Test
- MockMvc
- Spring Security Test
- Testcontainers
- MySQL 8.4

当前测试可以在开发 MySQL 未启动的情况下独立运行：

```bash
./mvnw test
```

Testcontainers 会自动：

```text
启动临时 MySQL
↓
Spring Boot 连接临时数据库
↓
Flyway 自动执行
↓
运行集成测试
↓
测试结束后回收容器
```

---

## 9. 当前架构

当前架构：

**单体应用 + 模块化代码组织**

当前主要模块：

```text
team
employee
application
common
config
security
```


---

## 10. 当前技术债

以下问题已知存在，但暂不在 Phase 1 解决。

### TD-001 HTTP Basic 是临时安全方案

当前只作为 Phase 1 管理 API 的最小安全边界。

未来认证模型明确后重新设计。

### TD-002 暂无 Role / Permission

等真实授权需求出现后再实现。

### TD-003 Service 暂无统一事务设计

当前主要是单表写操作。

出现多表原子操作后再设计事务边界。

### TD-004 跨模块存在少量 Mapper 依赖

例如 Employee / Application 使用 TeamMapper 校验 Team。

当前保持简单。

### TD-005 Error Code 使用字符串

错误码规模扩大后考虑统一管理。

### TD-006 ApiErrorResponse 可观测性不足

当前缺少：

- traceId
- requestId
- path

后续 Observability 阶段补充。

### TD-007 当前唯一约束基于单 Organization 假设

未来引入 Organization / Multi-Tenant 时需要重新评估唯一约束范围。

### TD-008 当前测试覆盖关键链路，不追求完整覆盖率

后续随着业务复杂度增长逐步补充。

---

## 11. Phase 1 Acceptance Result

Phase 1 当前验收结果：

- [x] Spring Boot 项目可运行
- [x] MySQL 可连接
- [x] Flyway 可从空数据库初始化 schema
- [x] Team CRUD 可运行
- [x] Employee CRUD 可运行
- [x] Application CRUD 可运行
- [x] Team 关系约束有效
- [x] Validation 生效
- [x] 404 / 409 / 500 错误语义稳定
- [x] Spring Security 生效
- [x] Swagger / OpenAPI 可访问
- [x] 集成测试可独立运行
- [x] Testcontainers 使用真实 MySQL
- [x] 开发数据库停止时测试仍可运行
- [x] Phase 1 主业务闭环完成

---




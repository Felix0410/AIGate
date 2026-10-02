# AIGate Architecture

## 1. 文档目的

本文档记录 AIGate 当前实际架构，以及架构演进过程

---

## 2. 当前阶段

当前阶段：

**Phase 1 — Foundation & Core Identity**

Phase 1 的核心业务目标是建立 AIGate 最基础的身份与归属模型：

```text
Team
├── Employee
└── Application
```
Phase 1 暂不处理：

- AI Provider
- Model
- AI Proxy
- API Key
- JWT / OAuth / OIDC
- Redis
- MQ
- Elasticsearch
- 微服务
- 分布式事务
- 分布式锁

---

## 3. 当前系统形态

AIGate 当前采用：

**单体应用 + 模块化代码组织**

当前没有拆分微服务。

原因：

- 当前业务规模较小
- Team / Employee / Application 强相关
- 尚未出现独立扩缩容需求
- 尚未出现服务间通信问题
- 尚未出现分布式一致性问题

当前阶段优先保证：

- 业务闭环
- 数据一致性
- 可维护性
- 可测试性

---

## 4. 当前整体架构

```text
Client
  |
  v
Spring Security Filter Chain
  |
  v
Spring MVC Controller
  |
  v
Service
  |
  v
MyBatis-Plus Mapper
  |
  v
MySQL
```

辅助基础设施：

```text
Flyway
→ 数据库版本管理

Bean Validation
→ 请求参数校验

GlobalExceptionHandler
→ MVC 层统一错误响应

Spring Security
→ API 认证边界

Testcontainers
→ 集成测试真实 MySQL 环境

MockMvc
→ HTTP 层集成测试
```

---

## 5. 模块划分

当前主要业务模块：

```text
team
employee
application
```

每个模块基本采用：

```text
controller
dto
entity
mapper
service
```

当前没有额外引入：

- Repository 抽象层
- Domain Service
- Command / Query 分离
- DDD Aggregate
- Event Bus

原因是当前业务复杂度还不足以支撑这些抽象成本。

---

## 6. Team 模块

Team 是当前 Phase 1 的核心归属单位。

职责：

- 创建 Team
- 查询 Team
- 更新 Team
- 删除 Team
- 保证 Team name 唯一

当前唯一性策略：

```text
Service 预检查
→ 提供明确业务错误

Database UNIQUE
→ 并发情况下最终保证数据完整性
```

更新时会排除当前记录自身，避免：

```text
Team A 更新名字为原来的名字
→ 被误判为重复
```

---

## 7. Employee 模块

Employee 表示组织中的人员实体。

当前关系：

```text
Employee
  |
  v
Team
```

每个 Employee 必须属于一个已存在的 Team。

创建 / 更新 Employee 时：

```text
检查 Team 是否存在
↓
检查 email 是否可用
↓
写入数据库
```

Employee email 当前是全局唯一。

这是 Phase 1 “单组织”假设下的简化方案。


---

## 8. Application 模块

Application 表示未来调用 AIGate 的业务应用。

当前关系：

```text
Application
  |
  v
Team
```

每个 Application 必须属于一个已存在的 Team，后续会调整使用多Application的关系

Application name 当前全局唯一。

当前 Phase 1 暂未实现：

- API Key
- Application Credential
- Model Permission
- Quota
- Rate Limit

---

## 9. 数据库设计原则

当前数据库使用 MySQL 8.4。

Schema 使用 Flyway 管理。

当前 migration：

```text
V1 → Team
V2 → Employee
V3 → Application
```

已执行 migration 不允许修改。

后续数据库变化必须通过新的 migration 完成。

### 9.1 数据完整性

数据库负责最终数据完整性：

```text
UNIQUE
FOREIGN KEY
NOT NULL
```

并使用：

```text
ON DELETE RESTRICT
```

防止删除仍然被引用的 Team。

---

## 10. API 层

当前 API 使用 REST 风格。

主要资源：

```text
/api/teams
/api/employees
/api/applications
```

PUT 的语义：

```text
PUT
→ 完整更新所有可修改字段
```

当前不实现 PATCH。

---

## 11. Validation

请求参数使用 Jakarta Bean Validation。

例如：

```text
@NotBlank
@NotNull
@Email
@Size
```

执行链路：

```text
HTTP Request
↓
Controller
↓
@Valid
↓
Validation
↓
成功进入 Service
或者
抛出 MethodArgumentNotValidException
```

参数错误统一返回：

```text
400
VALIDATION_ERROR
```

---

## 12. 异常处理

MVC 层统一使用：

```text
GlobalExceptionHandler
```

当前 API 错误语义：

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

### 12.1 409 的两层保护

业务唯一性冲突：

```text
Service 预检查
↓
ConflictException
↓
明确业务错误码
```

数据库完整性冲突：

```text
UNIQUE / FK
↓
DataIntegrityViolationException / DuplicateKeyException
↓
RESOURCE_CONFLICT
```

目的：

- 正常业务路径提供更明确错误
- 并发情况下仍由数据库保证最终一致性

---

## 13. Spring Security

Phase 1 使用最小安全方案：

```text
HTTP Basic
```

保护范围：

```text
/api/**
→ authenticated
```

公开范围：

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

当前没有：

- Role
- Authority
- JWT
- OAuth2
- OIDC
- API Key

### 13.1 为什么当前使用 HTTP Basic

当前 Phase 1 只需要解决：

> 管理 API 不能完全匿名访问。

最终认证模型尚未确定。

因此选择 HTTP Basic：

- Spring 原生支持
- 配置简单
- 足以保护开发阶段管理接口
- 不会提前绑定最终身份模型

### 13.2 Employee 不等于登录账号

当前 Employee 是业务人员实体。

并没有把 Employee 直接设计成：

```text
User Account
```

原因是：

- Employee 是业务身份
- 登录身份是安全身份
- 两者未来可能不是一一对应

因此 Phase 1 不提前建立 User 表。

---

## 14. CSRF

当前 Spring Security 中关闭 CSRF。

原因：

当前 API：

```text
REST API
+
HTTP Basic
+
Apifox / API Client
```

不依赖浏览器 Cookie 自动携带 Session。

因此当前 CSRF 防护不会带来实际收益，反而会影响 POST / PUT / DELETE 调试。

如果未来改成：

```text
Browser
+
Cookie / Session Authentication
```

则需要重新评估 CSRF。

---

## 15. Security 错误响应

Spring Security 位于 Controller 之前：

```text
Security Filter Chain
↓
Spring MVC
```

所以认证 / 授权异常不会自然进入：

```text
GlobalExceptionHandler
```

当前使用：

```text
RestAuthenticationEntryPoint
→ 401

RestAccessDeniedHandler
→ 403
```

并使用 Jackson 3 `JsonMapper` 手动把 `ApiErrorResponse` 写入 HTTP Response。

---

## 16. Jackson

当前项目基于 Spring Boot 4。

Spring Boot 4 使用 Jackson 3。

因此当前使用：

```text
tools.jackson.databind.json.JsonMapper
```

用于：

- Security Handler 手动输出 JSON
- Integration Test Java Object → JSON
- Integration Test 读取响应 JSON

---

## 17. 集成测试架构

Phase 1 引入：

```text
Spring Boot Test
MockMvc
Testcontainers
MySQL 8.4
Spring Security Test
```

目标：

验证真实链路：

```text
MockMvc
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
Real MySQL
```

---

## 18. Testcontainers

测试不会连接开发数据库：

```text
localhost:3306/aigate
```

测试运行时：

```text
JUnit
↓
Testcontainers
↓
启动临时 MySQL 8.4
↓
DynamicPropertySource
↓
Spring Boot 连接临时 MySQL
↓
Flyway V1 / V2 / V3
↓
运行集成测试
```

整个测试 JVM 共享一个 MySQL Testcontainer。

测试结束后容器被回收。

---

## 19. 当前测试范围

Phase 1 不追求高覆盖率数字。

当前测试重点覆盖：

```text
Security
→ anonymous 401
→ correct Basic Auth success
→ wrong password 401

Team
→ create
→ validation 400
→ not found 404
→ duplicate 409
→ update same name
→ referenced Team delete conflict

Employee
→ create under existing Team
→ missing Team 404
→ duplicate email 409

Application
→ create under existing Team
→ missing Team 404
→ duplicate name 409
```


---

## 20. 当前架构演进过程

Phase 1 的真实演进：

```text
Spring Boot
+
MySQL

↓

Flyway
解决数据库版本管理

↓

Team / Employee / Application
建立核心业务模型

↓

Validation
解决非法请求输入

↓

Global Exception Handling
建立稳定 API Error Contract

↓

Spring Security
解决匿名访问问题

↓

Testcontainers
解决人工测试与本地数据库依赖问题
```


---

## 21. 当前技术债

### 21.1 HTTP Basic 是临时认证方案

未来认证模型明确后重新设计。

### 21.2 当前没有 Role / Permission

等出现真实授权需求再加入。

### 21.3 Service 没有统一事务设计

当前以单表操作为主。

当出现多个写操作必须原子完成时，再引入明确事务边界。

### 21.4 跨模块存在少量 Mapper 直接依赖

例如 Employee / Application 直接使用 TeamMapper 校验 Team。

当前保持简单。

如果跨模块规则变复杂，再考虑更清晰的领域边界。

### 21.5 错误码仍使用字符串

后续错误码规模扩大后可以统一管理。

### 21.6 ApiErrorResponse 缺少 traceId / path

后续可观测性阶段再补。

### 21.7 当前唯一约束基于单 Organization 假设

未来多租户设计可能需要调整。

### 21.8 测试只覆盖关键链路

当前不是完整回归测试体系。

---

## 22. 当前不做微服务

当前仍保持单体架构。

未来只有在出现真实问题时，才考虑拆分，例如：

- 独立扩缩容需求
- 明确团队边界
- 不同模块资源消耗差异巨大
- 发布节奏需要独立
- 单体耦合真正成为维护障碍

拆分前必须先记录 ADR。

---

## 23. Phase 1 架构结论

Phase 1 当前已经形成：

```text
可运行
+
有数据库版本管理
+
有业务闭环
+
有输入校验
+
有统一异常
+
有安全边界
+
有真实数据库集成测试
```

当前架构目标不是复杂，而是：

> 为后续 AIGate 的 AI Gateway 核心能力提供一个稳定、可理解、可演进的基础。

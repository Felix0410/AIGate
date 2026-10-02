
# AIGate Knowledge Map

## 1. 文档目的

本文档记录 AIGate 项目开发过程中实际使用和学习到的 Java 后端知识。

原则：

- 只记录项目中真正出现过的知识
- 每个知识点都尽量回答：
  - 它是什么
  - 它解决什么问题
  - AIGate 为什么需要它
  - 当前掌握到什么程度
  - 后续还需要学习什么

---

# 2. Phase 1 知识地图

Phase 1：

**Foundation & Core Identity**

核心业务：

```text
Team
├── Employee
└── Application
```

这一阶段主要涉及：

```text
Java
Spring Boot
Spring MVC
MyBatis-Plus
MySQL
Flyway
Validation
Exception Handling
Spring Security
Jackson
JUnit
MockMvc
Testcontainers
HTTP
```

---

# 3. Spring Boot

## 3.1 当前理解

Spring Boot 用于快速构建和启动 Spring 应用。

AIGate 当前使用 Spring Boot 4.1.1。

Spring Boot 主要帮助项目完成：

- 自动配置
- Dependency Management
- Embedded Web Application
- Spring Bean 初始化
- Configuration 管理
- Test Context

## 3.2 AIGate 中的作用

AIGate 当前所有核心组件都运行在 Spring Boot ApplicationContext 中：

```text
Controller
Service
Mapper
Security
Exception Handler
Flyway
```

## 3.3 当前掌握程度

已能够理解：

- `@SpringBootApplication`
- Spring Bean 基础
- Constructor Injection
- application.yaml
- Environment Variable
- Spring Boot Test

后续需要继续学习：

- Auto Configuration 原理
- Conditional Bean
- Bean Lifecycle
- Spring Boot Starter 原理
- ConfigurationProperties

---

# 4. Spring MVC

## 4.1 它解决什么问题

Spring MVC 负责：

```text
HTTP Request
↓
Controller
↓
Java Method
↓
HTTP Response
```

## 4.2 AIGate 中的使用

当前 Controller 使用：

```text
@RestController
@RequestMapping
@GetMapping
@PostMapping
@PutMapping
@DeleteMapping
@PathVariable
@RequestBody
```

## 4.3 当前理解

能够解释：

```text
客户端请求
↓
DispatcherServlet
↓
Controller
↓
Service
↓
Response
```

当前还需要继续深入：

- DispatcherServlet
- HandlerMapping
- HandlerAdapter
- HttpMessageConverter
- Argument Resolver
- MVC Exception Handling 内部流程

---

# 5. DTO 与 Entity

## 5.1 当前设计

AIGate 当前没有直接使用 Entity 作为 API Request / Response。

而是拆成：

```text
Request DTO
Entity
Response DTO
```

例如：

```text
CreateTeamRequest
↓
Team
↓
TeamResponse
```

## 5.2 为什么这样做

Entity 表示数据库持久化模型。

DTO 表示 API Contract。

这样可以避免：

- 数据库字段直接暴露给 API
- API 与数据库结构过度耦合
- 请求字段和响应字段无法独立变化

## 5.3 当前掌握程度

能够理解 DTO / Entity 分离目的。

后续需要继续学习：

- DTO Mapping 策略
- MapStruct 是否值得使用
- Domain Model 与 Persistence Model 分离

当前 AIGate 不需要引入 MapStruct。

---

# 6. Dependency Injection

## 6.1 当前使用

AIGate 使用 Constructor Injection：

```
@RequiredArgsConstructor
```

配合：

```java
private final TeamService teamService;
```

## 6.2 核心理解

对象不自己创建依赖：

```
new TeamService(...)
```

而是交给 Spring 容器管理。

主要收益：

- 降低对象之间创建耦合
- 更容易测试
- 生命周期统一交给容器管理

后续需要继续学习：

- IoC
- BeanFactory
- ApplicationContext
- Bean Scope
- 循环依赖

---

# 7. MyBatis-Plus

## 7.1 它是什么

MyBatis-Plus 是对 MyBatis 的增强工具。

它帮助 AIGate 减少基础 CRUD SQL 编写。

例如：

```text
insert
selectById
selectList
updateById
deleteById
selectCount
```

## 7.2 AIGate 为什么使用

当前 Phase 1 主要是简单 CRUD。

如果所有 CRUD 都手写 Mapper XML，会增加很多重复代码。

因此 MyBatis-Plus 当前比较适合。

## 7.3 当前掌握

已经使用：

```text
BaseMapper
LambdaQueryWrapper
selectCount
```

例如业务唯一性检查：

```text
WHERE email = ?
AND id != ?
```

后续需要学习：

- MyBatis 执行原理
- Mapper Proxy
- SQL Session
- 一级缓存 / 二级缓存
- MyBatis-Plus Wrapper 原理
- N+1 Query

---

# 8. MySQL

## 8.1 当前实际使用能力

当前已经使用：

```text
PRIMARY KEY
AUTO_INCREMENT
VARCHAR
TIMESTAMP
NOT NULL
UNIQUE
FOREIGN KEY
INDEX
ON DELETE RESTRICT
```

## 8.2 数据完整性

目前已经理解：

> 数据正确性不能只依赖 Java 代码。

例如：

```text
Service uniqueness check
+
Database UNIQUE
```

这是两层保护。

## 8.3 外键

当前：

```text
employee.team_id
→ team.id

application.team_id
→ team.id
```

通过 FK 保证关联数据真实存在。

## 8.4 后续学习

需要继续学习：

- InnoDB
- B+Tree Index
- 聚簇索引
- 回表
- 联合索引
- 最左前缀
- EXPLAIN
- MVCC
- Isolation Level
- Deadlock
- Gap Lock

这些暂时没有强行加入 Phase 1。

---

# 9. Flyway

## 9.1 它解决的问题

数据库结构本身也需要版本管理。

没有 Flyway 时：

```text
开发者 A 手工改数据库
开发者 B 不知道改过什么
测试环境结构不同
生产环境无法可靠升级
```

Flyway 将 schema 变化写成：

```text
V1
V2
V3
```

## 9.2 AIGate 当前规则

```text
V1 → Team
V2 → Employee
V3 → Application
```

已经执行的 migration 不修改。

数据库变化通过新增 migration 演进。

## 9.3 当前掌握

能够解释：

- 为什么数据库需要版本控制
- Migration 为什么不可随意修改
- 新环境如何通过 migration 重建 schema

后续需要继续学习：

- Flyway checksum
- baseline
- repair
- rollback 策略
- Production migration 风险控制

---

# 10. Bean Validation

## 10.1 当前使用

当前请求 DTO 使用：

```text
@NotBlank
@NotNull
@Email
@Size
```

Controller 使用：

```
@Valid
```

## 10.2 核心理解

Validation 负责：

> 判断 HTTP 请求格式和字段约束是否合法。

它不负责：

```text
Team 是否存在
email 是否已被占用
```

这些属于业务规则。

因此需要区分：

```text
Validation
vs
Business Validation
```

## 10.3 后续学习

- 自定义 Constraint
- Class Level Validation
- Validation Group

当前暂不需要。

---

# 11. Exception Handling

## 11.1 当前体系

当前主要异常：

```text
ResourceNotFoundException
ConflictException
```

Spring / DB 异常：

```text
MethodArgumentNotValidException
DuplicateKeyException
DataIntegrityViolationException
NoResourceFoundException
```

最终由：

```text
GlobalExceptionHandler
```

转成稳定 API Response。

## 11.2 当前理解

能够区分：

```text
400 → 请求非法
404 → 资源不存在
409 → 当前状态冲突
500 → 系统未知错误
```

尤其已经理解：

> 可预期的业务冲突不应该都变成 500。

## 11.3 后续学习

- Exception Translation
- Checked / Unchecked Exception
- Spring PersistenceExceptionTranslation
- Error Code 管理体系

---

# 12. HTTP

Phase 1 已实际使用：

```text
GET
POST
PUT
DELETE

200
400
401
403
404
409
500
```

## 12.1 PUT 与 PATCH

当前约定：

```text
PUT
→ 完整更新可修改字段

PATCH
→ 暂未实现
```

## 12.2 当前理解

已经开始区分：

```text
HTTP Status
vs
业务 Error Code
```

例如：

```text
409
+
EMAIL_ALREADY_EXISTS
```

HTTP Status 表示通用语义。

Error Code 表示 AIGate 的具体业务语义。

后续需要深入：

- Idempotency
- Cache Header
- ETag
- 201 Created
- 204 No Content
- REST Constraints

---

# 13. Spring Security

## 13.1 当前业务问题

最初：

```text
/api/**
→ 完全匿名
```

Phase 1 需要建立最小安全边界。

## 13.2 当前方案

```text
HTTP Basic
```

保护：

```text
/api/**
```

Swagger / OpenAPI 允许匿名访问。

## 13.3 当前掌握

已经理解：

```text
Authentication
= 你是谁

Authorization
= 你能做什么
```

当前只有 Authentication。

尚未真正引入 Authorization Role。

## 13.4 Filter Chain

已经理解请求顺序：

```text
HTTP Request
↓
Spring Security Filter Chain
↓
Spring MVC
```

因此 Security 的 401 / 403 不一定进入：

```text
GlobalExceptionHandler
```

需要：

```text
AuthenticationEntryPoint
AccessDeniedHandler
```

## 13.5 CSRF

当前已经理解：

CSRF 主要针对：

```text
浏览器
+
自动携带 Cookie
```

当前：

```text
REST API + HTTP Basic/API Client
```

所以 Phase 1 暂时关闭 CSRF。

后续需要继续学习：

- SecurityContext
- FilterChain 内部结构
- AuthenticationManager
- AuthenticationProvider
- PasswordEncoder
- Session
- JWT
- OAuth2 / OIDC

---

# 14. Jackson 3

## 14.1 当前背景

AIGate 使用 Spring Boot 4。

当前 JSON 库为 Jackson 3。

使用：

```text
tools.jackson.databind.json.JsonMapper
```

## 14.2 当前使用场景

Security：

```text
ApiErrorResponse
↓
JsonMapper
↓
HTTP JSON
```

Integration Test：

```text
Java DTO
↓
JsonMapper
↓
JSON Request Body
```

以及：

```text
JSON Response
↓
JsonMapper
↓
读取字段
```

## 14.3 关键理解

普通 Controller 返回对象时：

```text
Spring MVC
→ 自动完成 JSON 序列化
```

Security Handler 直接操作：

```text
HttpServletResponse
```

所以需要手工使用 JsonMapper。

---

# 15. Automated Testing

## 15.1 为什么引入

项目最初依赖 Apifox 手工测试。

随着：

```text
Validation
Security
404
409
Database
```

越来越多，手动回归成本开始增加。

因此 Phase 1 结束前引入自动化测试。

---

# 16. Unit Test vs Integration Test

## Unit Test

关注：

```text
单个 Class / Method
```

依赖通常 Mock。

优点：

- 快
- 定位问题容易

## Integration Test

关注：

```text
多个真实组件是否能一起工作
```

AIGate 当前重点使用 Integration Test。

---

# 17. MockMvc

## 17.1 它解决的问题

让测试可以从 HTTP 层模拟请求，而不直接调用 Controller / Service。

测试链：

```text
MockMvc
↓
Security
↓
Controller
↓
Validation
↓
Service
↓
Mapper
↓
MySQL
```

## 17.2 当前理解

MockMvc 可以看作：

> 自动化版 API Client。

但它运行在 Spring Test 环境里。

---

# 18. Testcontainers

## 18.1 为什么引入

测试如果直接使用本机 MySQL：

```text
依赖本地环境
旧数据污染
CI 难运行
```

如果使用 H2：

```text
不是真实 MySQL
SQL / 数据类型行为可能不同
```

所以当前使用：

```text
Testcontainers + MySQL 8.4
```

## 18.2 当前执行流程

```text
JUnit
↓
Testcontainers
↓
Docker MySQL
↓
DynamicPropertySource
↓
Spring Boot
↓
Flyway
↓
Integration Test
```

## 18.3 当前掌握

已经理解：

- 临时真实数据库
- 不依赖开发 MySQL
- 测试结束容器回收
- 测试能够重复运行

后续可以继续学习：

- Container lifecycle
- Reuse
- CI integration
- 多 Container 测试

---

# 19. DynamicPropertySource

解决的问题：

> Testcontainers 的 JDBC URL 在测试启动前无法固定写死。

因此：

```text
Container 启动
↓
获取 JDBC URL
↓
DynamicPropertySource
↓
覆盖 Spring datasource
```

同样也用于覆盖测试 Security Account。

---

# 20. 当前还没有学习 / 引入的技术

以下技术当前没有真实业务需求，因此没有引入：

```text
Redis
MQ
Kafka
RocketMQ
Elasticsearch
Nacos
Spring Cloud
Microservices
Distributed Lock
Distributed Transaction
Circuit Breaker
Rate Limit
JWT
OAuth2
Kubernetes
```

原则：

> 等项目出现对应真实问题后，再学习并引入。

---

# 21. Phase 1 当前掌握总结

Phase 1 结束后，应该能够独立解释：

- Spring Boot 项目基本结构
- Controller / Service / Mapper 分层
- DTO 与 Entity 区别
- MyBatis-Plus CRUD
- MySQL UNIQUE / FK / INDEX
- Flyway Migration
- Bean Validation
- Global Exception Handling
- HTTP 400 / 401 / 403 / 404 / 409 / 500
- Spring Security Filter Chain
- Authentication vs Authorization
- HTTP Basic
- CSRF 基础
- Jackson JSON 转换
- MockMvc
- Integration Test
- Testcontainers
- 测试数据库隔离

---

# 22. Phase 1 后续重点复习

优先级较高：

```text
1. Spring MVC 请求执行流程
2. Spring Security Filter Chain
3. MySQL 索引基础
4. 数据库事务
5. Spring @Transactional
6. MyBatis 执行原理
7. JVM / Java 基础继续加强
```


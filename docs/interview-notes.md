
# AIGate Interview Notes

## 1. 文档目的

本文档整理 AIGate 项目中可以用于 Java 后端实习 / 校招面试表达的内容。

目标不是背答案，而是形成自己的项目表达逻辑：

```text
业务背景
↓
遇到的问题
↓
当前方案
↓
为什么这样选
↓
方案带来的收益
↓
方案的限制
↓
未来如何演进
```

回答项目题时，尽量避免只说：

> “因为大家都这么用。”

应该说明：

> “AIGate 当时具体遇到了什么问题，所以选择了什么方案。”

---

# 2. 项目介绍怎么讲

## 面试表达

AIGate 是一个面向 AI 调用场景的后端网关项目。

当前 Phase 1 还没有直接接入 AI Provider，而是先建立基础身份和归属模型：

```text
Team
├── Employee
└── Application
```

目的是让后续系统能够知道：

- 谁在调用
- 哪个 Application 在调用
- 这个 Application 属于哪个 Team

Phase 1 同时建立了基础工程能力：

- Spring Boot
- MySQL
- Flyway
- MyBatis-Plus
- Validation
- 统一异常处理
- Spring Security
- Testcontainers 集成测试

当前架构保持单体，因为还没有出现真实的微服务拆分需求。

---

# 3. 为什么一开始不做微服务

## 面试官可能问

> 这个项目以后会比较复杂，为什么不一开始直接做微服务？

## 回答思路

因为当前 Phase 1 的业务规模很小，Team、Employee、Application 之间关系紧密。

如果一开始拆微服务，会立刻带来：

```text
服务通信
服务发现
配置中心
分布式事务
链路追踪
部署复杂度
```

但这些复杂度当时并没有解决真实问题。

所以当前采用：

```text
简单单体
↓
模块化代码组织
↓
真实问题出现后
↓
再决定是否拆服务
```

这种方式能保证架构演进是由业务问题驱动，而不是为了技术栈。

---

# 4. 为什么使用 Flyway

## 面试官可能问

> 为什么需要 Flyway？

## 回答思路

数据库结构也是代码的一部分，需要版本管理。

如果只靠手工改数据库，会出现：

```text
开发环境结构不一致
测试环境遗漏字段
不知道生产数据库执行过哪些变更
```

AIGate 使用 Flyway：

```text
V1 → Team
V2 → Employee
V3 → Application
```

新环境启动时可以自动按顺序执行 migration。

同时已经执行过的 migration 不再修改，而是通过新增版本继续演进。

---

# 5. 为什么数据库 UNIQUE 之外还要 Service 查重

## 面试官可能问

> 数据库已经有 UNIQUE，为什么 Service 还要查一次？

## 回答思路

两者职责不同。

Service 查重主要为了：

```text
提供友好的业务错误
```

例如：

```text
EMAIL_ALREADY_EXISTS
```

数据库 UNIQUE 负责：

```text
并发情况下最终保证数据完整性
```

因为两个并发请求可能同时查询到 email 不存在，然后都尝试插入。

所以当前采用：

```text
Service 预检查
→ 友好业务错误

Database UNIQUE
→ 最终完整性保护
```

不能因为 Service 做了检查就删除数据库 UNIQUE。

---

# 6. 为什么 Employee / Application 要用外键

## 面试官可能问

> 为什么不用纯应用层保证 teamId 正确？

## 回答思路

因为 Employee 和 Application 都必须属于真实存在的 Team。

如果完全依赖 Java 代码，绕过某条业务路径或者出现 bug 后，数据库可能保存脏数据。

所以数据库使用：

```text
employee.team_id → team.id
application.team_id → team.id
```

真实 Foreign Key 保证关联完整性。

应用层仍然提前检查 Team 是否存在，用于返回更明确的：

```text
TEAM_NOT_FOUND
```

数据库 FK 则是最后一道防线。

---

# 7. 为什么 Team 删除使用 RESTRICT

## 面试官可能问

> 为什么不用 ON DELETE CASCADE？

## 回答思路

如果 Team 删除自动级联删除：

```text
Employee
Application
```

风险太高。

Team 是核心归属实体，删除 Team 不应该隐式删除大量业务数据。

所以使用：

```text
ON DELETE RESTRICT
```

如果 Team 仍被引用，数据库拒绝删除。

应用再把这种完整性冲突转换成：

```text
409 RESOURCE_CONFLICT
```

这样数据更安全，业务行为也更明确。

---

# 8. 为什么用 DTO，不直接返回 Entity

## 面试官可能问

> 为什么不直接把 Entity 返回给前端？

## 回答思路

Entity 是数据库持久化模型。

DTO 是 API Contract。

如果直接暴露 Entity：

```text
数据库字段变化
↓
可能直接影响 API
```

而且请求字段、响应字段和数据库字段未来不一定一致。

所以当前拆成：

```text
Request DTO
Entity
Response DTO
```

这样 API 和数据库可以相对独立演进。

---

# 9. Validation 和业务校验有什么区别

## 回答思路

Validation 解决的是：

```text
请求格式是否合法
```

例如：

```text
@NotBlank
@Email
@NotNull
@Size
```

业务校验解决的是：

```text
请求虽然格式合法，但当前业务状态是否允许
```

例如：

```text
Team 是否存在
email 是否已经被占用
```

所以：

```text
email = "abc"
→ Validation 问题

email = "a@example.com"
但已经有人用了
→ Business Conflict
```

两者不能混在一起。

---

# 10. 为什么设计统一异常处理

## 面试官可能问

> 为什么不用 Controller 里 try-catch？

## 回答思路

如果每个 Controller 都处理：

```text
try
catch
return error
```

会出现大量重复代码，而且不同接口可能返回不同错误格式。

所以使用：

```text
@RestControllerAdvice
+
@ExceptionHandler
```

统一把异常转换成稳定 API Error Response。

例如：

```text
MethodArgumentNotValidException
→ 400 VALIDATION_ERROR

ResourceNotFoundException
→ 404

ConflictException
→ 409
```

这样 Controller 只负责正常业务流程。

---

# 11. 为什么 409 而不是 500

## 回答思路

500 表示服务器发生未预期内部错误。

但例如：

```text
重复 email
删除仍被引用的 Team
```

其实都是可以预期的业务冲突。

所以更合理的是：

```text
409 Conflict
```

当前 AIGate 把：

```text
重复资源
数据库完整性冲突
```

统一映射到 409。

这样客户端可以区分：

```text
业务状态冲突
vs
服务器真的出问题
```

---

# 12. 为什么 Spring Security 不直接上 JWT

## 面试官可能问

> 为什么认证不用 JWT？

## 回答思路

Phase 1 的真实问题只有：

> 管理 API 不能完全匿名。

但最终身份模型还没有确定。

例如未来可能有：

```text
后台管理员登录
Application API Key
第三方用户 OAuth
```

如果现在直接设计 JWT，很可能提前把 Employee、User Account、Application Credential 混在一起。

所以 Phase 1 只使用：

```text
HTTP Basic
```

作为最小安全边界。

它简单、Spring 原生支持，而且足够满足当前开发阶段。

等最终认证需求明确后，再设计 JWT / OIDC / API Key。

---

# 13. Authentication 和 Authorization 怎么解释

## 回答

```text
Authentication
→ 你是谁

Authorization
→ 你能做什么
```

AIGate Phase 1 当前只真正解决了：

```text
Authentication
```

即：

```text
/api/**
必须经过认证
```

当前还没有 Role / Permission，因此 Authorization 模型还没有正式建立。

---

# 14. 为什么 401 / 403 不直接走 GlobalExceptionHandler

## 回答思路

Spring Security 在 Spring MVC Controller 之前执行：

```text
HTTP Request
↓
Security Filter Chain
↓
DispatcherServlet / Controller
```

如果请求在 Security 阶段已经被拒绝，就不会进入 Controller。

因此：

```text
GlobalExceptionHandler
```

并不能统一处理所有 Security 异常。

所以使用：

```text
AuthenticationEntryPoint
→ 401

AccessDeniedHandler
→ 403
```

直接写 HTTP Response。

---

# 15. 为什么 Security Handler 里使用 JsonMapper

## 回答思路

普通 Controller：

```text
返回 Java Object
↓
Spring MVC
↓
自动序列化 JSON
```

但是 Security Handler 直接操作：

```text
HttpServletResponse
```

没有经过 Controller 的正常返回流程。

所以需要自己：

```text
ApiErrorResponse
↓
JsonMapper
↓
JSON
↓
response output stream
```

当前项目基于 Spring Boot 4，所以使用 Jackson 3 的：

```text
tools.jackson.databind.json.JsonMapper
```

---

# 16. 为什么关闭 CSRF

## 回答思路

CSRF 主要防的是：

```text
浏览器自动携带 Cookie / Session Credential
```

攻击者诱导浏览器发请求。

Phase 1 当前是：

```text
REST API
+
HTTP Basic
+
API Client
```

并不依赖 Cookie Session。

因此当前关闭 CSRF，可以减少 POST / PUT / DELETE 调试复杂度。

如果以后改成：

```text
Browser + Cookie Session
```

就需要重新评估并开启 CSRF 防护。

---

# 17. 为什么使用 Testcontainers

## 面试官可能问

> 为什么不用 H2？

## 回答思路

AIGate 最终数据库是 MySQL。

如果测试使用 H2，可能出现：

```text
测试通过
↓
真实 MySQL 行为不同
```

尤其在：

```text
SQL 方言
Timestamp
Foreign Key
Unique Constraint
Flyway Migration
```

等方面可能存在差异。

如果直接使用本地 MySQL，又会导致：

```text
依赖开发者环境
数据污染
CI 难运行
```

所以当前使用：

```text
Testcontainers + MySQL 8.4
```

测试时启动临时真实 MySQL。

既保证数据库行为真实，又保证测试环境独立。

---

# 18. 集成测试到底测了什么

## 回答思路

AIGate 的集成测试不是直接调用 Service。

而是：

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
真实 MySQL
```

因此能够同时验证：

- Security
- JSON
- Validation
- Exception Handler
- Service
- Mapper
- Flyway
- MySQL Constraint

这比单纯的 Service Unit Test 更适合 Phase 1 当前的核心目标。

---

# 19. 为什么测试不用开发数据库

## 回答思路

因为测试应该可重复。

如果使用开发数据库：

```text
之前的数据
测试执行顺序
开发者本机配置
```

都可能影响结果。

Testcontainers 每次提供临时数据库。

Spring 通过：

```text
@DynamicPropertySource
```

动态使用 Testcontainer 的 JDBC URL。

所以即使开发 MySQL 没启动：

```text
./mvnw test
```

仍然能运行。

---

# 20. 为什么现在没有大量 Unit Test

## 回答思路

Phase 1 的核心风险不是复杂算法，而是：

```text
Spring 配置
Security
Flyway
MyBatis
Database Constraint
HTTP Error Contract
```

所以当前优先使用少量高价值 Integration Test。

以后如果 Service 出现复杂：

```text
计费算法
限流算法
路由策略
Quota 计算
```

再补大量 Unit Test 会更有价值。

---

# 21. 为什么不用 Redis

## 回答思路

当前没有：

```text
缓存热点
分布式 Session
Rate Limit
分布式锁
高频计数
```

等问题。

如果只是为了让项目技术栈更丰富而加 Redis，会带来：

```text
缓存一致性
过期策略
网络调用
运维成本
测试复杂度
```

却没有解决真实问题。

所以 Phase 1 不使用 Redis。

未来出现真实问题后再引入。

---

# 22. 为什么不用 MQ

## 回答思路

当前业务请求基本都是：

```text
同步 CRUD
```

没有：

```text
异步削峰
事件广播
长耗时异步任务
服务解耦
```

需求。

因此 MQ 暂无价值。

未来例如出现：

```text
AI 请求日志异步处理
Usage Event
Billing Event
Audit Event
```

时，再评估 Kafka / RocketMQ。

---

# 23. 当前项目最大设计原则

如果面试官问：

> 你这个项目架构设计最重要的原则是什么？

可以回答：

> AIGate 没有一开始堆复杂技术，而是遵循“先解决业务问题，再根据真实问题演进架构”。Phase 1 先用单体把 Team、Employee、Application 主流程打通，然后随着真实问题出现，逐步加入 Flyway、Validation、统一异常、Spring Security 和 Testcontainers。每项技术都对应一个已经出现的问题，而不是为了丰富技术栈。

---

# 24. Phase 1 一分钟表达

可以压缩成：

> Phase 1 我主要完成了 AIGate 的基础身份模型，包含 Team、Employee 和 Application 三个核心实体，其中 Employee 和 Application 都归属于 Team。后端使用 Spring Boot、Spring MVC 和 MyBatis-Plus，MySQL 使用 Flyway 管理 schema，通过 UNIQUE 和 FK 保证最终数据完整性。API 层加入 Bean Validation 和统一异常处理，将参数错误、资源不存在和资源冲突分别映射成 400、404 和 409。安全方面当前只使用 HTTP Basic 建立最小访问边界，没有提前引入 JWT。最后使用 MockMvc + Testcontainers MySQL 建立集成测试，从 HTTP 请求一直覆盖到真实数据库，避免测试依赖本地 MySQL。

---

# 25. Phase 1 深挖方向

如果面试官继续深入，可以准备：

```text
Spring MVC 请求流程
Spring Security Filter Chain
数据库 UNIQUE 与并发
Foreign Key
MySQL Index
Flyway Migration
Spring Exception Translation
Testcontainers 生命周期
事务与 @Transactional
```

其中：

```text
事务
索引
Spring 内部执行流程
```

是后续需要继续加强的重点。


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


---

# Phase 2 — 可直接用于面试的项目表达

## 项目业务与架构（约 60 秒）

AIGate 当前是 Spring Boot 模块化单体 AI 模型网关。管理端配置 Provider、逻辑 Model、ModelDeployment 与 Application 默认部署，并为 Application 创建 API Key。运行时 Client 通过 `POST /v1/invoke` 使用 Bearer Key；Spring Security 验证后生成 ApplicationIdentity，ModelProxyService 读取默认部署并检查启用状态，由 Adapter 构造 OpenAI-Compatible 请求、RestClient 发送真实 HTTP，再将响应转换为稳定 InvokeResponse。失败时按来源区分本地配置、上游状态/超时、无效 JSON，映射统一 ApiErrorResponse，且不暴露上游凭证和原始错误。测试通过 MockMvc、Testcontainers MySQL 和本地 HTTP Mock Provider 覆盖整条链路。

## Q1 为什么区分 Provider、Model 和 ModelDeployment？

Provider 是服务提供方，Model 是逻辑模型记录，ModelDeployment 才是具体调用配置（Provider/Model 关联、endpointUrl、remoteModelName、可选加密凭证、enabled）。Application 绑定默认 Deployment，变更部署无需把 URL 和 Secret 硬编码进客户端或 Application。

## Q2 为什么 Application API Key 用 hash，Provider Credential 用 AES-GCM？

入站 Key 只需验证，无需还原，因此数据库保留 SHA-256 摘要并使用安全比较；上游 Provider Bearer 必须在调用时恢复明文，所以 AES-GCM 可逆加密。两类凭证绝不互传。SHA-256 在这里依赖高熵随机 Secret，不适合作为用户低熵密码的通用替代。

## Q3 为什么分两条 Spring SecurityFilterChain？

管理端 `/api/**` 暂用 HTTP Basic；运行面 `/v1/**` 接受 Application Bearer Key。Filter 认证后只保存 ApplicationIdentity，不携带原始 Key，防止将客户端凭证错误转发给 Provider。这是单应用内的安全边界，不必为了隔离立刻拆两个微服务。

## Q4 Adapter 与 RestClient 为什么分开？

Adapter 负责供应商协议的请求/响应转换（例如 `maxTokens` → `max_tokens`，`choices[0]` → `InvokeResponse`）；RestClient 负责 HTTP、Header、网络超时；Service 编排业务部署选择。协议变化、网络变化、业务变化分别修改各自边界，避免一个大类包揽全部逻辑。

## Q5 为什么同步调用、不自动重试？

目前仅有单次非流式请求，Spring MVC + RestClient 最简单。代价是 Tomcat 请求线程等待上游响应，所以设置连接/读取超时。上游超时后不能确定是否已执行/计费，自动重试可能重复消耗，因此先把安全错误返回客户端，等待明确重试语义再演进。

## Q6 Provider 返回 401 或 429，为什么不原样透传？

Provider 401 说明上游凭证问题，并非客户端 Application Key 错；映射为 502 PROVIDER_AUTH_FAILED。Provider 429 表示上游限流，映射 503 PROVIDER_RATE_LIMITED。超时映射 504，成功 HTTP 200 但协议错误映射 502。统一错误码避免调用方误判，也不泄露上游原始 Body。

## Q7 怎么验证不是假接口？

E2E 通过管理 API 建配置和 Key，MockMvc 走真实认证/Controller/Service/Mapper，Testcontainers 跑真实 MySQL，RestClient 通过 TCP 发到本地 JDK HttpServer。验证真实请求目标、凭证隔离、撤销 Key 后零出站、默认 Deployment 切换和错误映射。用户报告本地 Maven 138 项测试全通过；没有 CI 流水线验收，不能说 CI 已绿。

## Q8 为什么不用 Redis、MQ、微服务和熔断框架？

现阶段单模型同步调用在模块化单体中能形成完整闭环，尚无吞吐、跨服务一致性或异步削峰的真实问题。引入这些技术会增大部署、运维和故障模式复杂度。后续以业务指标和实际瓶颈推动演进，而不是为技术栈堆砌。

## 未完成事项如何诚实表达？

生产化之前仍需要管理面权限强化、Provider Endpoint SSRF/出站目标防护、CI 测试门禁，以及按需求补充追踪与主密钥轮换。当前不是生产级多租户高并发平台，不宣称具备尚未开发的限流/计费/流式/多模型路由。


# Phase 3 — 调用历史、失败隔离与数据生命周期（P3-T06 复盘）

**一句话业务目标**：在现有同步 AI 网关上，为管理员提供可按 Application 查询的近期 SUCCESS/FAILED 状态、稳定错误码及代理耗时，用于基础排障，而非准确计费或严格审计。

**Q1：为什么用 MySQL 而不是只有日志或直接上 MQ？** 已有 MySQL，可最小成本保存结构化元数据、提供管理 HTTP 查询；日志不便按业务主键稳定查询，MQ 增加 Broker、投递失败、重复消费和运维成本。当前同步写入允许失败丢记录（best-effort），延迟会有所增加；达到真实高吞吐瓶颈时再评估异步。

**Q2：怎样在模型失败时记录又不改变原有 HTTP？** RuntimeInvokeController 调用 InvocationTrackingFacade；ModelProxyService 正常返回记 SUCCESS，抛 RuntimeException 时按 ProxyErrorType/异常类别记 FAILED、安全固定 errorCode；写入异常由单独 try-catch 隔离，然后 `throw original`。保持 Provider 400/401/429/超时对应现有 AIGate 502/503/504 等契约，不记录 Prompt/密钥/上游错误体。

**Q3：为什么不能把记录写入放到代理调用的整个 try 内？** SUCCESS insert 失败若被外层 catch 捕获，会把真实成功误判为 FAILED，并可能将客户端响应从成功变成数据库 500；因此仅把 ModelProxyService 调用置于业务异常 catch 里，记录本身单独 best-effort。

**Q4：为什么查询最近 N 条而非分页？** 已确认需求是近期排障，不是完整历史漫游；`WHERE application_id=? ORDER BY id DESC LIMIT 1..100` 与已有 `(application_id,id)` 索引契合；有连续浏览历史需求时优先评估基于 id 的游标分页，不为未出现需求设计 OFFSET/总数。

**Q5：30 天清理为什么使用 UTC？** 规定 `Instant.now().minus(Duration.ofDays(30))`，SQL 严格 `created_at < cutoff`；每日至少尝试一次调度（UTC 03:00），并非精确满 30 天即时删除。MySQL TIMESTAMP 和 JDBC 时区必须用真实 MySQL 测试边界；大数据量时可能需要 created_at 索引、分批 DELETE。多实例 @Scheduled 可重复触发，当前单实例暂不引入分布式锁。

**Q6：如何证明业务闭环？** 通过 MockMvc 认证/Controller/Facade/Service/Mapper，Testcontainers MySQL，真正 TCP 本地 Mock Provider 观察模型成功/失败后数据落库，再用管理 Basic 查询双状态。单位测试 `assertSame` 验证原异常 identity，集成测试验证非法 API Key 和无效入参不记录，时间清理测试验证早于/等于/晚于 cutoff。完整 CI Gate 仍属 TD-009，不能谎称 CI 已通过。

**目前限制**：Best-effort 丢记录，未做费用/Token Usage、统计、Streaming、分布式锁或审计；HTTP Basic 管理认证、SSRF 出站限制、主密钥轮换、CI 属生产化技术债。Phase 3 Closeout 尚在进行，不宣称 CLOSED。


## Phase 3 阶段最终面试结论 — 2026-10-20

**Phase 3 CLOSED / ACCEPTED。** 可以围绕“为什么纯日志不足 → 用现有 MySQL 保存最少的调用元数据 → Facade 同步 best-effort 成功/失败 → 按 App 最新20条查询 → UTC30天保留 → MockMvc/Testcontainers/真实 Mock Provider 验证”讲清整个演进。必须承认局限：记录可能丢失、同步多一次 DB 写、清理大表缺 created_at 索引、多实例任务可能重复触发；不能把本记录表说成计费账本或严格审计。TD-001/009/010/011 仍开放。Git 提交不等于 CI 自动验证，P3-T02 的149项结果不是 P3-T06 的测试统计。

# AIGate Architecture

## 1. 当前阶段

**Phase 3 — Invocation Records & Basic Visibility（ACTIVE）**

以下为历史任务演进；当前状态以 `docs/current-phase.md` 为准。

历史任务执行状态：

```text
P2-T01 Model Registry Schema
→ COMPLETED
P2-T02 Provider Credential Protection
→ COMPLETED / ACCEPTED
P2-T03 Application Default Deployment
→ COMPLETED / ACCEPTED
P2-T04 Application API Key Lifecycle
→ COMPLETED / ACCEPTED
P2-T05 Runtime Authentication
→ COMPLETED / ACCEPTED
P2-T06 Unified Model Contract
→ COMPLETED / ACCEPTED
P2-T07 Provider Adapter
→ COMPLETED / ACCEPTED
P2-T08 RestClient + Single Model Proxy
→ COMPLETED / ACCEPTED
P2-T09 Provider Error Mapping
→ COMPLETED / ACCEPTED
P2-T10 End-to-End Integration Test
→ COMPLETED / ACCEPTED
P2-T11 Phase Closeout
→ COMPLETED / ACCEPTED
P3-T01 Business Goal & Scope Baseline
→ COMPLETED / ACCEPTED
P3-T02 Invocation Record Schema
→ COMPLETED / ACCEPTED
P3-T03 Successful Invocation Recording
→ COMPLETED / ACCEPTED
P3-T04 Management Invocation History Query
→ COMPLETED / ACCEPTED
P3-T05 Invocation Record Completion
→ 实现已提交，Code Review / 验收待完成
P3-T06 Phase 3 Closeout
→ NOT STARTED
```

---

## 2. 当前系统形态

AIGate 继续采用：**模块化单体 + 单 MySQL**。

当前没有拆微服务，也没有引入 Redis / MQ / Nacos / Spring Cloud。

---

## 3. 当前身份与模型关系

```text
Team
├── Employee
└── Application
      └── 1:N ApplicationApiKey

Provider ───┐
            ├── ModelDeployment
Model ──────┘

Application
  ↓ defaultDeploymentId
ModelDeployment
```

---

## 4. Management Plane 与 Runtime Plane

```text
Management
/api/**
→ HTTP Basic

Runtime
/v1/**
→ Application API Key
```

两者仍在一个 Spring Boot 进程中，但认证机制已隔离。

---

## 5. Runtime Authentication

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
lookup by keyId
↓
ACTIVE
↓
SHA-256 + MessageDigest.isEqual
↓
ApplicationIdentity
↓
SecurityContext
```

ApplicationIdentity：

```text
applicationId
applicationName
teamId
```

不携带任何 API Key secret/hash。

---

## 6. Unified Model Contract

P2-T06 新增：

```text
runtime.dto
├── InvokeRequest
├── InvokeMessage
├── MessageRole
└── InvokeResponse
```

Client 与 AIGate 的契约由 AIGate 自己控制，不直接复用 OpenAI / Provider SDK DTO。

---

## 7. Invoke Request Boundary

```text
InvokeRequest
├── messages
├── temperature?
└── maxTokens?

InvokeMessage
├── role
└── content
```

Role 仅允许：

```text
system
user
assistant
```

Validation：

```text
messages → non-empty + nested validation
role → non-null
content → non-blank
temperature → 0.0..2.0
maxTokens → > 0
```

Request 不包含：

```text
applicationId
providerId
modelId
deploymentId
```

Application 身份来自 SecurityContext；模型目标后续从 Application.defaultDeploymentId 获取。

---

## 8. Invoke Response Boundary

```text
InvokeResponse
├── content
├── model
└── finishReason
```

当前明确不加入：

```text
usage
cost
requestId
provider raw response
deploymentId
routing metadata
```

---

## 9. Client Contract 与 Provider Contract 分离

```text
Client
↓
AIGate InvokeRequest
↓
ProviderAdapter
↓
Provider-specific Request
↓
Provider
↓
Provider-specific Response
↓
ProviderAdapter
↓
AIGate InvokeResponse
```

P2-T06 完成 AIGate 侧统一 DTO；P2-T07 已实现 ProviderAdapter、ProviderAdapterRegistry 与 OpenAiCompatibleProviderAdapter，负责纯协议双向转换。P2-T08 才增加 RestClient 的 HTTP 出站调用。

---

## 10. Runtime Endpoint Wiring 状态

设计路径：

```text
POST /v1/invoke
```

**历史状态（P2-T06 时）：尚未创建生产 handler；当前已经实现。**

原因：

```text
ModelProxyService
RestClient
```

尚未具备。

为避免 hard-coded / echo / fake model result，P2-T06 定义了契约，P2-T07 完成纯协议转换；生产 HTTP 代理链待 P2-T08 打通。

因此当前真实状态：

```text
Runtime Authentication
→ DONE

Unified Contract
→ DONE

ProviderAdapter protocol conversion
→ DONE

Production /v1/invoke execution
→ DONE（P2-T08 已实现；P2-T09/T10 错误与 E2E 已验收）
```

P2-T08 已完成真实同步非流式调用主链，并经静态代码、测试覆盖验收通过；当前 P2-T09 处理统一失败语义。

---

## 11. Secret Strategies

Application API Key：

```text
high-entropy secret
→ SHA-256
→ verification only
```

Provider Credential：

```text
Provider Secret
→ AES-256-GCM
→ reversible for upstream call
```

---

## 12. Testing Architecture

P2-T06 Contract Test：

```text
Jackson 3
+
Jakarta Validation
↓
Invoke Contract
```

覆盖三种 role、unknown role 拒绝、messages / nested message 校验、temperature/maxTokens 边界、内部 selector 不暴露，以及 Response 最小字段。

---

## 13. Invocation Recording Architecture (Phase 3)

P3-T03/T05 实现调用记录的双路径写入。

```text
POST /v1/invoke
↓
RuntimeInvokeController
↓
InvocationTrackingFacade
  ├── try { modelProxyService.invoke() }
  │     ├── 正常返回 → saveBestEffort(SUCCESS, null)
  │     └── 抛出 RuntimeException
  │           ├── classifyErrorCode(exception)
  │           ├── saveBestEffort(FAILED, errorCode)
  │           └── throw original  ← 原异常对象透传
  ↓
return InvokeResponse (或 异常继续传播)
```

成功/失败路径使用相同的 `System.nanoTime()` 计时，saveBestEffort 的整个记录构造和写入过程包含在局部 try-catch(Exception) 内，记录失败只输出固定安全告警 `Invocation record persistence failed`，不影响 Provider 响应或原始异常。

异常分类：

| 异常 | errorCode |
|---|---|
| ModelInvocationException | exception.getErrorType().getCode() |
| InvalidProviderResponseException | PROVIDER_INVALID_RESPONSE |
| 其他 RuntimeException | INTERNAL_SERVER_ERROR |

**不记录：** exception message、cause、stacktrace、Provider 原始 HTTP 响应、Prompt、API Key、Credential。

---

## 14. 30 天调用历史清理

每天 UTC 03:00，`InvocationRecordRetentionScheduler` 自动执行：

```text
cutoff = Instant.now() - Duration.ofDays(30)
DELETE FROM invocation_record WHERE created_at < cutoff
```

- created_at 早于 cutoff → 删除。
- created_at 等于/晚于 cutoff → 保留。
- 清理失败只输出固定安全告警 `Invocation record retention failed`。
- 不影响 Runtime Controller 或模型代理。
- 当前采用单实例单条 DELETE 执行；不做分布式锁或分批。

清理只操作 invocation_record，不删除 Application、API Key、Team、Provider 或其他业务数据。

---

GitHub 当前仍无 CI Test Gate。

---

## 13. 当前架构演进

```text
Phase 1 Identity Foundation
↓
P2-T01 Model Registry
↓
P2-T02 Provider Credential Protection
↓
P2-T03 Application -> default ModelDeployment
↓
P2-T04 Application API Key Lifecycle
↓
P2-T05 Runtime Authentication
↓
P2-T06 Unified Model Contract
↓
P2-T07 Provider Adapter
```

当前任务：

```text
P2-T08 RestClient + Single Model Proxy
```

---

## 14. ADR

当前已有：

```text
ADR-001 Phase 1 Authentication Baseline
ADR-002 Application Runtime Authentication
```

P2-T06 与 P2-T07 均执行已冻结的架构设计，未发生新的重大决策，不新增 ADR。

---

## 15. 当前技术债

继续保留 TD-001 ~ TD-010。

**历史 P2-T06 记录：** 当时 Runtime 未具备真实模型调用；P2-T08 后已完成，当前不再是未完成功能。

没有为了 Contract 引入 Conversation persistence、Prompt aggregate、OpenAI SDK 或复杂领域层。


---

## 16. P2-T07 Provider Adapter — 已实现

实现提交：`14f0203d91dac394bc90c71bb8cf8bc1bccedbc7`。

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
OpenAiChatRequest / OpenAiChatMessage
InvalidProviderResponseException
```

- Registry 以 ProviderType 选择 Adapter，并阻止重复类型注册
- `InvokeRequest + remoteModelName` → 上游 `model/messages/temperature/max_tokens`
- 可选字段为 null 时不发送，不设置隐式模型参数默认值
- `choices[0]` 的 `message.content`、`finish_reason` 与 `model` → InvokeResponse
- 无效上游结构触发 InvalidProviderResponseException，不返回伪造结果
- 纯协议单元测试覆盖正常和异常情况；不含 HTTP、数据库与 Credential 操作

P2-T07 已通过代码与测试设计审查，**COMPLETED / ACCEPTED**。GitHub 未提供该实现提交的 CI status/workflow run，TD-009 保留。

## 17. P2-T08 RestClient + Single Model Proxy — 历史设计稿（现已完成）

### Business Goal

第一次打通单部署、同步非流式模型调用 Happy Path：

```text
Client POST /v1/invoke
→ Runtime API Key Authentication
→ ApplicationIdentity
→ Application.defaultDeploymentId
→ ModelDeployment.enabled
→ Provider.type
→ ProviderAdapterRegistry
→ CredentialService.decrypt（按需）
→ RestClient HTTP POST
→ OpenAI-Compatible response
→ ProviderAdapter.parseResponse
→ InvokeResponse
```

### 为什么用 RestClient

当前为 Spring MVC 同步非流式调用，RestClient 是 Spring 提供的同步 HTTP 客户端；当前不需要 WebFlux/响应流处理。代价是调用时占用 Servlet 请求线程，因此设置连接与读取超时，不自动重试。

### Responsibility

- Runtime Controller：`@Valid InvokeRequest` + `ApplicationIdentity`，不解析 API Key
- ModelProxyService：解析 Application / Deployment / Provider 并编排调用
- ProviderAdapter：继续只负责协议转换
- RestClient：只负责 HTTP 出站；发送独立 Provider Credential，不发送客户端 Application API Key

`model_deployment.endpoint_url` 作为 API base URL，例如 `http://mock/v1`；调用目标为 `{base}/chat/completions`。Credential 为空时不附加上游 Authorization。

### 阶段边界

本阶段只需正常 HTTP 200 Happy Path、运行配置基本拒绝与最小测试。Provider 4xx/429/5xx/timeout 统一错误映射留 P2-T09；完整 E2E 测试矩阵留 P2-T10。无 Retry、Fallback、Circuit Breaker、Routing、Redis、MQ、SSE。

P2-T08 未改变数据库 schema，Flyway 仍为 V1–V6。已验收完成并根据自动推进规则启动 P2-T09。


---

## 18. P2-T08 Runtime Proxy — 已实现并验收

实际新增组件：

```text
RuntimeInvokeController
ModelProxyService
ProviderHttpClient
RestClientConfig
ModelInvocationException
```

主流程：

```text
POST /v1/invoke + Application API Key
→ Spring Security Authentication
→ ApplicationIdentity
→ InvokeRequest @Valid
→ ModelProxyService
→ Application.defaultDeploymentId
→ ModelDeployment (enabled)
→ Provider.type
→ ProviderAdapterRegistry
→ ProviderAdapter.buildRequest
→ CredentialService.decrypt(encryptedCredential), if present
→ RestClient POST {endpointUrl}/chat/completions
→ Provider HTTP 200 body
→ ProviderAdapter.parseResponse
→ InvokeResponse
```

设计判断：

- Runtime Controller 只读取认证后的 ApplicationIdentity，不解析客户端 Secret
- ModelProxyService 负责业务编排；Adapter 负责 Provider 协议 JSON；ProviderHttpClient 负责 HTTP
- Provider Credential 是独立上游 Bearer；不会把客户端 Application API Key 转发上游
- Endpoint 作为 API base URL，追加 /chat/completions，不重复引入 /v1
- RestClient 同步非流式，连接超时默认 3 秒、读取超时默认 60 秒，均可配置
- 未引入异步执行、流式代理、自动重试、熔断、负载均衡等

测试：RuntimeInvokeIntegrationTest（MockMvc + MySQL Testcontainers + local HttpServer），ProviderHttpClientHttpTest（真实本地 HTTP），ModelProxyServiceTest（Mocked mappers/transport）。覆盖成功代理、鉴权、验证、Credential 有/无、disabled/缺省 Deployment 不出站及 URL 拼接。

P2-T08 **COMPLETED / ACCEPTED**。GitHub 未提供相关 commit 的 CI status/workflow run，所以只声明代码与测试覆盖验收，不声明 CI green。

### P2-T08 原始遗留项（已在 P2-T09 完成错误分类）

`ModelInvocationException` 在当前版本通过通用 Exception Handler 返回 500；需要把本地配置错误与上游 HTTP 错误分开分类，同时保证安全错误响应。

安全技术债：**TD-011 Provider Endpoint URL / SSRF Hardening**。数据库中配置的 endpointUrl 会触发服务端出站 HTTP；正式公网/多租户部署前必须按信任边界限制 URL scheme、内网/metadata 地址、重定向和 DNS 解析行为。暂不在 P2-T09 引入通用网络安全框架，保留本地 Mock Provider 测试能力。

## 19. P2-T09 Provider Error Mapping — COMPLETED / ACCEPTED

目标：在成功主流程之外，建立可信、准确、可预测的失败契约。

```text
ProviderHttpClient: HTTP statuses + timeout + network
ProviderAdapter: invalid success body
ModelProxyService: missing/disabled/invalid deployment
↓
Typed safe proxy exceptions
↓
GlobalExceptionHandler
↓
ApiErrorResponse(code, message, timestamp, errors)
```

上游失败可分类但不可泄露 raw body、credential、URL 或堆栈；Provider HTTP 401/403 不能错误映射为客户端 API Key 401，上游 429 也不能冒充平台自身 Quota 限流。

优先小型错误分类，不引入统一大异常框架；不重试、不 fallback、不路由。真实错误 Mock Provider 测试与既有成功路径回归是必要验收项。

P2-T09 已落地并验收；P2-T10 已启动，准备完整系统级测试验证。


---

## 20. P2-T09 Typed Provider Error Boundary — 已实现并验收

Commit: `0ea97870ffab56d19d31b6675605d363a1cd4224`

实际组件：

```text
ProxyErrorType (enum: error code / HTTP status / safe message)
ModelInvocationException(ProxyErrorType)
ProviderHttpClient.classifyResponseStatus(int)
ProviderHttpClient.classifyNetworkFailure(Throwable)
GlobalExceptionHandler typed proxy handlers
```

实际 Runtime 失败路径：

```text
Missing / disabled default Deployment
→ ModelProxyService
→ Typed ModelInvocationException

Upstream HTTP 400/401/403/429/5xx / network / timeout
→ ProviderHttpClient
→ Typed ModelInvocationException

Upstream HTTP 200 + malformed response
→ OpenAiCompatibleProviderAdapter
→ InvalidProviderResponseException

↓
GlobalExceptionHandler
↓
ApiErrorResponse(safe code/message/timestamp)
```

关键架构边界：

- 不直接透传 Provider 原始 HTTP 401/429/error Body
- Provider 401/403 → 502 PROVIDER_AUTH_FAILED；客户端认证仍单独使用 401 UNAUTHORIZED
- Provider 429 → 503 PROVIDER_RATE_LIMITED，不代表 AIGate 平台 Quota
- 超时 → 504 PROVIDER_TIMEOUT；连接故障 → 502 PROVIDER_CONNECTION_ERROR
- 无效成功 JSON → 502 PROVIDER_INVALID_RESPONSE
- 本地无默认部署/已禁用 → 409，对 Provider 零出站
- ExceptionHandler 不打印已分类异常堆栈或原始上游 HTTP 异常，外部使用固定脱敏文案

这一步采用小型 enum + typed exception + Handler，而不是通用错误中间件或 Resilience Framework。收益：分类集中、可测试、不暴露上游错误。代价：需要维护对外错误码表，新增 Provider 类型时检查映射语义。

测试代码有真实本地 HTTP 401/429/500、connection refused、read timeout、HTTP 200 无效结构，单元检查状态分类和零出站前置失败。审查未见阻塞，**P2-T09 DONE / ACCEPTED**。GitHub 没有 CI status/workflow 证据，因此不宣称 Maven 完整测试已运行。

## 21. P2-T10 System-level E2E Testing — COMPLETED / ACCEPTED

Business Goal: 将已有的局部 HTTP/MockMvc 测试整合为可复现的跨模块验证，覆盖管理配置 → 机器凭证签发/撤销 → Application 默认部署 → Spring Security → 出站 RestClient → Provider 正常/异常 → AIGate 返回。

测试架构保持：

```text
SpringBootTest + MockMvc + real SecurityFilterChain
↓
real Service / MyBatis-Plus / MySQL Testcontainers + Flyway
↓
JDK HttpServer (loopback random port, response programmable)
↓
RestClient / ProviderAdapter
```

不要用 Mockito 替换 E2E 中的核心业务 Service、HTTP client 或数据库；单元测试可继续使用 Mockito。

P2-T10 已补齐 **Key revoked/多 ACTIVE Key、默认 Deployment 切换、无 Credential、异常状态、真实超时、敏感数据/日志检查和测试资源清理** 等关键场景。用户提供本地 Maven 汇总：138 个测试，0 失败、0 错误、0 跳过；不是 GitHub CI 结果。

P2-T10 已完成且未新增 Redis、MQ、Circuit Breaker、Retry、Routing、微服务，也没有改变数据库 Schema。P2-T11 现已启动正式 Phase Closeout，必须完成测试/架构/技术债/文档/知识地图/面试表达的总检查后才能关闭 Phase 2。

**技术债延续：TD-001~TD-011。** 其中 TD-011 Provider Endpoint URL / SSRF hardening 仍需在对外生产暴露前落实；P2-T10 只测试当前单机可信管理配置下的设计，不代表通过了公网安全审计。


---

## 22. P2-T10 Final Acceptance and P2-T11 Architecture Review

P2-T10 代码范围：`2943dce28313742acc608fa11aaf962e23aae3c0`。

用户提交实际 Maven 测试汇总：

```text
Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

基于已完成代码审查和用户本地测试记录，P2-T10 **DONE / ACCEPTED**。GitHub 没有 CI 流水线运行记录，因此 TD-009 未关闭。

P2-T11 Phase Closeout **ACTIVE**。需从整个 Phase 2 审视：

- Management / Runtime 双安全边界：HTTP Basic 和 Application API Key 不能互代
- Credential 策略：Provider Secret 可逆 AES-GCM，Application API Key 高熵随机 Secret SHA-256 验证
- Runtime 代理分层：Controller → ModelProxyService → Adapter → ProviderHttpClient
- 错误边界：ProxyErrorType、HTTP 状态、脱敏内容、无重试
- E2E：MySQL Testcontainers + MockMvc + Mock Provider 的真实性和复现性
- 单体的适度模块化：当前不拆微服务，也不引入 Redis/MQ/复杂路由
- 风险与演进：TD-009 缺 CI，TD-011 出站 URL/SSRF 生产前硬化

本阶段 Closeout 必须同步 progress/roadmap、核心架构与数据库/API 文档、knowledge-map、interview-notes；若无重大新决策则不强行新增 ADR。

**当前 Phase 2 仍 ACTIVE / Closeout in progress，Phase 3 未启动。**


---

## 23. Phase 2 当前有效架构（2026-10-18，优先于上文历史设计段落）

**P2-T01～T10 已实现并验收；P2-T11 Closeout ACTIVE，Phase 3 NOT STARTED。** 以上以“进行中”“尚未实现”描述 P2-T06/P2-T08 的段落属于阶段历史，不是当前运行事实。

管理面 `/api/**` 通过 HTTP Basic 管理 Team/Application/Provider/Model/ModelDeployment/API Key；运行面 `/v1/**` 通过 Bearer Application Key → Spring SecurityFilterChain → ApplicationIdentity → RuntimeInvokeController → ModelProxyService（从 DB 读取 Application.defaultDeploymentId、启用的 ModelDeployment、Provider）→ ProviderAdapterRegistry/OpenAiCompatibleProviderAdapter → CredentialService（如有密文则 AES-GCM 解密）→ ProviderHttpClient/RestClient（同步非流式，3s connect、60s read 默认值）→ Provider → Adapter.parseResponse → InvokeResponse。

失败边界：ModelProxyService 检查本地配置，ProviderHttpClient 按 HTTP 状态/网络异常分类，Adapter 检查成功 JSON 结构，GlobalExceptionHandler 根据 ProxyErrorType 统一脱敏返回 ApiErrorResponse。没有自动重试、Fallback、Redis、MQ、Nacos、微服务、SSE 或复杂路由。

凭证分离：入站 Application Key 存高熵 Secret 的 SHA-256 hash，Provider Secret 存 AES-GCM 密文，只有出站才解密；客户端 Key 永不转发上游。E2E 通过 MockMvc + Testcontainers MySQL + 本地 JDK HTTP Provider；用户本地 Maven 汇总 138/0/0/0，仍无 GitHub CI Gate（TD-009）。

架构风险/处置门槛：生产化前必须审视 HTTP Basic 管理认证（TD-001）与 Provider Endpoint 的 SSRF 出站目标限制（TD-011）；其他 TD 依真实问题逐步解决。没有本轮新增重大架构决策，不需要为了收尾虚构 ADR。


---

## 24. Phase 2 Closed / Phase 3 Scope Baseline（2026-10-18）

**当前优先级最高的状态说明：** Phase 2 已完成 P2-T11 全面收尾，**CLOSED / ACCEPTED**；关闭审查及保留的生产风险见 `docs/phase-2-closeout.md`。以上各节历史“ACTIVE/NOT STARTED”状态表示当时阶段记录，不是现在执行任务。

**Phase 3 当前：P3-T01 ACTIVE（只定义业务目标与最小范围）**。现有真实代码保持模块化单体、Spring MVC + RestClient、MySQL V1–V6、两条 Spring SecurityFilterChain、ProviderAdapter + 安全 ProxyErrorType。尚未开发新的 InvocationRecord、Usage、Quota 或其他 P3 业务代码。

首选需要验证的下一个业务缺口是：现有 Runtime 能调用，但没有可查询的每次调用记录/失败类别/耗时。P3-T01 将评估现有日志是否足够，以及结构化日志或 MySQL 少量元数据的成本；**尚未做存储方案或 ADR 决策**。任何持久化设计都应避免 Prompt、模型响应原文及凭证明文默认落库。

TD-001 管理认证、TD-009 CI Gate、TD-010 Master Key Rotation、TD-011 SSRF 网络目标限制仍需根据实际发布风险处理。Phase 2 测试证据为用户本地 138/0/0/0，不是 CI。

---

## 25. Phase 3 已冻结架构决策（2026-10-18，PLANNED）

**阶段：Phase 3 — Invocation Records & Basic Visibility。P3-T01 DONE；P3-T02 ACTIVE。**
此节是已经批准但尚未实现的架构；现有生产代码依然是 Phase 2 的单模型同步代理主流程。

### 当前业务问题

Runtime 可以代理模型、返回结果/分类错误，但管理端不能按 Application 稳定查询最近调用历史、失败类别和耗时。

### 目标架构

```text
/v1/invoke → P2 Runtime Security + @Valid
          → P3 InvocationTrackingFacade [P3-T03 planned]
                  ├── P2 ModelProxyService → Adapter/RestClient → Provider
                  └── P3 InvocationRecordService/Mapper → MySQL V7 [planned]

/api/applications/{id}/invocations [P3-T04 planned]
          → HTTP Basic → Invocation query service
          → MySQL WHERE application_id = ? ORDER BY id DESC LIMIT ?
```

- 记录入口在已有 Runtime Controller/ModelProxyService **边界**，不要侵入 ProviderAdapter、ProviderHttpClient 的协议/网络职责。
- 只有成功认证、参数校验通过并进入代理业务后才记录。
- 正常调用返回成功，记录失败仅作无敏感内容的告警；不更改响应。
- 代理失败按稳定安全 errorCode 记 FAILED，不能吞掉/改变原始异常。
- 当前不宣称 Exactly-once、严格审计、准确计费或分布式链路追踪。
- 保存 applicationId/status/errorCode/durationMs/createdAt，禁止保存 Prompt、模型回答、API Key、Provider Secret、原始错误、Provider URL。
- 查询默认 limit=20，允许 1..100；30 天保留（P3-T06 后落地）。
- 不新增 Redis/MQ/ES/微服务。按真实写库延迟、吞吐再演进。

### 备选方案与权衡

单纯日志难以提供稳定的管理 API；结构化日志需要新的检索与保留基础设施；MySQL 已存在、开发/测试成本最低；MQ 适合将来吞吐与可靠异步写入问题，但现在增大 Broker、投递、幂等与运维复杂度。

决策文件：`docs/adr/ADR-003-invocation-recording-persistence.md`。


---

## 26. P3-T02 Accepted / P3-T03 Success Tracking Design

Phase 3 的 V7 数据基础已实现：invocation_record 表、InvocationRecord/InvocationStatus/Mapper、CHECK/FK CASCADE/(application_id,id) 复合索引。用户本地 Maven 149 项测试无失败/错误/跳过，BUILD SUCCESS；不是 GitHub CI 结果。

P3-T03 ACTIVE：只接成功调用记录，使用显式 InvocationTrackingFacade 包装现有 ModelProxyService，获取认证后的 applicationId。通过 System.nanoTime 量取模型代理耗时，调用成功后同步 best-effort 保存 SUCCESS/errorCode NULL/durationMs/createdAt。写入异常只生成安全固定告警，不改变真实 Provider 返回；不要给包含 HTTP 的外层调用加 @Transactional。ModelProxyService、Adapter、ProviderHttpClient 保持现有职责。失败记录交给 P3-T05，查询交给 P3-T04，保留清理交给 P3-T06。

这是 P3-T03 的已批准设计，不是当前已实现的自动成功记录功能。决策沿用 ADR-003，不需要重复新增 ADR。


---

## 27. P3-T03 实现验收 / P3-T04 管理查询设计（2026-10-18）

P3-T03 提交 `ee38c95f20781de6aa219ea80b99ab590e7016bb` 已通过 Code Review，现有 `RuntimeInvokeController` 委托 `InvocationTrackingFacade`，Facade 调用原 `ModelProxyService`。正常响应时记录 SUCCESS，非负 durationMs 用 System.nanoTime 计算，createdAt 用 Instant，Mapper 写入异常被安全固定 warn 隔离且不影响原 InvokeResponse；上游错误继续原样映射，FAILED 延至 P3-T05。

**P3-T03 DONE / ACCEPTED，P3-T04 ACTIVE**。GitHub 无关联 CI/workflow，测试源码已审阅，没有独立的这次 Maven 汇总。

P3-T04 规划链路：Management HTTP Basic → InvocationQueryController → InvocationQueryService → ApplicationService.getApplicationById（404）→ InvocationRecordMapper（按 application_id 过滤、id 降序、LIMIT 1..100）→ 不暴露敏感数据的六字段 DTO 数组。

采用已有 V7 复合索引 `(application_id,id)`，不新建表或缓存。HTTP 400 INVALID_REQUEST 包括 limit 数字转换异常与超界；200 [] 表示 Application 存在但记录为空；401 表示管理认证失败。查询保持数据库限量，不全表读取再内存截断。管理查询与 Runtime Bearer Key 认证边界独立。

---

## 29. P3-T05 当前代码事实及待验收项（2026-10-18）

实现提交 `8262303df74598f6e76e68ad1f10ce40b096e2c6`：InvocationTrackingFacade 已支持 SUCCESS 与 FAILED 双路径 best-effort 写入；ModelInvocationException 使用既有 ProxyErrorType，InvalidProviderResponseException 写固定 PROVIDER_INVALID_RESPONSE，其他 RuntimeException 写 INTERNAL_SERVER_ERROR；FAILED 记录后 `throw original` 保留原异常。入站认证失败和 @Valid 失败不进入记录流程。durationMs 自 Facade 调用 ModelProxyService 前计时至其正常返回/抛异常，不包含记录保存耗时。

InvocationRecordRetentionScheduler 使用 `@Scheduled(cron="0 0 3 * * *", zone="UTC")` 每日触发，删除早于 `Instant.now().minus(Duration.ofDays(30))` 的记录，等于边界的记录保留；清理失败只生成固定安全警告。当前为单实例、单条 DELETE；缺少 created_at 索引，未来真实数据规模扩大时评估批量删除和单列时间索引。

状态：P3-T01～T04 DONE / ACCEPTED；P3-T05 功能已提交但仍为 ACTIVE，等待 Review 问题处理和总控验收；P3-T06 仅用于 Phase 3 Closeout，不提前启动。GitHub 无独立 CI 验证，依用户提交前测试通过的约定确认本地测试。

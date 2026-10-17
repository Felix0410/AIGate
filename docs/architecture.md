# AIGate Architecture

## 1. 当前阶段

**Phase 2 — Model Registry & Single Model Proxy**

当前执行状态：

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
→ ACTIVE
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

当前尚未创建生产 handler。

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
→ NOT WIRED
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

当前 Runtime 仍未具备真实模型调用；这是任务顺序中的未完成功能，不记为技术债。

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

## 17. P2-T08 RestClient + Single Model Proxy — 进行中

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

### Remaining shortcomings entering P2-T09

`ModelInvocationException` 在当前版本通过通用 Exception Handler 返回 500；需要把本地配置错误与上游 HTTP 错误分开分类，同时保证安全错误响应。

安全技术债：**TD-011 Provider Endpoint URL / SSRF Hardening**。数据库中配置的 endpointUrl 会触发服务端出站 HTTP；正式公网/多租户部署前必须按信任边界限制 URL scheme、内网/metadata 地址、重定向和 DNS 解析行为。暂不在 P2-T09 引入通用网络安全框架，保留本地 Mock Provider 测试能力。

## 19. P2-T09 Provider Error Mapping — ACTIVE

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

P2-T10 仍未启动。

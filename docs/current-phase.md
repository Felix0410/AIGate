# AIGate Current Phase

> 共享执行基线：实际代码与验收结论为事实来源。本文件用于总控、开发导师和 Code Review。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T08 — RestClient + Single Model Proxy（ACTIVE）**

Next Planned Task: **P2-T09 — Provider Error Mapping（NOT STARTED）**

> P2-T07 已验收通过并按 2026-10-17 的自动推进规则完成收尾，直接启动 P2-T08。P2-T08 完成并验收通过后继续自动推进 P2-T09；如果发现阻塞问题，先修复并复验。

## 2. Phase 2 Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | DONE |
| P2-T07 | Provider Adapter | **DONE / ACCEPTED** |
| P2-T08 | RestClient + Single Model Proxy | **ACTIVE** |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T07 Acceptance — DONE / ACCEPTED

实现提交：`14f0203d91dac394bc90c71bb8cf8bc1bccedbc7`。

已实现：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
OpenAiChatRequest / OpenAiChatMessage
InvalidProviderResponseException
```

转换方向：

```text
InvokeRequest + remoteModelName
→ ProviderAdapterRegistry.select(ProviderType.OPENAI_COMPATIBLE)
→ OpenAiCompatibleProviderAdapter.buildRequest
→ OpenAiChatRequest JSON

Provider successful JSON
→ OpenAiCompatibleProviderAdapter.parseResponse
→ InvokeResponse(content, model, finishReason)
```

已验证：

- model 来自 remoteModelName
- 消息顺序和小写 role 保留
- temperature / maxTokens → temperature / max_tokens
- null 可选字段从 Provider 请求 JSON 省略
- 200 成功响应读取 choices[0]
- 无效 JSON、空 choices、缺失关键字段不伪造成功
- Registry 对缺失类型及重复注册明确失败
- Adapter 只负责协议转换，不涉及 HTTP / DB / Secret
- 单元测试不依赖外部 Provider

GitHub commit 未提供 CI check 或 workflow run，不宣称 CI 已执行通过，继续保留 TD-009。

结论：**P2-T07 COMPLETED / ACCEPTED**，无阻塞问题。

---

## 4. P2-T08 — Business Goal

> 让持有 ACTIVE Application API Key 的真实客户端，第一次能够经由 AIGate 调用其默认 ModelDeployment，并得到真实 Provider 的非流式模型响应，打通完整 Happy Path。

最终目标：

```text
Client
→ POST /v1/invoke + Bearer Application API Key
→ Runtime SecurityFilterChain
→ ApplicationIdentity
→ Runtime Controller (@Valid InvokeRequest)
→ ModelProxyService
→ Application.defaultDeploymentId
→ ModelDeployment (enabled)
→ Provider (type)
→ ProviderAdapterRegistry / Adapter
→ CredentialService.decrypt
→ RestClient POST to OpenAI-Compatible endpoint
→ Provider response
→ Adapter.parseResponse
→ InvokeResponse JSON
```

这一任务是 Phase 2 首次真正的业务闭环。优先打通 Happy Path，不把重试、熔断、路由、限流等复杂度混进来。

## 5. 为什么选择 RestClient

RestClient 是 Spring 的同步 HTTP 客户端，适合当前 Spring MVC 同步、单次、非流式的 outbound 调用。

当前选择原因：

- 业务只需要一次同步 Chat Completions 请求/响应
- 与已有 Spring MVC 技术栈一致
- 比手写 HttpClient / 直接操作连接更简单
- WebClient / WebFlux 不是当前业务必需
- 不需要引入 Feign / 服务发现 / 微服务依赖

代价：请求处理线程在上游响应前会阻塞；必须设置明确的连接与响应读取超时。等真实并发问题出现后再评估其他模型。

## 6. P2-T08 Minimal Components

建议最小新增：

```text
RuntimeInvokeController
ModelProxyService
RestClient configuration / small outbound component
```

责任边界：

- Controller：`POST /v1/invoke`，`@Valid @RequestBody InvokeRequest`，从 `Authentication.getPrincipal()` 获取 `ApplicationIdentity`，不解析 API Key
- ModelProxyService：编排 Application、Deployment、Provider、凭证、Adapter、HTTP 调用
- Adapter：继续只负责构造 Provider 请求和解析 Provider 成功响应
- RestClient：只负责发送 HTTP、接收响应，不负责决定调用哪个 Deployment
- CredentialService：现有 AES-GCM 解密能力复用，绝不回显或日志打印 Secret

不提前创建复杂的 Router、Provider SDK、Executor hierarchy、通用 Middleware Pipeline。

## 7. Deployment Resolution

```text
ApplicationIdentity.applicationId
↓
ApplicationMapper.selectById
↓
Application.defaultDeploymentId
↓
ModelDeploymentMapper.selectById
↓
ModelDeployment.enabled == true
↓
ProviderMapper.selectById
↓
Provider.type
↓
ProviderAdapterRegistry.select(...)
```

当前一个 Application 最多一个默认 Deployment；不根据 Client 输入选择模型。

若 Application 没有默认 Deployment、目标被禁用、引用数据异常等，在本任务中**至少明确拒绝调用上游**，不得静默 fallback 到任意模型或给出假成功响应。可使用最小明确的业务异常；跨 Provider 的统一错误体系留 P2-T09。

## 8. Upstream Endpoint / Credential Contract

当前 `model_deployment.endpoint_url` 视为 **Provider 的 API base URL**，例如：

```text
http://mock-provider:8080/v1
https://provider.example.com/v1
```

OpenAI-Compatible Chat Completions 当前请求目标：

```text
{endpointUrl 去掉末尾斜杠}/chat/completions
```

约定应形成可测试的单一行为，避免重复 `/v1` 或拼错路径。

HTTP：

```text
POST /chat/completions
Content-Type: application/json
```

Provider credential：

```text
encryptedCredential != null
→ CredentialService.decrypt
→ Authorization: Bearer <provider secret>

encryptedCredential == null
→ 不附加 Provider Authorization header
```

注意：**Application API Key 与 Provider Credential 完全分离**。客户端的 Bearer API Key 不得转发给上游。绝不把 Provider Secret、Authorization 或响应中的敏感内容写进日志。

Model 来自 `ModelDeployment.remoteModelName`，不是 Runtime 请求字段。

## 9. HTTP Boundaries

最简单可运行方案：

- Spring MVC + RestClient 同步非流式
- 配置连接超时与响应读取超时（有界、可解释、可测试）
- 不自动重试
- 只支持 `OPENAI_COMPATIBLE`
- 不实现 SSE / streaming
- 不重新编写 Adapter 的 JSON mapping
- 不把数据库 Entity 直接暴露为 Runtime HTTP 响应

**P2-T09 专门处理** Provider 4xx/5xx、429、timeout、响应结构异常到 AIGate 标准 HTTP Error 的映射。P2-T08 应避免泄露上游原始凭证或错误 Body，暂不追求完整错误分类。

## 10. Acceptance Criteria

P2-T08 验收时至少确认：

- 生产 `POST /v1/invoke` Controller 实际存在、可启动
- 无 API Key → 401，ACTIVE API Key 可访问
- `@Valid` 对空 messages、空内容、非法 temperature/maxTokens 生效，非法 role 返回 400
- Application 身份只来自 SecurityContext，Client 不传 applicationId
- App 默认 Deployment 被正确加载；ModelDeployment 必须 enabled
- 没绑定或 disabled 时不发送上游 HTTP 请求
- Provider Type 通过 Registry 选 Adapter；`remoteModelName` 被正确发送
- OpenAI-Compatible POST 路径、Content-Type、JSON 字段正确
- Provider Credential 存储密文经现有 CredentialService 解密后，使用独立 Bearer Header 发上游
- 没有 Credential 的 Deployment 不附加 Provider Authorization
- 上游真实 HTTP 200 → Adapter.parseResponse → 真实 InvokeResponse JSON
- 可以用本地 Mock Provider / 测试 HTTP Server 验证 Happy Path，不能返回硬编码假结果
- timeout 已配置，异常与日志不暴露 secrets
- 未引入 Retry、Fallback、Circuit Breaker、Routing、Redis、MQ、WebFlux

建议此阶段至少写一条**可复现的真实 HTTP Happy Path 测试**，证明出站 HTTP 真的发生；P2-T10 再做完整 Testcontainers + Mock Provider 的跨模块端到端测试矩阵。

## 11. Scope Boundaries

P2-T08 本阶段不做：

```text
Provider 4xx/5xx/429/timeout 完整标准化映射（P2-T09）
完整 E2E failure matrix（P2-T10）
Route / ModelAlias / Weighted Routing
Rate Limit / Quota / Usage Ledger
Retry / Fallback / Circuit Breaker
SSE / Streaming
WebFlux / Feign / MQ
Redis / Distributed Cache
Observability platform
```

数据库继续使用 Flyway V1–V6；当前任务预计不需新增 Migration。

## 12. Execution Rule

2026-10-17 用户已确认：**任务验收通过且无阻塞问题，完成收尾文档后自动启动下一任务。** 遇到阻塞问题先修复复验。Phase 级别切换必须先完成完整 Phase Closeout。

当前只执行 **P2-T08**，不提前实现 P2-T09。

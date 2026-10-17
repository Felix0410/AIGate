# AIGate Current Phase

> 这是项目总控、开发导师与 Code Review 的共享执行基线；以真实代码和验收记录为准。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T07 — Provider Adapter（ACTIVE）**

Next Planned Task: **P2-T08 — RestClient + Single Model Proxy（NOT STARTED）**

> 用户已确认启动 P2-T07。仅完成 OpenAI-Compatible 的协议适配和单元测试，完成 Review 后停在执行门，不自动进入 P2-T08。

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | DONE / ACCEPTED |
| P2-T07 | Provider Adapter | **ACTIVE** |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

## 3. Business Goal

P2-T06 已定义 AIGate 对客户端的统一契约：

```text
InvokeRequest / InvokeMessage / MessageRole
InvokeResponse(content, model, finishReason)
```

但真实 Provider 使用自己的 HTTP/JSON 协议。

**本任务的业务目标：** 让 AIGate 能将统一请求转换为 OpenAI-Compatible Chat Completions 请求，并将其正常响应转换为统一 InvokeResponse，避免未来 Runtime Proxy 直接耦合 Provider JSON 细节。

## 4. Minimal Main Flow

```text
InvokeRequest + ModelDeployment.remoteModelName
↓
ProviderAdapterRegistry.select(Provider.type)
↓
OpenAiCompatibleProviderAdapter
↓
OpenAI-Compatible Request Body（仅构造，不发送）

OpenAI-Compatible Successful Response Body（测试输入）
↓
OpenAiCompatibleProviderAdapter
↓
InvokeResponse
```

**本任务只做协议转换，不发送 HTTP 请求。**

## 5. Frozen Architecture

建议最小结构：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
（仅必要的 Provider-specific JSON DTO / mapping）
```

职责：

- `ProviderAdapter`：提供构造上游请求及解析上游成功响应的边界
- `ProviderAdapterRegistry`：根据 `ProviderType` 找到适配器
- `OpenAiCompatibleProviderAdapter`：只理解 OpenAI-Compatible Chat Completions JSON
- P2-T08 的 `ModelProxyService`：负责 Application → Deployment → Provider → Credential → Adapter → HTTP 的整体编排

当前 `ProviderType` 只有 `OPENAI_COMPATIBLE`。通过一个简单 Spring Bean registry/select 逻辑即可，不实现 SPI、类加载器、动态插件或反射框架。

保持统一 DTO 与 Provider DTO **分离**，不使用第三方 OpenAI SDK DTO 充当 AIGate 的业务契约。

## 6. Request Conversion

上游 Chat Completions 最小 Body 形态：

```json
{
  "model": "remote-model-name",
  "messages": [
    {"role": "system", "content": "You are helpful."},
    {"role": "user", "content": "Explain Java HashMap."}
  ],
  "temperature": 0.7,
  "max_tokens": 500
}
```

规则：

- `model` 来自 `ModelDeployment.remoteModelName`，**不是**客户端输入
- `messages` 保持顺序，role 为小写 `system/user/assistant`
- AIGate `maxTokens` 转为上游 `max_tokens`
- `temperature` 原样映射
- 两个可选参数为 null 时省略，不自行设置默认值
- 不添加 `stream`、tools、多模态、模型选择、路由等字段
- Provider Credential / Authorization Header 不属于请求体转换，留给 P2-T08 HTTP 层

实际 Java 方法签名保持简单，由开发导师根据项目 Jackson 3 风格选定；优先可读、易测的 Provider-specific DTO 或 JSON 映射，不引入过度泛型。

## 7. Response Conversion

上游成功响应最小结构：

```json
{
  "model": "remote-model-name",
  "choices": [
    {
      "message": {"role": "assistant", "content": "HashMap is..."},
      "finish_reason": "stop"
    }
  ]
}
```

统一为：

```json
{
  "content": "HashMap is...",
  "model": "remote-model-name",
  "finishReason": "stop"
}
```

当前单次非流式调用只选择 `choices[0]`。不保留原始 Provider Response，也不存 Prompt / Response。

上游返回无效 JSON、空 `choices`、缺失 `message.content` 等无效结构时，不得静默返回假成功。P2-T07 需让结构异常可辨识；统一对外 `PROVIDER_INVALID_RESPONSE` 错误码及 HTTP 映射留给 P2-T09。

## 8. Scope Boundary

**P2-T07 要做：**

- ProviderAdapter 接口
- ProviderAdapterRegistry 基于 Provider.type 选择实现
- OpenAiCompatibleProviderAdapter 双向转换
- 最小 Provider-specific 映射结构
- 请求、响应及结构异常的单元测试

**P2-T07 不做：**

- RestClient / WebClient / Feign
- endpointUrl 拼接、连接/读取超时
- Provider Credential 解密、Bearer 请求头
- ModelProxyService / Runtime Controller / `POST /v1/invoke` 接线
- HTTP 400/401/403/429/5xx、超时统一映射（P2-T09）
- Retry、Fallback、Circuit Breaker、Routing、SSE、Redis、MQ

此任务不新增数据库表、Flyway migration、外部服务或依赖，除非存在真实阻塞问题。

## 9. Acceptance Criteria / Tests

至少通过以下测试：

1. `OPENAI_COMPATIBLE` 被 Registry 选择到正确 Adapter
2. `InvokeRequest` 被转换成上游合法的 `model/messages`
3. 三种 message role 与内容、顺序被保留
4. `maxTokens → max_tokens`，`temperature` 正确映射
5. 可选参数为 null 时不产生错误默认值
6. Provider 200 成功 JSON → `InvokeResponse(content,model,finishReason)`
7. 上游返回无效 JSON / 空 choices / 缺失必要结构时不会伪造成功
8. 不将 Provider-specific DTO 泄露成 AIGate Runtime 公共 DTO
9. 不依赖外部 HTTP、真实 Provider、MySQL 或 MockLLM
10. 未提前创建 Production `POST /v1/invoke` Handler 或 ModelProxyService

P2-T07 优先用快速、确定性的纯协议单元测试。真正的 HTTP/Mock Provider 与 Testcontainers 端到端链路属于 P2-T08/P2-T10。

## 10. Existing Facts

- P2-T01: `Provider.type=OPENAI_COMPATIBLE`、Model、ModelDeployment 已建立
- P2-T02: Provider Credential AES-GCM
- P2-T03: Application.defaultDeploymentId
- P2-T04: Application API Key Lifecycle
- P2-T05: `/v1/**` API Key Runtime Authentication + ApplicationIdentity
- P2-T06: Invoke DTO/Validation 已完成，生产 `POST /v1/invoke` 尚未接通

## 11. Execution Gate

**当前只执行 P2-T07。** 实现、测试和 Code Review 完成后回总控验收。

### 验收后的自动推进规则（用户于 2026-10-17 调整）

- **验收通过且无阻塞问题**：直接完成当前任务收尾（Code Review / 测试与架构检查、技术债、progress / architecture / api-design 等必要文档、必要的 ADR），**并在同一次验收中正式启动下一任务**，同步将执行基线改为 ACTIVE；不再额外等待用户回复“启动”。
- **验收未通过或发现阻塞问题**：保持当前任务 ACTIVE，明确修复项，修复并复验后再推进。
- **Phase 级切换**：必须先完成约定的 Phase 关闭检查与文档沉淀；均通过后可以直接启动下一 Phase。不要跳过 Phase 收尾。
- 当前 P2-T07 **仍为 ACTIVE**；本规则变更不代表已经验收通过或启动 P2-T08。

总原则：业务问题 → 最简单可运行方案 → 验证 → 暴露真实问题 → 再优化架构。

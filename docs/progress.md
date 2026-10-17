# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T07 — Provider Adapter（ACTIVE）**

Next Planned Task: **P2-T08 — RestClient + Single Model Proxy（NOT STARTED）**

---

## 2. Phase 2 Task Status

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

---

## 3. 已完成的 Runtime 基础

```text
Application API Key
↓
/v1/** Runtime Authentication
↓
ApplicationIdentity
```

```text
Application.defaultDeploymentId
↓
ModelDeployment
↓
Provider(type=OPENAI_COMPATIBLE)
```

P2-T06 已完成：

```text
InvokeRequest(messages, temperature?, maxTokens?)
InvokeMessage(role, content)
MessageRole(system/user/assistant)
InvokeResponse(content, model, finishReason)
```

上述内容为已实现的项目事实。生产 `POST /v1/invoke` 尚未接通。

---

## 4. 当前任务 P2-T07

目标：

> 引入最小 Provider Adapter 边界，把 AIGate Invoke Contract 转换为 OpenAI-Compatible Chat Completions 协议，并把其成功响应转换为 InvokeResponse。

计划组件：

```text
ProviderAdapter
ProviderAdapterRegistry
OpenAiCompatibleProviderAdapter
必要的 Provider-specific DTO / mapping
```

数据方向：

```text
InvokeRequest + remoteModelName
→ ProviderAdapter
→ OpenAI-Compatible request JSON

OpenAI-Compatible successful response JSON
→ ProviderAdapter
→ InvokeResponse
```

请求字段约定：

```text
model ← ModelDeployment.remoteModelName
messages ← roles/content/order
temperature ← temperature? (omit if null)
max_tokens ← maxTokens? (omit if null)
```

成功响应约定：

```text
content ← choices[0].message.content
model ← provider response.model
finishReason ← choices[0].finish_reason
```

异常结构必须拒绝，不能伪造模型成功结果；完整 Provider 错误码映射留给 P2-T09。

---

## 5. 当前任务验收重点

- `ProviderType.OPENAI_COMPATIBLE` 能正确选择 Adapter
- 请求 `model/messages/temperature/max_tokens` 转换准确
- role 小写且消息顺序不丢失
- 可选参数 null 不意外变成默认值
- 成功响应准确转换成 InvokeResponse
- 无效/缺失必要 Provider 响应结构不被当成成功
- AIGate 公共 DTO 不依赖 Provider-specific DTO
- 独立单元测试覆盖请求和响应转换
- 不引入 HTTP 客户端、Provider 凭证操作、数据库 migration
- 不提前实现完整 Runtime ModelProxyService

---

## 6. 后续阶段边界

P2-T08 才打通：

```text
POST /v1/invoke
→ ApplicationIdentity
→ Application.defaultDeploymentId
→ ModelDeployment + Provider
→ CredentialService.decrypt
→ ProviderAdapter
→ RestClient
→ Provider
→ InvokeResponse
```

P2-T09 才统一映射 Provider 400、401/403、429、timeout、5xx 和无效响应。

P2-T10 负责 Testcontainers MySQL + Mock Provider 的端到端验证。

---

## 7. 当前数据库及安全

Flyway 仍为 V1–V6。P2-T07 不涉及 schema 变化。

```text
/api/** → HTTP Basic
/v1/** → Application API Key
Provider Credential at rest → AES-GCM
Application API Key at rest → SHA-256(secret)
```

---

## 8. 当前技术债

继续保留：

- TD-001 HTTP Basic 为临时管理面认证
- TD-002 暂无 Role / Permission
- TD-003 暂无统一事务设计
- TD-004 跨模块存在少量 Mapper 直接依赖
- TD-005 Error Code 使用字符串
- TD-006 ApiErrorResponse 缺少 traceId / requestId / path
- TD-007 单 Organization 假设下的唯一约束
- TD-008 测试聚焦关键链路
- TD-009 暂无 CI Test Gate
- TD-010 Provider Master Key Rotation 尚未设计

---

## 9. Execution Gate

**P2-T07 — Provider Adapter：ACTIVE。**

完成实现、测试、开发导师 Code Review 后回总控验收。

**新的推进规则（2026-10-17 起）**：验收通过且无阻塞问题，直接完成收尾文档与必要 ADR，并自动启动下一任务；不再单独等待“启动”。未通过先修复复验。跨 Phase 仍需完成完整 Phase 关闭检查，全部通过后也可自动启动下一 Phase。

当前 P2-T07 仍为 ACTIVE，P2-T08 仍为 NOT STARTED。

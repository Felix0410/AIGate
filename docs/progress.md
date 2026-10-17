# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前执行门：

**P2-T06 COMPLETED / WAITING FOR USER CONFIRMATION**

下一计划任务：

**P2-T07 — Provider Adapter（NOT STARTED）**

---

## 2. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | DONE |
| P2-T06 | Unified Model Contract | **DONE / ACCEPTED** |
| P2-T07 | Provider Adapter | **NOT STARTED** |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. 当前已完成 Runtime 基础

```text
Application API Key
↓
Runtime Authentication
↓
ApplicationIdentity
↓
SecurityContext
```

同时已经建立：

```text
Application.defaultDeploymentId
```

以及统一模型 DTO：

```text
InvokeRequest
InvokeMessage
MessageRole
InvokeResponse
```

---

## 4. P2-T06 Completed Contract

### Request

```text
InvokeRequest
- messages
- temperature?
- maxTokens?
```

### Message

```text
InvokeMessage
- role
- content
```

Role：

```text
system
user
assistant
```

### Response

```text
InvokeResponse
- content
- model
- finishReason
```

---

## 5. Validation

已实现：

```text
messages → @NotEmpty + nested @Valid
role → @NotNull
content → @NotBlank
temperature → 0.0..2.0
maxTokens → > 0
```

unknown role 由 Jackson 反序列化拒绝。

---

## 6. Contract Boundary

当前明确：

```text
Client
→ AIGate Runtime DTO

Provider
→ 后续 Provider-specific DTO
```

AIGate public contract 不复用 Provider SDK / OpenAI SDK DTO。

InvokeRequest 不包含：

```text
applicationId
providerId
modelId
deploymentId
```

---

## 7. No Fake Success

P2-T06 没有创建硬编码 / echo / fake 模型成功响应。

当前真实状态：

```text
/v1/** authentication
→ DONE

Invoke contract
→ DONE

POST /v1/invoke production execution
→ NOT WIRED YET
```

这是有意识的阶段边界，不是遗漏。

---

## 8. Testing

InvokeContractTest 覆盖：

- 三种角色的 JSON 映射
- unknown role 拒绝
- messages 校验
- nested message 校验
- content 校验
- temperature 边界
- maxTokens 边界
- internal resource IDs 不进入 Request DTO
- Response 只包含最小三字段

P2-T06 没有 DB schema 变化，也没有新增 Flyway migration。

GitHub 最新 commit 仍无 CI status，因此 TD-009 继续保留。

---

## 9. Current Technical Debt

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

P2-T06 没有新增阻塞性技术债。

---

## 10. P2-T06 Acceptance Result

- [x] InvokeRequest
- [x] InvokeMessage
- [x] MessageRole
- [x] InvokeResponse
- [x] role 仅 system/user/assistant
- [x] JSON 小写 role
- [x] messages 非空
- [x] content 非空白
- [x] temperature 0.0..2.0
- [x] maxTokens > 0
- [x] Request 不暴露内部资源 selector
- [x] Response 保持最小三字段
- [x] 不依赖 Provider SDK DTO
- [x] 未提前实现 ProviderAdapter / RestClient / ModelProxyService
- [x] 未伪造模型成功结果

结论：

**P2-T06 — Unified Model Contract：COMPLETED / ACCEPTED**

当前停在执行门，等待用户确认是否进入 P2-T07。

# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前任务：

**P2-T05 — Runtime Authentication（ACTIVE）**

下一计划任务：

**P2-T06 — Unified Model Contract（NOT STARTED）**

---

## 2. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | **ACTIVE** |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. 当前已完成基础

```text
P2-T01
Provider / Model / ModelDeployment

P2-T02
Provider Credential AES-GCM

P2-T03
Application.defaultDeploymentId

P2-T04
Application API Key Lifecycle
```

API Key 当前已经具备：

```text
aig_live_<keyId>.<secret>
ACTIVE / REVOKED
SHA-256(secret) at rest
```

---

## 4. P2-T05 Goal

目标：

> 使用 Application API Key 认证 /v1/** 请求，并在 Spring SecurityContext 中建立 ApplicationIdentity。

冻结安全边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

计划使用两条 SecurityFilterChain 隔离认证机制，避免 HTTP Basic 与 API Key 在两个平面之间串用。

---

## 5. Planned Authentication Flow

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
lookup by keyId
↓
ACTIVE?
↓
SHA-256(secret)
↓
constant-time compare
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

---

## 6. P2-T05 Implementation Scope

计划最小组件：

```text
ApplicationIdentity
ApiKeyAuthenticationFilter
ApiKeyAuthenticationService
ApplicationApiKeyHasher
SecurityConfig runtime/management chain split
```

P2-T04 的 SHA-256 生成逻辑可以在本任务中提取为共享 Hasher，避免创建与认证算法重复。

---

## 7. Authentication Failure Contract

以下统一：

```text
missing header
wrong scheme
malformed key
unknown keyId
revoked key
wrong secret
invalid application state
→ 401 UNAUTHORIZED
```

不向未认证客户端暴露 Key 内部状态。

不得记录：

```text
Authorization header
full API key
secret
keyHash
```

---

## 8. P2-T05 Acceptance Focus

至少验证：

- valid ACTIVE Key 可以通过 /v1/** Security Chain
- ApplicationIdentity 正确
- missing header → 401
- malformed / wrong scheme → 401
- unknown keyId → 401
- wrong secret → 401
- revoked Key → 401
- Runtime 不接受 HTTP Basic 替代 API Key
- Management /api/** 继续保持 HTTP Basic
- API Key 不改变管理面原有认证行为
- hash 验证使用 constant-time compare
- 按 keyId 查询单条记录，不扫描所有 Hash
- SecurityContext 不保留完整 secret

正式 `POST /v1/invoke` 属于 P2-T06，不提前实现。

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

---

## 10. Execution Gate

当前只执行：

**P2-T05 — Runtime Authentication**

完成实现、测试、开发导师 Code Review 后回总控验收。

**不得自动进入 P2-T06。**

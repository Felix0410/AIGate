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
→ NOT STARTED
```

---

## 2. 当前系统形态

AIGate 继续采用：

**模块化单体 + 单 MySQL**

当前没有拆微服务，也没有引入 Redis / MQ / Nacos / Spring Cloud。

---

## 3. 当前身份与模型关系

Identity：

```text
Team
├── Employee
└── Application
      └── 1:N ApplicationApiKey
```

Model Registry：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

运行配置：

```text
Application
  ↓ defaultDeploymentId
ModelDeployment
```

---

## 4. Management Plane 与 Runtime Plane 认证分离

P2-T05 后，单体应用内部已经形成逻辑双平面认证：

```text
Management
/api/**
→ HTTP Basic

Runtime
/v1/**
→ Application API Key
```

两者仍运行在同一个 Spring Boot 进程中，但认证机制已经隔离。

这是“逻辑控制面 / 数据面分离”，不是微服务拆分。

---

## 5. 为什么使用两条 SecurityFilterChain

如果把 HTTP Basic 和 API Key 同时放进一条 authenticated() 规则：

```text
/api/** + /v1/**
→ 任意一种认证成功即可
```

会模糊身份边界，甚至让管理凭证误用于 Runtime。

因此当前采用：

```text
@Order(1)
securityMatcher("/v1/**")
→ API Key only

@Order(2)
management/default
→ /api/** HTTP Basic
→ Swagger permitAll
```

收益：

- Runtime 不接受 HTTP Basic
- Management 不接受 API Key
- 后续两个平面可以独立演进

代价：

- Security 配置复杂度上升
- 需要明确 Filter 顺序与 chain 匹配规则

当前复杂度是由真实身份边界需求驱动的，合理。

---

## 6. Runtime API Key Authentication

认证流程：

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
parse keyId + secret
↓
SELECT ApplicationApiKey WHERE key_id = ?
↓
status == ACTIVE
↓
ApplicationApiKeyHasher.matches(secret, keyHash)
↓
load Application
↓
ApplicationIdentity
↓
UsernamePasswordAuthenticationToken
↓
SecurityContext
```

Runtime 请求不携带 applicationId；Application 身份由凭证本身确定。

---

## 7. ApplicationIdentity Boundary

SecurityContext principal 当前只携带：

```text
applicationId
applicationName
teamId
```

不携带：

```text
full API key
secret
keyHash
keyId
Authorization header
defaultDeploymentId
```

Authentication credentials 为 null。

后续 Runtime 业务逻辑应只依赖 ApplicationIdentity，而不是直接解析 Header 或读取 API Key 表。

---

## 8. API Key Hashing

P2-T04 的 SHA-256 逻辑已在 P2-T05 提取为：

```text
ApplicationApiKeyHasher
```

创建与验证共用：

```text
UTF-8
→ SHA-256
→ lowercase hex
```

验证采用：

```text
MessageDigest.isEqual(...)
```

避免普通 String.equals 直接比较 Secret Hash。

API Key 通过 `key_id` 单条查询，不扫描所有 Hash。

---

## 9. Authentication Failure Boundary

Runtime 以下失败统一返回：

```text
401 UNAUTHORIZED
```

包括：

- 缺少 Authorization
- 非 Bearer
- Key 格式错误
- keyId 不存在
- Key 已 REVOKED
- secret 错误
- Application 不存在

不向未认证客户端区分 KEY_NOT_FOUND / REVOKED / WRONG_SECRET。

异常文案不包含 API Key、secret 或 hash。

---

## 10. Existing Secret Strategies

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

两种凭证使用不同策略，因为业务需求不同：

```text
API Key
→ only verify

Provider Credential
→ must recover plaintext
```

---

## 11. Existing Runtime Configuration

Application 当前还直接绑定：

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

这是 single-model proxy 阶段的临时设计。

Runtime Authentication 只确认“哪个 Application”，尚未执行模型选择或 Provider 调用。

---

## 12. Testing Architecture

P2-T05 使用 test-only Runtime Controller 验证真正的 Security Chain：

```text
MockMvc
↓
Runtime SecurityFilterChain
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
MySQL Testcontainer
↓
ApplicationIdentity
↓
test-only Controller
```

验证了：

- ACTIVE Key 成功
- principal 正确
- 各种错误凭证 401
- REVOKED 401
- Runtime 不接受 Basic
- Management 不接受 API Key
- Management Basic 原行为不变
- Swagger 保持公开

GitHub 当前仍无 CI Test Gate。

---

## 13. 当前架构演进

```text
Phase 1
Identity Foundation
↓
P2-T01
Model Registry
↓
P2-T02
Provider Credential Protection
↓
P2-T03
Application -> default ModelDeployment
↓
P2-T04
Application API Key Lifecycle
↓
P2-T05
Runtime Authentication
Management / Runtime auth separation
```

下一步：

```text
P2-T06 Unified Model Contract
```

---

## 14. ADR

P2-T05 落地后新增：

```text
ADR-002 — Application Runtime Authentication
```

记录：

- 为什么不复用 HTTP Basic
- 为什么 API Key 属于 Application
- 为什么 API Key 只存 Hash
- 为什么使用两条 SecurityFilterChain
- 为什么认证失败统一 401
- 为什么暂不引入 JWT / OAuth2

---

## 15. 当前技术债

继续保留 TD-001 ~ TD-010。

P2-T05 没有引入 Redis token cache、JWT、OAuth Server 等额外基础设施。

Runtime 认证目前每次请求访问 MySQL；这是 Phase 2 有意保留的简单实现，等真实性能/多节点问题出现后再演进。

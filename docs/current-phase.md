# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Current Task: **P2-T05 — Runtime Authentication（ACTIVE）**

Next Planned Task: **P2-T06 — Unified Model Contract（NOT STARTED）**

> 用户已确认启动 P2-T05。当前只建立 /v1/** 的 Application API Key 身份认证，不提前实现 /v1/invoke、Provider Adapter 或模型代理。

---

## 2. Task Status

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

## 3. Business Goal

P2-T04 已经解决：

```text
Application
→ 可以签发 API Key
→ DB 只保存 hash
→ 可以 revoke
```

但当前 Spring Security 仍只有：

```text
/api/**
→ HTTP Basic
```

而 `/v1/**` 尚未建立 Application 身份。

P2-T05 的目标：

> 当客户端携带 Application API Key 请求 Runtime API 时，AIGate 能认证出“这是哪个 Application”，并把 Application 身份写入 Spring SecurityContext。

---

## 4. Why a Separate Runtime Authentication Path

管理面与运行时代表两种不同身份：

```text
/api/**
→ 管理身份
→ HTTP Basic

/v1/**
→ 机器 Application 身份
→ Application API Key
```

不能简单写成：

```text
/api/** + /v1/**
→ authenticated()
→ 同时开启 HTTP Basic + API Key
```

否则可能出现认证机制串用，例如 Runtime 被 HTTP Basic 登录状态放行。

因此 P2-T05 冻结为两条明确的 SecurityFilterChain：

```text
@Order(1)
securityMatcher("/v1/**")
→ API Key Authentication only

@Order(2)
management/default chain
→ /api/** HTTP Basic
→ Swagger permitAll
```

目标是认证机制隔离，而不是拆微服务。

---

## 5. Runtime Credential Contract

Header：

```http
Authorization: Bearer <AIGATE_API_KEY>
```

这里的 Bearer 只是 bearer credential，不代表 JWT。

当前 Key 格式：

```text
aig_live_<keyId>.<secret>
```

解析结果：

```text
keyId
→ DB locator

secret
→ credential proof
```

Runtime request 不携带 applicationId。

---

## 6. Authentication Flow

冻结主流程：

```text
Request /v1/**
↓
ApiKeyAuthenticationFilter
↓
read Authorization: Bearer ...
↓
parse aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationService
↓
SELECT ApplicationApiKey WHERE key_id = ?
↓
check status == ACTIVE
↓
SHA-256(supplied secret)
↓
constant-time compare with stored key_hash
↓
load Application
↓
ApplicationIdentity
↓
Spring Security Authentication
↓
SecurityContext
↓
continue filter chain
```

禁止：

```text
扫描全部 API Key hash
按 applicationId 查所有 Key 再比较
把完整 API Key 放入 principal
把 secret 留在 SecurityContext
```

---

## 7. ApplicationIdentity

认证成功后对后续 Runtime 暴露最小身份：

```text
applicationId
applicationName
teamId
```

后续 P2-T06 / P2-T08 业务代码只依赖 ApplicationIdentity，不应关心：

```text
API Key 表
keyHash
secret
Authorization header
```

---

## 8. Hash Verification

P2-T04 当前创建时使用：

```text
SHA-256(secret)
→ lowercase hex key_hash
```

P2-T05 必须与创建算法保持完全一致。

推荐在本任务中提取一个小型共享组件，例如：

```text
ApplicationApiKeyHasher
```

负责：

```text
hash(secret)
matches(secret, storedHash)
```

这样创建和认证不各自复制一份 SHA-256 规则。

比较必须使用 constant-time compare，例如：

```text
MessageDigest.isEqual(...)
```

不要直接使用普通 String.equals 作为 Secret Hash 比较逻辑。

---

## 9. Authentication Failure Semantics

以下情况统一视为认证失败：

```text
Authorization 缺失
不是 Bearer
Key 格式错误
keyId 不存在
Key 已 REVOKED
secret 不匹配
Application 数据异常/不存在
```

对外统一：

```text
401 UNAUTHORIZED
```

不要细分成：

```text
KEY_NOT_FOUND
KEY_REVOKED
WRONG_SECRET
```

原因：Runtime 认证失败不应向未认证客户端暴露凭证内部状态。

错误消息、日志、异常中不得输出：

```text
完整 Authorization header
完整 API Key
secret
keyHash
```

---

## 10. Spring Security Shape

当前 `SecurityConfig` 只有一条 chain。

P2-T05 允许把它演进为：

### Runtime Chain

```text
@Order(1)
securityMatcher("/v1/**")
csrf disabled
/v1/** authenticated
ApiKeyAuthenticationFilter
RestAuthenticationEntryPoint
NO httpBasic
```

### Management Chain

```text
@Order(2)
/v3/api-docs/** permitAll
/swagger-ui/** permitAll
/api/** authenticated
HTTP Basic
existing 401/403 handlers
```

不要为了这一任务引入：

```text
OAuth2 Resource Server
JWT decoder
Authentication Server
Redis session/token cache
```

---

## 11. Minimal Implementation Components

P2-T05 最小组件建议：

```text
ApplicationIdentity
ApiKeyAuthenticationFilter
ApiKeyAuthenticationService
ApplicationApiKeyHasher
SecurityConfig changes
```

可以根据 Spring Security 最简实现选择是否增加一个很小的 Authentication 类型；不要为了模式完整性建立复杂 Provider/Manager 体系。

核心要求是职责清晰：

```text
Filter
→ HTTP Header / SecurityContext 边界

AuthenticationService
→ API Key 验证与 ApplicationIdentity

Hasher
→ SHA-256 + constant-time match
```

---

## 12. Testing Focus

P2-T05 至少验证：

- valid ACTIVE API Key → /v1/** 可通过
- 认证后的 principal / ApplicationIdentity 包含正确 applicationId / applicationName / teamId
- 缺少 Authorization → 401 UNAUTHORIZED
- 非 Bearer header → 401
- malformed API Key → 401
- 不存在 keyId → 401
- wrong secret → 401
- REVOKED Key → 401
- /v1/** 不接受 HTTP Basic 代替 API Key
- /api/** 继续接受 HTTP Basic
- /api/** 不因为 API Key 而改变原有管理认证语义
- Swagger 公共路径保持原行为
- Authentication / SecurityContext 中不保留完整 secret
- API Key 验证按 keyId 单条查询，不扫描全部 Hash
- hash 比较采用 constant-time compare

测试可以使用 test-only Runtime endpoint / controller 来证明 Security Chain，不要求 P2-T05 提前实现正式 `POST /v1/invoke`。

---

## 13. Existing Phase 2 Facts

### API Key Lifecycle

```text
Application 1:N ApplicationApiKey

ACTIVE → REVOKED
```

Key：

```text
aig_live_<keyId>.<secret>
```

DB：

```text
keyId + keyPrefix + SHA-256(secret)
```

### Application Default Deployment

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

### Provider Credential

```text
Provider Credential
→ AES-256-GCM
→ encryptedCredential
```

---

## 14. Explicitly Deferred

P2-T05 不实现：

```text
POST /v1/invoke 正式模型请求协议
ProviderAdapter
RestClient Provider 调用
Provider Error Mapping
API Key expiration
automatic rotation
Redis API Key cache
JWT
OAuth2 / OIDC
Rate Limit / Quota
Routing
SSE
WebFlux
MQ
Microservices
```

---

## 15. Acceptance Gate

当前只执行：

**P2-T05 — Runtime Authentication**

完成实现、测试、开发导师 Code Review 后回总控验收。

**不得自动进入 P2-T06。**

# AIGate Current Phase

> 本文档是 AIGate 当前阶段的共享执行基线，供项目总控、开发导师、Code Review 共同读取。

## 1. Current Phase

**Phase 2 — Model Registry & Single Model Proxy**

Status: **ACTIVE**

Execution Gate: **P2-T05 COMPLETED / WAITING FOR USER CONFIRMATION**

Next Planned Task: **P2-T06 — Unified Model Contract（NOT STARTED）**

> P2-T05 已通过总控验收。在用户明确确认前，不得自动开始 P2-T06。

---

## 2. Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | DONE |
| P2-T02 | Provider Credential Protection | DONE |
| P2-T03 | Application Default Deployment | DONE |
| P2-T04 | Application API Key Lifecycle | DONE |
| P2-T05 | Runtime Authentication | **DONE / ACCEPTED** |
| P2-T06 | Unified Model Contract | **NOT STARTED** |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. P2-T05 — Runtime Authentication

业务目标：

> 使用 Application API Key 认证 /v1/** 请求，并在 Spring SecurityContext 中建立最小 ApplicationIdentity。

当前真实安全边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

两种认证机制已经通过两条 SecurityFilterChain 隔离。

---

## 4. SecurityFilterChain Design

### Runtime Chain

```text
@Order(1)
securityMatcher("/v1/**")
↓
ApiKeyAuthenticationFilter
↓
authenticated()
```

特征：

- 不启用 HTTP Basic
- 只处理 Runtime /v1/**
- API Key 认证成功后建立 SecurityContext
- 使用统一 RestAuthenticationEntryPoint 返回 401

### Management Chain

```text
@Order(2)
/api/** → HTTP Basic
Swagger → permitAll
```

Management chain 不注册 API Key Filter。

因此：

```text
HTTP Basic 不能代替 API Key 访问 /v1/**
API Key 不能代替 HTTP Basic 访问 /api/**
```

---

## 5. Runtime Authentication Flow

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
ApiKeyAuthenticationFilter
↓
ApiKeyAuthenticationService
↓
parse keyId + secret
↓
SELECT application_api_key WHERE key_id = ?
↓
check ACTIVE
↓
ApplicationApiKeyHasher.matches(...)
↓
SHA-256(secret)
↓
MessageDigest.isEqual(...)
↓
load Application
↓
ApplicationIdentity
↓
UsernamePasswordAuthenticationToken
↓
SecurityContext
```

API Key 通过 keyId 单条查询，不扫描所有 key hash。

---

## 6. ApplicationIdentity

当前 principal：

```text
applicationId
applicationName
teamId
```

明确不包含：

```text
apiKey
secret
keyHash
keyId
Authorization header
```

Authentication credentials 设为 null，避免凭证进入 SecurityContext。

---

## 7. Shared API Key Hasher

P2-T04 创建 Key 时的 SHA-256 逻辑已提取为：

```text
ApplicationApiKeyHasher
```

职责：

```text
hash(secret)
matches(secret, storedHash)
```

创建和认证共用同一套：

```text
UTF-8
→ SHA-256
→ lowercase hex
```

认证比较使用：

```text
MessageDigest.isEqual(...)
```

避免直接使用普通 String.equals 做 Secret Hash 比较。

---

## 8. Authentication Failure Contract

以下情况统一：

```text
Authorization 缺失
非 Bearer
API Key 格式错误
keyId 不存在
Key 已 REVOKED
secret 错误
Application 不存在
→ 401 UNAUTHORIZED
```

对外不返回：

```text
API_KEY_NOT_FOUND
KEY_REVOKED
WRONG_SECRET
```

认证异常使用固定内部文案，不携带完整 API Key / secret / keyHash。

---

## 9. P2-T05 Test Coverage

使用 test-only Runtime Controller 验证 Security Chain，不提前实现正式 /v1/invoke。

集成测试覆盖：

- ACTIVE API Key → /v1/** 200
- principal 中 applicationId / applicationName / teamId 正确
- 缺 Authorization → 401 UNAUTHORIZED
- 非 Bearer → 401
- malformed Key → 401
- unknown keyId → 401
- wrong secret → 401
- REVOKED Key → 401
- HTTP Basic 无法访问 /v1/**
- API Key 无法访问 /api/**
- HTTP Basic 继续可以访问 /api/**
- Swagger 公共路径保持公开

Hasher 单元测试覆盖：

- hash 稳定
- 64 位 lowercase hex
- 正确 secret matches=true
- 错误 secret matches=false

GitHub 当前仍无 CI status / workflow run；TD-009 继续保留。

---

## 10. Existing Phase 2 Facts

### Application API Key

```text
Application 1:N ApplicationApiKey
ACTIVE → REVOKED
```

### Default Deployment

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

## 11. Next Planned Task

**P2-T06 — Unified Model Contract**

计划才会开始定义：

```text
POST /v1/invoke
InvokeRequest
Message
InvokeResponse
```

P2-T05 没有提前实现正式模型调用 API、Provider Adapter 或 RestClient。

---

## 12. Explicitly Deferred

```text
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

## 13. Acceptance Gate

P2-T05 已完成并验收。

**当前等待用户确认是否开始 P2-T06。**

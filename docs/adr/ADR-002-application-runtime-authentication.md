# ADR-002 — Application Runtime Authentication

## Status

Accepted

## 背景

AIGate Phase 1 已使用 HTTP Basic 保护管理 API：

```text
/api/**
→ management identity
→ HTTP Basic
```

Phase 2 开始出现真正的 Runtime 调用方：

```text
DevAgent / other client
→ AIGate Runtime
→ Model Provider
```

Runtime 调用方不是管理员账号，而是一个已注册的 Application。

P2-T04 已经建立：

```text
Application 1:N ApplicationApiKey
```

并实现 API Key 的签发、Hash 存储和撤销。

P2-T05 需要决定：

> Runtime 如何认证 Application，并且如何与现有 Management Authentication 隔离。

---

## 问题

需要同时满足：

1. Runtime 能识别具体 Application
2. Client 不需要携带内部 applicationId
3. API Key 明文不落库
4. revoked Key 立即不可用
5. Management HTTP Basic 不能误用于 Runtime
6. Runtime API Key 不能误用于 Management
7. 不为当前规模提前引入复杂身份基础设施

---

## 候选方案

### 方案 A — Runtime 继续复用 HTTP Basic

优点：

- 实现最少
- 不需要新增认证逻辑

缺点：

- 无法自然表达 Application 机器身份
- 管理身份与运行身份混在一起
- 无法做到 Application 独立 revoke / 多 Key
- 后续 Quota / Usage / Audit 无法自然归属 Application

结论：不采用。

### 方案 B — JWT

优点：

- 自包含 Claims
- Runtime 查询数据库需求可以减少
- 标准化程度高

缺点：

- 需要签发、签名验证、过期和轮换策略
- revoke 语义变复杂
- 需要解决 token 生命周期和密钥管理
- 当前没有跨系统身份联邦需求

结论：Phase 2 不采用。

### 方案 C — OAuth2 / OIDC

优点：

- 标准身份授权协议
- 适合复杂用户/第三方授权场景

缺点：

- 当前 Application M2M 场景不需要完整 OAuth 授权服务器
- 会显著增加配置、协议和运维复杂度
- 偏离当前“先打通模型代理主流程”的目标

结论：当前不采用。

### 方案 D — Application API Key

优点：

- 非常适合服务到服务 / Application 身份
- 签发与撤销模型简单
- 可以自然支持一个 Application 多 Key
- 与后续 Quota / Usage / Audit 的 Application 归属一致
- 实现成本低

缺点：

- Bearer Key 泄露后在撤销前可被直接使用
- 当前每次认证需要访问 MySQL
- 未来需要考虑 rotation / cache / multi-node consistency

结论：采用。

---

## 最终方案

### 1. Application API Key

格式：

```text
aig_live_<keyId>.<secret>
```

其中：

- `keyId` 是非秘密定位符
- `secret` 是高熵 bearer secret

数据库只保存：

```text
keyId
keyPrefix
SHA-256(secret)
status
metadata
```

完整 Key 明文只在创建时返回一次。

---

### 2. 为什么使用 SHA-256 而不是 BCrypt / Argon2

Application API Key secret：

- 由服务器生成
- 使用 SecureRandom
- 具备 256-bit 随机熵
- 不属于低熵人类密码

因此不存在典型密码字典攻击问题。

当前采用：

```text
SHA-256(secret)
```

认证时：

```text
MessageDigest.isEqual(...)
```

进行 constant-time hash 比较。

---

### 3. 两条 SecurityFilterChain

Runtime：

```text
@Order(1)
securityMatcher("/v1/**")
→ Application API Key only
```

Management：

```text
@Order(2)
/api/** → HTTP Basic
Swagger → permitAll
```

这样明确保证：

```text
HTTP Basic ≠ Runtime identity
API Key ≠ Management identity
```

---

### 4. Runtime Authentication Flow

```text
Authorization: Bearer aig_live_<keyId>.<secret>
↓
parse
↓
lookup ApplicationApiKey by keyId
↓
check ACTIVE
↓
hash + constant-time compare
↓
load Application
↓
ApplicationIdentity
↓
SecurityContext
```

ApplicationIdentity 只包含：

```text
applicationId
applicationName
teamId
```

不保留 API Key / secret / keyHash。

---

### 5. Failure Semantics

以下统一返回：

```text
401 UNAUTHORIZED
```

包括：

- Header 缺失
- Scheme 错误
- Key 格式错误
- keyId 不存在
- revoked
- secret 错误
- Application 不存在

不向未认证客户端暴露凭证内部状态。

---

## 选择原因

当前阶段真实需求只是：

> 让一个注册 Application 能安全调用 Runtime，并能够独立签发和撤销凭证。

API Key + Hash + 两条 SecurityFilterChain 是当前最小、清晰、可测试的方案。

它满足当前业务需求，同时没有提前承担 JWT / OAuth2 / Redis Token Cache 的复杂度。

---

## 优点

- Management / Runtime 身份边界清晰
- Application 身份天然适合后续 Quota / Usage / Audit
- API Key 可多 Key、可 revoke
- Secret 不明文落库
- Spring SecurityContext 为后续业务提供统一身份入口
- 实现简单，容易验证

---

## 缺点

- API Key 是 bearer credential，泄露即可以调用
- 当前每次 Runtime 认证访问 MySQL
- 没有 expiration
- 没有 automatic rotation
- 没有 distributed cache
- 没有更复杂的 scope / permission

这些都是当前阶段有意接受的成本。

---

## 对未来的影响

短期：

```text
P2-T06 / P2-T08
→ 从 SecurityContext 获取 ApplicationIdentity
→ 不再自行解析 API Key
```

中期可能演进：

```text
API Key local/Redis cache
rotation
last-used metadata
scope / permission
```

长期如果出现企业身份联邦或复杂授权需求，可以重新评估：

```text
OAuth2 Client Credentials
OIDC
external Identity Provider
```

当前 ADR 不承诺未来永远使用数据库直查 API Key；它只确定 Phase 2 的最小 Runtime Authentication 基线。

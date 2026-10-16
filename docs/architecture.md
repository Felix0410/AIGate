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
→ NOT STARTED
```

---

## 2. 当前系统形态

AIGate 继续采用：

**模块化单体 + 单 MySQL**

当前没有拆微服务，也没有引入 Redis / MQ / Nacos / Spring Cloud。

---

## 3. 当前领域关系

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

## 4. Application Runtime Identity 基础

P2-T04 建立了机器身份凭证的数据基础：

```text
Application
↓
ApplicationApiKey
↓
未来 /v1/** Runtime Authentication
```

API Key 属于 Application，而不是 Employee。

一个 Application 可以同时拥有多把 Key，便于手工轮换：

```text
create new key
→ client switches
→ verify
→ revoke old key
```

当前不做自动 rotation / expiry。

---

## 5. API Key 为什么用 Hash

Provider Credential 后续需要恢复原文，因此使用 AES-GCM。

Application API Key 只需要验证，不需要恢复：

```text
supplied secret
→ SHA-256
→ compare stored hash
```

因此 P2-T04 使用：

```text
SecureRandom high-entropy secret
+
SHA-256
```

而不是可逆加密。

这里不使用 BCrypt / Argon2，是因为 secret 由服务器生成，具备高随机熵，不是低熵人类密码。

---

## 6. API Key Format

当前格式：

```text
aig_live_<keyId>.<secret>
```

其中：

```text
keyId
→ 非秘密定位符
→ P2-T05 可据此 O(1) 查询数据库记录

secret
→ 真正的 bearer secret
```

生成参数：

```text
keyId  = SecureRandom 16 bytes
secret = SecureRandom 32 bytes
encoding = URL-safe Base64 without padding
```

完整明文 Key 只在创建时返回一次。

---

## 7. API Key Storage Boundary

数据库保存：

```text
key_id
key_prefix
key_hash
status
metadata
```

不保存：

```text
完整 apiKey
secret 明文
```

普通 List / Revoke Response 也不暴露：

```text
apiKey
secret
keyHash
```

因此管理面无法通过普通查询恢复完整 Key。

---

## 8. API Key Lifecycle

当前状态机只做：

```text
ACTIVE
  ↓ revoke
REVOKED
```

撤销会写入：

```text
revokedAt = now
```

记录保留，不物理删除。

重复 revoke 是幂等的：

```text
REVOKED
→ 再 revoke
→ 返回原记录
→ 不重写 revokedAt
```

---

## 9. Application 删除与 CASCADE

`application_api_key.application_id` 使用：

```text
ON DELETE CASCADE
```

这是项目通常 RESTRICT 策略的局部例外。

原因：

- API Key 是 Application 的强生命周期从属凭证
- Application 删除后所有 Key 必须失效
- Key 没有脱离 Application 独立存在的业务意义
- Key 本身不提供物理删除接口

这不意味着其他业务关系改用 CASCADE。

---

## 10. Existing Runtime Configuration

P2-T03 已建立：

```text
Application
↓
defaultDeploymentId
↓
ModelDeployment
```

这是 single-model proxy 阶段的临时绑定。

后续 Routing 阶段才演进：

```text
Application
↓
ModelAlias / Route
↓
ModelDeployment
```

---

## 11. Provider Credential Protection

P2-T02 安全边界继续有效：

```text
Provider Credential
→ AES/GCM/NoPadding
→ encrypted_credential
```

Master Key：

```text
AIGATE_MASTER_KEY
```

普通 Response 不返回 credential 或 encryptedCredential。

---

## 12. 当前 Security

已经实现：

```text
/api/**
→ HTTP Basic

Provider Secret at rest
→ AES-GCM

Application API Key at rest
→ SHA-256(secret)
```

尚未实现：

```text
/v1/**
→ Application API Key Authentication
```

P2-T04 只建立凭证生命周期，不建立 SecurityContext。

---

## 13. P2-T05 Planned Authentication Flow

下一任务计划：

```text
Authorization: Bearer <AIGATE_API_KEY>
↓
parse keyId + secret
↓
lookup ApplicationApiKey by keyId
↓
check ACTIVE
↓
SHA-256(supplied secret)
↓
constant-time compare
↓
ApplicationIdentity
↓
Spring Security Authentication
↓
SecurityContext
```

管理面 HTTP Basic 与运行时 API Key 认证将开始分离。

---

## 14. Testing Architecture

当前测试继续使用：

```text
MockMvc
↓
Spring Security
↓
Controller
↓
Service
↓
MyBatis-Plus
↓
MySQL Testcontainer
```

P2-T04 已验证：

- 多 Key
- hash storage
- secret boundary
- revoke / idempotency
- cross-application isolation
- FK CASCADE

GitHub 当前仍无 CI Test Gate。

---

## 15. 当前架构演进

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
```

下一步：

```text
P2-T05 Runtime Authentication
```

---

## 16. 当前技术债

继续保留 TD-001 ~ TD-010。

API Key expiration / automatic rotation 当前是明确非需求，不作为技术债强行记录。

P2-T04 没有引入 Redis Token Cache、JWT、OAuth Server 等额外复杂度。

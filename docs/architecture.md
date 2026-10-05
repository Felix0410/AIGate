# AIGate Architecture

## 1. 文档目的

本文档记录 AIGate 当前实际架构，以及架构演进过程。

---

## 2. 当前阶段

当前阶段：

**Phase 2 — Model Registry & Single Model Proxy**

当前执行状态：

```text
P2-T01 Model Registry Schema
→ COMPLETED

P2-T02 Provider Credential Protection
→ COMPLETED / ACCEPTED

P2-T03 Application Default Deployment
→ NOT STARTED
```

当前停在执行门，等待用户确认是否进入 P2-T03。

---

## 3. 当前系统形态

AIGate 继续采用：

**单体应用 + 模块化代码组织**

当前没有拆分微服务，也没有为了 Secret 管理引入 Vault / KMS。

当前重点仍是：

```text
先打通 AI Gateway 主流程
再根据真实问题演进基础设施
```

---

## 4. 当前整体架构

管理面当前链路：

```text
Client
↓
HTTP Basic
↓
Spring MVC Controller
↓
Service
↓
MyBatis-Plus
↓
MySQL
```

Model Registry：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

Provider Credential 保护链路：

```text
credential input
↓
ModelDeploymentService
↓
CredentialService.encrypt()
↓
AES-GCM
↓
model_deployment.encrypted_credential
```

未来 Runtime 使用时：

```text
model_deployment.encrypted_credential
↓
CredentialService.decrypt()
↓
ProviderAdapter
↓
Provider
```

Runtime Proxy 本身尚未实现。

---

## 5. 当前模块划分

业务模块：

```text
team
employee
application
provider
model
deployment
```

基础能力：

```text
credential
common
config
security
```

`credential` 当前只负责 Provider Credential 的对称加密/解密边界。

没有额外引入：

- Repository abstraction
- Domain Service
- CQRS
- Event Bus
- Secret Manager client

---

## 6. Model Registry

### Provider

表示模型服务提供方 / 协议类别。

当前只支持：

```text
OPENAI_COMPATIBLE
```

### Model

表示逻辑模型，不直接属于 Provider。

### ModelDeployment

真正可调用的部署实例，同时关联 Provider 与 Model。

当前关键运行字段：

```text
endpointUrl
remoteModelName
encryptedCredential
enabled
```

运行时最终应选择 ModelDeployment。

---

## 7. P2-T02 Credential Protection

### 7.1 为什么需要加密而不是 Hash

Provider Credential 后续还要被 AIGate 恢复原文并发送给 Provider，因此：

```text
Hash
→ 无法恢复原文
→ 不适用

Encryption
→ 可以受控恢复原文
→ 适用
```

因此当前使用对称认证加密。

### 7.2 当前算法

```text
AES/GCM/NoPadding
```

参数：

```text
AES key: 256 bits
IV: 12 random bytes
GCM tag: 128 bits
```

使用 `SecureRandom` 为每次加密产生新的 IV。

因此相同 credential 多次加密也不会得到相同密文。

### 7.3 密文 Envelope

当前格式：

```text
v1:<base64(iv)>:<base64(ciphertext+tag)>
```

版本号 `v1` 为未来算法 / Key 策略演进保留兼容入口。

### 7.4 Master Key

来源：

```text
AIGATE_MASTER_KEY
```

要求：

```text
Base64 decode
→ exactly 32 bytes
```

Master Key 不存入数据库，也未写进 application.yaml。

缺失或格式非法时应用不能正常建立 CredentialService，属于 fail-fast 配置错误。

### 7.5 Authentication Integrity

GCM 同时提供：

```text
Confidentiality
+
Integrity / Authenticity
```

因此：

- 密文被篡改 → 解密失败
- 错误 Master Key → 解密失败

不会静默得到错误的 Provider Secret。

### 7.6 API Secret Boundary

HTTP Request DTO 可以接受：

```text
credential
```

但 Response DTO 不包含：

```text
credential
encryptedCredential
```

因此普通管理 API 不回显 Secret。

### 7.7 Null Credential

`encryptedCredential` 允许 NULL。

原因：

- MockLLM 可能无需认证
- 私有 Provider 可能暂时不需要 Secret

### 7.8 PUT 语义

当前项目 PUT 是完整更新。

因此：

```text
PUT credential = null
→ encryptedCredential = null
```

Entity 对该字段使用 `FieldStrategy.ALWAYS`，确保 null 能真正写入数据库。

这是当前明确行为，不是 PATCH 语义。

---

## 8. 数据库与 Flyway

当前 migration：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

P2-T02 没有新增 migration。

原因：

> V4 已经存在 `model_deployment.encrypted_credential`，本任务只有应用层安全逻辑变化，没有 schema 变化。

不为了“每个任务一个 migration”人为制造数据库版本。

---

## 9. Security

当前真实安全边界：

```text
Management API
/api/**
→ HTTP Basic

Provider Secret at rest
→ AES-GCM
```

尚未实现：

```text
Runtime /v1/**
→ Application API Key
```

Employee 仍不等于登录账号。

---

## 10. Testing Architecture

原有集成测试链保持：

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
Real MySQL Testcontainer
```

P2-T02 新增两层测试：

### CredentialService Unit Test

验证：

- round-trip
- random IV
- tamper detection
- wrong master key
- invalid master key length
- null credential

### ModelDeployment Integration Test

验证：

- API 可以接收 credential
- Response 不泄露 Secret
- DB 保存的是密文
- 密文可恢复原文
- update 后重新加密
- PUT null 清空 credential

测试上下文通过 DynamicPropertySource 提供专用测试 Master Key。

GitHub 当前仍无 CI 自动测试门禁。

---

## 11. 当前架构演进

```text
Phase 1
Identity Foundation
↓

P2-T01
Model Registry
Provider / Model / ModelDeployment
↓

P2-T02
Provider Credential Protection
AES-GCM + external Master Key
```

尚未发生：

```text
Application Default Deployment
Application API Key
Runtime Authentication
Provider Adapter
Outbound HTTP Proxy
```

---

## 12. 当前技术债

继续保留既有 TD-001 ~ TD-009。

新增：

### TD-010 Provider Master Key Rotation 尚未设计

当前只有：

```text
AIGATE_MASTER_KEY
+
v1 envelope
```

未来如果需要更换 Master Key，需要解决：

- 旧密文如何继续读取
- 是否批量 re-encrypt
- 多 Key version 如何识别
- Rotation 失败如何回滚

当前没有真实轮换需求，因此 Phase 2 不提前引入 KMS / Vault / Key Ring。

---

## 13. 当前架构结论

P2-T02 完成后，AIGate 已具备：

```text
Identity Foundation
+
Model Registry
+
Provider Credential Encryption at Rest
+
稳定管理 API
+
真实数据库集成测试
```

下一计划任务是 P2-T03，但当前尚未启动。

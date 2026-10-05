# AIGate Progress

## 1. 当前项目状态

当前阶段：

**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前执行门：

**P2-T02 COMPLETED / WAITING FOR USER CONFIRMATION**

下一计划任务：

**P2-T03 — Application Default Deployment（NOT STARTED）**

Phase 1 已完成并验收通过。
P2-T01 已完成。
P2-T02 已通过总控验收。

---

## 2. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **DONE** |
| P2-T03 | Application Default Deployment | **NOT STARTED** |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

当前不会自动开始 P2-T03。

---

## 3. 已完成能力

### P2-T01 Model Registry

已建立：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

Provider / Model / ModelDeployment 已完成基础 CRUD、唯一约束、外键约束和核心集成测试。

### P2-T02 Provider Credential Protection

已实现：

```text
Plain Provider Credential
↓
CredentialService
↓
AES/GCM/NoPadding
↓
v1:<iv>:<ciphertext+tag>
↓
model_deployment.encrypted_credential
```

安全参数：

- AES-256
- 12-byte random IV
- 128-bit GCM authentication tag
- Master Key 为 Base64 编码的 32 bytes
- Master Key 从 `AIGATE_MASTER_KEY` 注入

当前行为：

- 创建/更新 ModelDeployment 时可提交 `credential`
- credential 在 Service 层加密后写入数据库
- 相同明文重复加密产生不同密文
- 普通 ModelDeployment Response 不返回明文或密文 credential
- null credential 合法
- PUT 是完整更新，`credential = null` 会清空数据库中的 encrypted credential
- 错误 Master Key / 被篡改密文无法成功解密

P2-T02 没有 schema 变化，直接复用 V4 的 `encrypted_credential` 字段。

---

## 4. 当前数据库

MySQL 8.4。

Flyway：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

P2-T03 才计划新增 `application.default_deployment_id`。

---

## 5. Security 状态

当前已经实现：

```text
/api/**
→ HTTP Basic

Provider Credential at rest
→ AES-GCM encrypted
```

尚未实现：

```text
/v1/**
→ Application API Key
```

Application API Key 属于后续 P2-T04 / P2-T05。

---

## 6. Testing

P2-T02 新增单元测试与集成测试，覆盖：

- encrypt/decrypt round trip
- 同一 credential 随机 IV 导致不同密文
- 密文篡改检测
- 错误 Master Key
- 非法 Master Key 长度
- null credential
- API Response 不泄露 credential
- 数据库实际保存密文
- credential 更新
- PUT null 清空 credential

测试上下文通过 DynamicPropertySource 提供测试 Master Key，不依赖开发机真实环境变量。

GitHub 当前仍无 CI status / workflow run，因此自动测试门禁缺失继续保留为 TD-009。

---

## 7. 当前架构

继续保持：

**模块化单体**

当前主要模块：

```text
team
employee
application
provider
model
deployment
credential
common
config
security
```

没有因为 Secret 管理引入 Vault / KMS / 微服务。

---

## 8. 当前技术债

### TD-001 HTTP Basic 是临时管理面认证方案

最终管理身份体系尚未确定。

### TD-002 暂无 Role / Permission

等真实授权需求出现后再实现。

### TD-003 Service 暂无统一事务设计

出现真实多表原子操作后再明确事务边界。

### TD-004 跨模块存在少量 Mapper 依赖

当前保持简单。

### TD-005 Error Code 使用字符串

规模扩大后考虑统一管理。

### TD-006 ApiErrorResponse 可观测性不足

缺少 traceId / requestId / path。

### TD-007 当前唯一约束基于单 Organization 假设

未来 Multi-Tenant 时重新评估。

### TD-008 测试覆盖关键链路，不追求完整覆盖率

随复杂度逐步增加。

### TD-009 暂无 CI Test Gate

GitHub 当前无自动测试状态检查。

### TD-010 Provider Master Key Rotation 尚未设计

当前一个 `AIGATE_MASTER_KEY` 负责 v1 Credential 解密。

Phase 2 不做自动 Key Rotation；如果未来需要更换 Master Key，需要设计旧密文迁移 / 多版本 Key 读取策略。当前版本化 envelope 已为此预留演进空间。

---

## 9. P2-T02 Acceptance Result

- [x] AES-GCM 加密/解密
- [x] 32-byte Master Key 校验
- [x] 随机 IV
- [x] 版本化 `v1` envelope
- [x] credential 明文不落库
- [x] API Response 不暴露 credential
- [x] null credential 合法
- [x] 错误 Master Key 失败
- [x] 篡改密文失败
- [x] 独立 CredentialService 测试
- [x] ModelDeployment 集成测试覆盖真实 DB 密文
- [x] 无不必要 schema migration
- [x] 未提前引入 Vault / KMS / Runtime Proxy

结论：

**P2-T02 — Provider Credential Protection：COMPLETED / ACCEPTED**

当前停在执行门，等待用户确认是否进入 P2-T03。

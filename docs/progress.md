# AIGate Progress

## 1. 当前项目状态

当前阶段：**Phase 2 — Model Registry & Single Model Proxy**

状态：**ACTIVE**

当前执行门：

**P2-T05 COMPLETED / WAITING FOR USER CONFIRMATION**

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
| P2-T05 | Runtime Authentication | **DONE / ACCEPTED** |
| P2-T06 | Unified Model Contract | **NOT STARTED** |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

---

## 3. 当前已完成能力

### P2-T01 Model Registry

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

### P2-T02 Provider Credential Protection

```text
Provider Credential
→ AES-256-GCM
→ MySQL encryptedCredential
```

### P2-T03 Application Default Deployment

```text
Application
→ defaultDeploymentId
→ ModelDeployment
```

### P2-T04 Application API Key Lifecycle

```text
Application 1:N ApplicationApiKey
ACTIVE → REVOKED
```

### P2-T05 Runtime Authentication

当前安全边界：

```text
/api/**
→ HTTP Basic

/v1/**
→ Application API Key
```

已实现：

```text
Bearer API Key
→ parse keyId + secret
→ lookup by keyId
→ check ACTIVE
→ SHA-256
→ constant-time compare
→ ApplicationIdentity
→ SecurityContext
```

ApplicationIdentity：

```text
applicationId
applicationName
teamId
```

---

## 4. Security Architecture

P2-T05 将单一 SecurityFilterChain 演进为两条独立 chain：

```text
@Order(1)
Runtime /v1/**
→ API Key only

@Order(2)
Management /api/**
→ HTTP Basic
```

验证了双向隔离：

- HTTP Basic 不能访问 Runtime
- API Key 不能访问 Management
- Swagger 公开路径保持不变

---

## 5. API Key Verification

新增共享：

```text
ApplicationApiKeyHasher
```

创建与认证共用：

```text
UTF-8
→ SHA-256
→ lowercase hex
```

认证比较：

```text
MessageDigest.isEqual
```

API Key 查询：

```text
WHERE key_id = ?
```

不扫描所有 Key Hash。

---

## 6. Authentication Failure Semantics

以下全部统一：

```text
missing Authorization
non-Bearer
malformed Key
unknown keyId
revoked Key
wrong secret
missing Application
→ 401 UNAUTHORIZED
```

避免向未认证调用方暴露凭证内部状态。

---

## 7. Testing

P2-T05 集成测试覆盖：

- ACTIVE Key 认证成功
- ApplicationIdentity 正确
- missing Authorization
- non-Bearer
- malformed Key
- unknown keyId
- wrong secret
- REVOKED Key
- Basic 不能越界访问 /v1/**
- API Key 不能越界访问 /api/**
- Basic 继续可访问 /api/**
- Swagger 保持公开

Hasher 单元测试覆盖 hash 格式和 matches。

测试使用现有真实 MySQL Testcontainers。

GitHub 最新 commit 仍无 CI status / workflow run，因此 TD-009 继续保留。

---

## 8. 当前数据库

Flyway 仍为：

```text
V1 Team
V2 Employee
V3 Application
V4 Provider / Model / ModelDeployment
V5 Application.default_deployment_id
V6 ApplicationApiKey
```

P2-T05 无 schema 变化，因此没有新增 migration。

---

## 9. 当前技术债

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

P2-T05 没有引入新的阻塞性技术债。

---

## 10. P2-T05 Acceptance Result

- [x] /v1/** 使用 Application API Key
- [x] /api/** 保持 HTTP Basic
- [x] 双 SecurityFilterChain 隔离
- [x] 按 keyId 单条查询
- [x] ACTIVE 校验
- [x] SHA-256 与创建规则复用
- [x] constant-time compare
- [x] ApplicationIdentity 最小化
- [x] credentials 不进入 SecurityContext
- [x] 认证失败统一 401
- [x] 不暴露 key 内部状态
- [x] Runtime / Management 双向认证隔离测试
- [x] 未提前实现 /v1/invoke

结论：

**P2-T05 — Runtime Authentication：COMPLETED / ACCEPTED**

当前停在执行门，等待用户确认是否进入 P2-T06。

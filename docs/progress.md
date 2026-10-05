# AIGate Progress

## 1. 当前项目状态

当前阶段：

**Phase 2 — Model Registry & Single Model Proxy**

状态：

**ACTIVE**

当前任务：

**P2-T02 — Provider Credential Protection（ACTIVE）**

下一计划任务：

**P2-T03 — Application Default Deployment（NOT STARTED）**

Phase 1 已完成并验收通过。
P2-T01 已完成并通过开发导师 Code Review。

---

## 2. Phase 1 完成结果

Phase 1 — Foundation & Core Identity 已完成：

```text
Team
├── Employee
└── Application
```

已具备：

- Team / Employee / Application CRUD
- Team 归属关系
- Bean Validation
- Global Exception Handling
- HTTP Basic 管理 API 安全边界
- Flyway
- MySQL 8.4
- MyBatis-Plus
- Testcontainers + MockMvc 集成测试

Phase 1 任务 P1-T01 ~ P1-T09 全部 DONE。

---

## 3. Phase 2 Business Goal

Phase 2 的目标是让一个 Application 第一次通过 AIGate 安全调用 AI Model，同时 Application 不持有 Provider Secret。

目标主流程：

```text
Application
↓
AIGate API Key
↓
AIGate
↓
Application.defaultDeployment
↓
ModelDeployment
↓
ProviderAdapter
↓
Provider
```

Phase 2 只做 single-model、non-streaming proxy。

---

## 4. Phase 2 Task Status

| Task | 内容 | 状态 |
|---|---|---|
| P2-T01 | Model Registry Schema | **DONE** |
| P2-T02 | Provider Credential Protection | **ACTIVE** |
| P2-T03 | Application Default Deployment | NOT STARTED |
| P2-T04 | Application API Key Lifecycle | NOT STARTED |
| P2-T05 | Runtime Authentication | NOT STARTED |
| P2-T06 | Unified Model Contract | NOT STARTED |
| P2-T07 | Provider Adapter | NOT STARTED |
| P2-T08 | RestClient + Single Model Proxy | NOT STARTED |
| P2-T09 | Provider Error Mapping | NOT STARTED |
| P2-T10 | End-to-End Integration Test | NOT STARTED |
| P2-T11 | Phase Closeout | NOT STARTED |

当前只执行 P2-T02，不提前进入 P2-T03。

---

## 5. P2-T01 完成能力

P2-T01 已建立 Model Registry 的第一版最小业务模型：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

### 5.1 Provider

已支持：

- create / get / list / update / delete
- name 唯一性
- `ProviderType.OPENAI_COMPATIBLE`
- 非法 ProviderType JSON 返回 `400 INVALID_REQUEST`
- 被 ModelDeployment 引用时删除返回 `409 RESOURCE_CONFLICT`

### 5.2 Model

已支持：

- create / get / list / update / delete
- name 唯一性
- 被 ModelDeployment 引用时删除返回 `409 RESOURCE_CONFLICT`

### 5.3 ModelDeployment

已支持：

- create / get / list / update / delete
- name 唯一性
- 必须关联已存在 Provider
- 必须关联已存在 Model
- endpointUrl
- remoteModelName
- enabled
- encryptedCredential 字段已预留

---

## 6. 当前任务 P2-T02

目标：

> Provider Credential 可以由 AIGate 安全保存和恢复使用，但不能以明文形式落库或通过普通管理 API 返回。

当前冻结方案：

```text
Plain Provider Secret
↓
AES-GCM
↓
encryptedCredential
↓
MySQL
```

Master Key：

```text
AIGATE_MASTER_KEY
```

来自环境变量，不进入数据库、不提交 Git。

建议密文格式：

```text
v1:<iv>:<ciphertext+tag>
```

P2-T02 当前重点：

- CredentialService encrypt / decrypt
- credential 明文不落库
- 查询 API 不返回 credential
- 错误/损坏密文受控失败
- 无 credential Deployment 仍然合法
- 加密逻辑独立测试

明确不做：

- Vault / KMS / Secret Manager
- 自动 Key Rotation
- Runtime Proxy
- Application API Key

---

## 7. 当前数据库

数据库：**MySQL 8.4**

当前 Flyway Migration：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

当前新增主要约束：

- Provider name UNIQUE
- Model name UNIQUE
- ModelDeployment name UNIQUE
- ModelDeployment.provider_id FK → Provider.id
- ModelDeployment.model_id FK → Model.id
- Provider / Model 删除使用 ON DELETE RESTRICT

P2-T02 优先复用 V4 已有 `model_deployment.encrypted_credential`，没有真实 schema 变化则不新增 migration。

---

## 8. 当前 API Error Contract 增量

在 Phase 1 错误码基础上，P2-T01 新增：

```text
400
INVALID_REQUEST

404
PROVIDER_NOT_FOUND
MODEL_NOT_FOUND
MODEL_DEPLOYMENT_NOT_FOUND

409
PROVIDER_NAME_ALREADY_EXISTS
MODEL_NAME_ALREADY_EXISTS
MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS
RESOURCE_CONFLICT
```

P2-T02 如产生新的受控 credential 错误语义，应在任务完成收尾时按实际实现更新，不提前虚构。

---

## 9. Security

当前真正已实现的管理面安全方案仍为：

```text
/api/**
→ HTTP Basic
```

P2-T02 增加的是 Provider Credential 的存储安全，不等同于 Runtime Application API Key Authentication。

Phase 2 计划中的 `/v1/**` Application API Key 认证尚未实现。

---

## 10. Testing

当前继续使用：

- Spring Boot Test
- MockMvc
- Spring Security Test
- Testcontainers
- MySQL 8.4

P2-T01 集成测试已覆盖 Model Registry 核心场景。
P2-T02 应重点补充 credential 加密/解密与“不暴露明文”相关测试。

---

## 11. 当前架构

当前仍然是：

**单体应用 + 模块化代码组织**

当前主要模块：

```text
team
employee
application
provider
model
deployment
common
config
security
```

当前没有拆微服务，也没有引入 Redis / MQ / Nacos / Spring Cloud。

---

## 12. 当前技术债

### TD-001 HTTP Basic 是临时管理面认证方案

Phase 2 后续会为 Runtime 引入 Application API Key，但管理面的最终身份体系仍未确定。

### TD-002 暂无 Role / Permission

等真实授权需求出现后再实现。

### TD-003 Service 暂无统一事务设计

当前主要操作仍以单表写为主。出现真实多表原子操作后再设计事务边界。

### TD-004 跨模块存在少量 Mapper 依赖

例如 Employee / Application 使用 TeamMapper，ModelDeploymentService 使用 ProviderMapper / ModelMapper 做存在性检查。

当前保持简单；跨模块规则复杂后再考虑更清晰边界。

### TD-005 Error Code 使用字符串

错误码规模扩大后考虑统一管理。

### TD-006 ApiErrorResponse 可观测性不足

当前仍缺少 traceId / requestId / path。

### TD-007 当前唯一约束基于单 Organization 假设

未来 Multi-Tenant 时重新评估唯一约束范围。

### TD-008 当前测试覆盖关键链路，不追求完整覆盖率

后续随着业务复杂度增长逐步补充。

### TD-009 暂无 CI Test Gate

当前测试主要通过本地 `./mvnw test` 执行，尚未建立 GitHub Actions 自动测试门禁。

---

## 13. P2-T01 Acceptance Result

P2-T01 收尾结果：

- [x] Provider schema / CRUD
- [x] Model schema / CRUD
- [x] ModelDeployment schema / CRUD
- [x] Provider 与 Model 不直接绑定
- [x] ModelDeployment 同时关联 Provider 与 Model
- [x] Flyway V4 生效
- [x] name UNIQUE 约束
- [x] FK + ON DELETE RESTRICT
- [x] ProviderType 当前只支持 OPENAI_COMPATIBLE
- [x] 非法 ProviderType 请求返回 400 INVALID_REQUEST
- [x] 关键集成测试已补充
- [x] 已通过开发导师 Code Review

结论：

**P2-T01 — Model Registry Schema：COMPLETED**

---

## 14. Current Execution Gate

**P2-T02 — Provider Credential Protection：ACTIVE**

完成实现、测试和 Code Review 后，再回到总控进行 P2-T02 收尾；未经过用户确认不得进入 P2-T03。

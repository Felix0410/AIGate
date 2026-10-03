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
→ NOT STARTED
```

当前不会自动进入 P2-T02，等待用户确认。

---

## 3. 当前系统形态

AIGate 仍采用：

**单体应用 + 模块化代码组织**

当前没有拆分微服务。

原因：

- 仍处于单节点、单数据库阶段
- 尚未出现独立扩缩容需求
- 尚未出现服务间通信问题
- 尚未出现分布式一致性问题
- 当前重点是先打通 AI Gateway 主流程

当前阶段优先保证：

- 业务闭环
- 数据一致性
- 可维护性
- 可测试性
- 架构可演进但不过度设计

---

## 4. 当前整体架构

当前已经实现的主链仍是管理面 CRUD：

```text
Client
  ↓
Spring Security Filter Chain
  ↓
Spring MVC Controller
  ↓
Service
  ↓
MyBatis-Plus Mapper
  ↓
MySQL
```

当前新增 Model Registry：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

Phase 2 后续目标运行时链路尚未实现：

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

---

## 5. 当前模块划分

当前主要业务模块：

```text
team
employee
application
provider
model
deployment
```

基础模块：

```text
common
config
security
```

各业务模块继续采用简单结构：

```text
controller
dto
entity
mapper
service
```

当前没有额外引入：

- Repository 抽象层
- Domain Service
- CQRS
- DDD Aggregate
- Event Bus

原因仍然是当前业务复杂度不足以支撑这些额外抽象成本。

---

## 6. Phase 1 Identity Model

Phase 1 已完成：

```text
Team
├── Employee
└── Application
```

Employee 仍是业务人员实体，不等同于登录账号。

Application 仍表示未来调用 AIGate 的机器应用身份主体。

---

## 7. P2-T01 Model Registry

### 7.1 Provider

Provider 表示模型服务提供方 / 协议类别。

当前实际支持：

```text
ProviderType.OPENAI_COMPATIBLE
```

当前 Provider 不保存 endpoint / credential 等部署级运行配置。

### 7.2 Model

Model 表示逻辑模型。

当前重要设计：

> Model 不直接属于 Provider。

原因是同一个逻辑模型未来可以由不同 Provider 承载。

### 7.3 ModelDeployment

ModelDeployment 表示真正可调用的模型部署实例。

关系：

```text
Provider 1 ---- N ModelDeployment
Model    1 ---- N ModelDeployment
```

当前字段已经包含：

```text
endpointUrl
remoteModelName
encryptedCredential
enabled
```

其中 `encryptedCredential` 只是 schema 预留；真正 Credential 加密/解密逻辑属于 P2-T02，尚未实现。

### 7.4 为什么运行时最终选择 Deployment

真正影响调用的是：

```text
endpoint
remote model identifier
credential
enabled
```

这些都属于具体部署实例，而不是逻辑 Model。

因此未来 Routing 的真实目标也会是 ModelDeployment。

---

## 8. 数据库设计原则

当前数据库使用 MySQL 8.4，Schema 通过 Flyway 管理。

当前 migration：

```text
V1 → Team
V2 → Employee
V3 → Application
V4 → Provider / Model / ModelDeployment
```

已执行 migration 不允许修改。

数据库继续负责最终数据完整性：

```text
UNIQUE
FOREIGN KEY
NOT NULL
```

P2-T01 新增：

```text
model_deployment.provider_id
→ provider.id
→ ON DELETE RESTRICT

model_deployment.model_id
→ model.id
→ ON DELETE RESTRICT
```

正常业务路径仍采用：

```text
Service 预检查
+
Database Constraint 最终保护
```

---

## 9. API 层

当前实际管理 API 新增：

```text
/api/providers
/api/models
/api/model-deployments
```

仍全部受 Phase 1 HTTP Basic 保护。

Phase 2 计划中的：

```text
/v1/**
→ Application API Key
```

尚未实现。

---

## 10. Validation 与错误处理

继续使用 Jakarta Bean Validation 和 GlobalExceptionHandler。

P2-T01 新增了 JSON 反序列化错误语义：

```text
HttpMessageNotReadableException
↓
400 INVALID_REQUEST
```

典型场景：

```text
ProviderType = NOT_A_VALID_TYPE
```

因此目前可区分：

```text
VALIDATION_ERROR
→ DTO 字段校验失败

INVALID_REQUEST
→ 请求体无法反序列化为有效请求模型
```

---

## 11. Spring Security

当前真正已实现的认证仍是：

```text
/api/**
→ HTTP Basic
```

公开范围：

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

当前还没有：

- Runtime API Key Authentication
- Role / Permission
- JWT
- OAuth2 / OIDC

后续 Phase 2 会让管理面和运行时安全边界开始分化，但当前事实仍只有管理面 HTTP Basic。

---

## 12. Jackson

项目基于 Spring Boot 4 / Jackson 3。

当前继续使用：

```text
tools.jackson.databind.json.JsonMapper
```

P2-T01 中 ProviderType 非法枚举值会在 Jackson 反序列化阶段失败，再由 GlobalExceptionHandler 转换为 `400 INVALID_REQUEST`。

---

## 13. 集成测试架构

当前测试仍是：

```text
MockMvc
↓
Spring Security
↓
Controller
↓
Validation / JSON Binding
↓
Service
↓
MyBatis-Plus
↓
Real MySQL Testcontainer
```

P2-T01 新增 Provider / Model / ModelDeployment 集成测试，重点验证：

- 正常创建
- 唯一性冲突
- ProviderType VARCHAR 往返
- 非法 ProviderType JSON
- Deployment 外键存在性
- Deployment validation
- FK RESTRICT 删除冲突

---

## 14. 当前架构演进过程

```text
Phase 1
Identity Foundation
Team / Employee / Application
↓
Validation / Exception Contract
↓
HTTP Basic
↓
Testcontainers
↓

Phase 2 / P2-T01
Model Registry
Provider / Model / ModelDeployment
↓
建立未来模型调用目标的领域边界
```

当前还没有真正发生：

```text
Credential Protection
Application API Key
Runtime Authentication
Provider Adapter
Outbound HTTP Proxy
```

这些仍然是后续任务，不应写成当前事实。

---

## 15. 当前技术债

### 15.1 HTTP Basic 是临时管理面认证方案

后续 Runtime 会出现 Application API Key，但最终管理身份体系仍需后续需求驱动。

### 15.2 当前没有 Role / Permission

等真实授权需求再加入。

### 15.3 Service 没有统一事务设计

当前以单表操作为主，多表原子写出现后再明确事务边界。

### 15.4 跨模块存在少量 Mapper 直接依赖

当前包括：

```text
Employee / Application → TeamMapper
ModelDeploymentService → ProviderMapper / ModelMapper
```

当前保持简单；跨模块业务规则复杂后再演进。

### 15.5 错误码仍使用字符串

错误码规模扩大后考虑集中管理。

### 15.6 ApiErrorResponse 缺少 traceId / requestId / path

后续 Observability 阶段处理。

### 15.7 当前唯一约束基于单 Organization 假设

未来 Multi-Tenant 时重新评估。

### 15.8 测试只覆盖关键链路

当前不追求覆盖率数字。

### 15.9 暂无 CI Test Gate

当前仍缺少 GitHub Actions 自动测试门禁。

---

## 16. 当前仍不做微服务

当前继续保持模块化单体。

只有出现真实问题时才考虑拆分，例如：

- 独立扩缩容需求
- 不同运行时资源模型
- 发布节奏必须独立
- 单体耦合真正成为维护障碍

拆分前必须有清晰问题证据和 ADR。

---

## 17. 当前架构结论

P2-T01 完成后，AIGate 已从单纯 Identity Foundation 扩展到具备第一版 Model Registry：

```text
Identity
+
Model Registry
+
稳定数据库约束
+
管理 API
+
集成测试
```

下一步计划是 Provider Credential Protection，但当前尚未启动。

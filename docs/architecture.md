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
```

Model Registry：

```text
Provider ───┐
            ├── ModelDeployment
Model ──────┘
```

Phase 2 当前新增运行时配置关系：

```text
Application
  ↓ defaultDeploymentId
ModelDeployment
```

这意味着 Runtime Proxy 后续可以从 Application 直接确定当前阶段的唯一调用目标。

---

## 4. 为什么 Application 直接绑定 ModelDeployment

当前真实需求只有：

> 一个 Application 先能够调用一个确定的模型部署。

因此当前最简单方案是：

```text
Application.defaultDeploymentId
```

而不是提前建立：

```text
ModelAlias
Route
RoutingRule
Weighted Target
```

当前方案收益：

- 实现简单
- Runtime 解析链路清楚
- 能先打通 single-model proxy
- 不提前承担 Routing 复杂度

代价：

- Application 与具体 Deployment 暂时耦合
- 暂时不支持动态路由、多 Deployment、灰度和权重

这是有意识的阶段性架构。后续出现 Routing 真实需求后，再演进为：

```text
Application
↓
ModelAlias / Route
↓
ModelDeployment
```

---

## 5. Application Default Deployment 行为

`defaultDeploymentId` 允许 NULL。

原因：

```text
Application 可以先注册
↓
模型配置稍后完成
```

创建 / 更新时，如果传入非 NULL Deployment：

```text
ApplicationService
↓
ModelDeploymentMapper.selectById
↓
不存在 → MODEL_DEPLOYMENT_NOT_FOUND
↓
存在 → 保存 FK
```

当前 PUT 是完整更新，因此：

```text
PUT defaultDeploymentId = null
→ 解绑当前默认 Deployment
```

Entity 使用 `FieldStrategy.ALWAYS`，确保 null 真正写回数据库。

---

## 6. 数据完整性

V5 建立：

```text
application.default_deployment_id
→ model_deployment.id
→ ON DELETE RESTRICT
```

数据库负责最终保证：

- Application 不会引用不存在的 Deployment
- 被 Application 引用的 Deployment 不能直接删除

Service 负责提前提供明确的 `MODEL_DEPLOYMENT_NOT_FOUND`。

仍然保持：

```text
Service business precheck
+
Database constraint final protection
```

---

## 7. Provider Credential Protection

P2-T02 的安全边界继续有效：

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

## 8. 当前 Security

已经实现：

```text
/api/**
→ HTTP Basic
```

尚未实现：

```text
/v1/**
→ Application API Key
```

所以当前 `Application.defaultDeploymentId` 只是运行配置关系，不代表 Runtime 身份认证已经完成。

---

## 9. 当前测试架构

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

P2-T03 测试验证：

- null 默认 Deployment
- 正常绑定
- 不存在 Deployment 404
- 切换 Deployment
- null 解绑
- FK RESTRICT 删除冲突 409

GitHub 当前仍无 CI Test Gate。

---

## 10. 当前架构演进

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
```

下一步才是：

```text
P2-T04 Application API Key Lifecycle
```

不要提前进入 Runtime Authentication 或 Routing。

---

## 11. 当前技术债

继续保留 TD-001 ~ TD-010。

`Application.defaultDeploymentId` 不单独视为缺陷，而是当前阶段为了先打通 single-model proxy 采用的明确临时设计。Routing 阶段出现时需要重新评估并迁移。

# AIGate Domain Model

## 现有业务对象
```text
Team
├── Employee
└── Application
    ├── 1:N ApplicationApiKey
    └── defaultDeploymentId → ModelDeployment

ModelDeployment
├── providerId → Provider
└── modelId → Model
```

- **Team**：当前组织归属的基础单位；Employee 与 Application 属于 Team。
- **Application**：消费 AI 能力的运行时业务身份；默认部署从 `defaultDeploymentId` 解析。
- **ApplicationApiKey**：Application 的可撤销机器访问凭证（keyId、Secret hash、状态），一个 Application 可有多把 Key。
- **Provider**：外部模型服务提供方，当前支持 OPENAI_COMPATIBLE 类型。
- **Model**：逻辑模型目录项，不等同于某个可访问 HTTP 地址。
- **ModelDeployment**：真正可调用的部署配置；包括 endpointUrl、remoteModelName、enabled 与可选 encryptedCredential；关联 Provider 和 Model。

## 为什么分开
Application 与认证 Key 分离，使撤销一个 Key 不需要删除 Application；逻辑 Model 与 Deployment 分离，使同一逻辑模型能够拥有独立的供应商和出站配置；Provider 与 Deployment 分离，使供应商身份不等于具体可调用实例。

目前一个 Application 有单个默认 Deployment，不等同于已实现路由/负载均衡。应用在每次 Invoke 时从数据库读取当前默认目标，因此变更后的下一次调用生效。

## 数据与安全约束
详见 `database-design.md` 中 V1–V6、唯一约束及 FK；入站 Key hash 和出站 Credential AES-GCM 作用不同，Provider Credential 不属于 Application API Key。

---

## Phase 3 Domain — InvocationRecord（已实现）

```text
Application
  └── 1:N InvocationRecord (implemented; ON DELETE CASCADE)
         ├── SUCCESS / FAILED
         ├── safe errorCode (nullable)
         ├── durationMs
         └── createdAt
```

InvocationRecord 是**管理排障历史元数据**，不是 Prompt Conversation、计费 Ledger 或 Provider HTTP 原文。一个 Application 的多次有效 Invoke 可以形成多条记录；失败也要记录，但如果持久化本身失败，可以丢失本条，不覆盖真实业务结果。P3-T02 建表，P3-T03/05 写入，P3-T04 查询，P3-T06 清理。

当前生产代码已实际使用 InvocationRecord：Flyway V7 + SUCCESS/FAILED 元数据记录、管理查询、30 天清理；**Phase 3 CLOSED / ACCEPTED（2026-10-20）**。


InvocationRecord 仅是 Application 下属的排障元数据，写入为同步 best-effort，允许丢失历史、不会改变原模型响应。既不是 Prompt 对话记录，也不是可用于收费的审计账本。

---

## Phase 4 Planned Domain — ApplicationRateLimitPolicy（2026-10-20，未实现）

```text
Application (existing)
 ├── id / name / teamId / defaultDeploymentId
 ├── requestsPerMinute: Integer?  [P4 V8 PLANNED]
 │      ├── null        → unlimited
 │      └── 1..10000    → TokenBucket policy
 └── multiple ACTIVE ApplicationApiKeys → same Application bucket

InMemoryTokenBucket (transient single-JVM execution state, NOT DB entity)
 └── applicationId → capacity / tokens / lastRefillNanos / lastAccessNanos
```

业务身份是 **Application**，不是 API Key：同一 Application 的多把 Key 共享请求额度。持久化的是管理员配置而不是消费数据；服务重启桶被重新填满，多实例各自计数。被429拒绝的请求未进入 ModelProxyService，不产生 Phase 3 InvocationRecord。既有 Application CRUD / P3 Invoke 记录语义不变。

**设计已批准但代码未实施，P4-T01 ACTIVE。** 不创建 Quota/Usage/Billing/RateLimitCounter 域对象。完整 ADR：`docs/adr/ADR-004-application-rate-limiting.md`。

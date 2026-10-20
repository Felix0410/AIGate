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


## Enterprise Domain Mapping — User Confirmed Direction（2026-10-20，尚未编码）

```text
Employee ── primary Team
         └─ participates temporarily in other Team tasks (details pending)

Existing Application 1:1 Managed Agent (logical target)
  └─ Application API Key authenticates Agent, NOT end employee

Invocation: trusted Employee identity + Agent identity + optional Task/Team context
  └─ selected logical Model → route among deployments of SAME Model

Weekly allowances (target; not tables yet):
  Employee authorized Agent free allowance
  → Employee personal allowance
  → approved Team extra allocation
```

Agent 由管理员纳管但不由 AIGate 开发/构建；自动任务可以专用账号独立归属。关键安全设计：现有 Application Bearer Key 只能证明 Agent/Application 身份，**不能自动证明具体 Employee 身份**；未确认可靠的员工认证/委托方式前，不允许直接相信调用者随意提交 employeeId 进行扣费。额度、参与团队与结算单位尚需继续确认；不提前增加 Schema 或假定 Employee 是登录 User。详细事实：`docs/product-business-rules.md`。


## Confirmed Weekly Points Account Ownership（2026-10-20，业务概念，未建表）

```text
Employee Identity (independent personal credential)
 + Managed Agent / Application Identity (Application API Key)
     -> allow model call only after both are authenticated and authorized

Employee x Authorized Agent -> independent free weekly points
Employee -> personal basic weekly points
Employee -> temporary extra grant(s) from Team pool
Team -> grant record (on request or proactive, own expiry)
Model -> per-token price converted to internal points
```

消费顺序是 Agent 免费点数→个人本周基础点数→员工已获发放的有效补充点数。**团队在“发放额度”时承担资源调配责任，正常模型扣减只需面对员工可用额度，不再次请求每次调用的团队费用承担人。** 来源和发放交易仍须可追溯与防重复扣账。周起点/有效期未用返还/并发消耗预留、员工账号鉴权机制尚待下一层决定。Agent/Application 一对一，当前 API Key 只鉴别 Agent，员工个人凭证需后续开发。无生产 schema 更改，P4 PAUSED。


## Quota Ownership and Billing Rules Update — 2026-10-20 (Target Only)

**周刷新**：公司统一时区每周一 00:00；每员工×Agent 免费额度和员工个人基础额度周重置、不结转。公司具体时区待定。

**额度发放**：TeamQuotaPool → EmployeeExtraGrant，是团队发放时立即划扣团队积分与员工获得额外有有效期积分的一次业务交易。到期未用积分失效、**不返还**团队池；团队支出与员工实际消费分别展示，不能重复记作模型成本。

**计价**：ModelPrice 具备输入/输出等 Token 价差，最终换算内部 Points；UsageSettlement 目标状态包括预留、确认结算、未知用量待核对，而非仅 P3 SUCCESS/FAILED。

**余额耗尽**：EmployeeQuotaStatus 将转入最低速率/最低优先级并提醒，不能简单拒绝所有已授权调用；**保底资金是谁提供、保底池总量和模型/并发限制仍待业务决策**，暂不建余额或调度 Schema。

这些是已确认产品规则及待决边界，不是当前已实现领域实体或表。Agent/Application 1:1 和 Employee 双身份凭证原则不变。P4 PAUSED。


## Confirmed Budget Exhaustion Decision（2026-10-20，目标领域语义、未建表）

Employee/Agent 普通积分（Agent 周免费 → Employee 周基础 → Team 已发放有效补充）耗尽后由 Corporation Fallback Budget 在员工周/公司池双重上限内承担，使用 Agent 已获准的保底逻辑模型并进入最低速率/优先级档。

**再耗尽时**：Paid Provider Invocation 无有效、已预留资金来源则硬停止；如果拥有独立成本/容量边界且被允许的 Self-hosted Base Model 可保持受控的有限基本服务；Emergency Budget 是另一个公司明确预留且有限额的资金来源，必须基于已审批紧急任务+有效期+使用者等授权才能释放。没有批准资金或合规容量时不可无限调用。团队发放、模型实际成本、企业保底和应急消费应各自有可追溯账目，不把 P3 best-effort 调用记录当财务余额事实。

**本节为批准的业务规则，不指定数据表、外键、锁、队列或微服务结构。** P4 仍是设计审查前准备，旧限流 P4-T01 PAUSED。


## Enterprise Governance and FinOps Domain Extensions — Approved Business Boundaries（2026-10-20，目标未实现）

新增的两项用户确认领域概念，**不代表已创建这些 Entity 或数据库表**：

```text
Employee + Agent(Application) + Allowed Model + Request Context
  ├─ Agent/Model Authorization: may this identity call the model? (P4)
  └─ GovernancePolicy (policyId, version, dataClass, scope)
       ├─ SafetyDecision (ALLOW / BLOCK / REDACT candidate)
       └─ SecurityAudit (decisionId, traceRef, ruleVersion, safeMetadataOnly)

ModelUsage + ActualTokenUsage + PriceVersion
  ├─ QuotaLedger / Reservation / Settlement: authoritative points movement
  └─ CostAnalytics Projection: per Team/Agent/Model/Deployment aggregation
       ├─ ProviderCostEstimate (cost currency, price source/version)
       ├─ BudgetBurn / CostTrend
       └─ AnomalyAlert
```

**所有权边界**：Identity/Control 决定身份和基础 Agent/Model 授权；AI Governance 为企业数据和模型风险规则的发布/审批与审计责任域；Gateway 执行最小必要安全策略，默认不跨服务传原始 Prompt；Quota/Billing 单独负责员工积分和真实 Token 最终结算；FinOps 只从权威已确认数据维护允许延迟的聚合投影，不回写积分余额。供应商侧成本初期是计价估算，不等于供应商发票。

**微服务拆分仅是候选**：这些稳定的代码/数据责任可以先放在一个模块化单体里，真实需要独立扩容、规则发布、故障隔离/异步分析时才提出 ADR 并抽取。P4-T01 PAUSED，不开始新安全/财务 schema。

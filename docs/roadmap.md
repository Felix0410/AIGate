# AIGate Roadmap

> **最新有效状态（2026-10-20）**：用户已批准 P4～P10 **顶层业务开发顺序**，但没有批准任一新 Phase 开始编码。下方早期 Application 级限流 P4 方案已暂停，仅为历史草案。以下“Enterprise AIGate Product Roadmap — DRAFT”章节所列七阶段正式改为 **APPROVED HIGH-LEVEL BUSINESS ORDER / NOT YET STARTED**；各 Phase 的 Scope、交付包、技术决策和验收尚未冻结。总控负责 docs，开发导师不得按旧 P4 执行。


> 以实际交付为准。**已实现、已验收、计划/候选**必须明确区分。项目以业务问题驱动架构演进。

## Phase 1 — Foundation & Core Identity

**Status: CLOSED**

Team / Employee / Application、Spring Boot + Spring MVC + MyBatis-Plus + MySQL/Flyway 基础、Validation、异常处理、开发期 HTTP Basic 管理认证、MockMvc + Testcontainers。

## Phase 2 — Model Registry & Single Model Proxy

**Status: CLOSED / ACCEPTED（2026-10-18）**

P2-T01～T11 全部完成并验收。具体关闭依据：`docs/phase-2-closeout.md`。

实际交付：

```text
Provider / Model / ModelDeployment 管理
→ Provider Credential AES-GCM 加密
→ Application.defaultDeploymentId
→ Application API Key 创建 / 撤销 / Hash 校验
→ /v1/** 独立 Spring SecurityFilterChain
→ InvokeRequest / InvokeResponse
→ ProviderAdapter 转 OpenAI-Compatible JSON
→ RestClient 同步真实出站模型调用
→ ProxyErrorType 稳定、脱敏失败映射
→ 应用级 E2E 测试（Testcontainers MySQL + Mock Provider）
```

用户提供本地 Maven 汇总：**138 tests, 0 failures, 0 errors, 0 skipped**；**不是 GitHub CI**。当前数据库仍为 Flyway V1–V6。

Phase 2 不包含 Redis、MQ、微服务、SSE、自动重试、多模型路由、配额、账本或收费。

## Phase 3 — Invocation Records & Basic Visibility

**Status: CLOSED / ACCEPTED（2026-10-20）**

**总控已于 2026-10-18 冻结业务目标**：允许管理员按 Application 查询最近模型调用的 SUCCESS/FAILED、固定错误分类、耗时及时间，解决 Phase 2 已能代理模型但缺少业务调用历史的问题。

**选定方案：** Flyway V7 一张 MySQL `invocation_record` 表，最小元数据同步 best-effort 写入；管理面只提供有上限的最近记录查询。设计取舍见 `ADR-003-invocation-recording-persistence.md`。不存原始 Prompt、模型回复、任何 API Key / Provider Secret 或原始上游错误。保留 30 天，后续做简单清理。

| Task | Deliverable | Status |
|---|---|---|
| P3-T01 | 总控冻结业务目标、范围、数据/API 契约、架构与 ADR | **DONE / ACCEPTED** |
| P3-T02 | V7 invocation_record + Entity/Mapper + DB Tests | **DONE / ACCEPTED** |
| P3-T03 | 成功 Invoke 记录与写入失败隔离 | **DONE / ACCEPTED** |
| P3-T04 | GET /api/applications/{id}/invocations?limit=20 | **DONE / ACCEPTED** |
| P3-T05 | 失败 Invoke 记录 + 30 天清理 + 全链路 E2E（合并原 T05/T06） | **DONE / ACCEPTED** |
| P3-T06 | Phase 3 Closeout（原 T07，编号调整） | **DONE / ACCEPTED** |

**P3-T02 已验收：本地 Maven 测试 149/0/0/0 且 BUILD SUCCESS（非 CI）。P3-T03 成功自动记录与 P3-T04 管理端调用历史查询均已验收。下一步 P3-T05 一次完成失败调用记录、30 天清理和完整 E2E；P3-T06 统一收尾。**

**明确不做：** 计费/用量账本、聚合仪表盘、Retry、SSE、多模型路由、Redis、MQ、ES、微服务、全量 Prompt/Response 存储。

## 保留的生产化/工程风险

- **TD-001**：管理面 HTTP Basic 只是当前开发阶段的安全基线。
- **TD-009**：尚无持续集成的自动测试门禁。
- **TD-011**：Provider endpointUrl 的 SSRF / 出站网络目标限制必须在公网或不可信管理者场景落实。
- **TD-010**：Provider Master Key 轮换/应急方案尚未设计。
- 其余技术债详见 progress / Phase 2 closeout。

这些风险不随 Phase 2 CLOSED 自动消失；是否属于 P3 当前核心功能应由业务目标及安全部署约束决定。


### 2026-10-18 Task Granularity Adjustment

用户明确反馈此前拆分过细、单任务复杂度过低。总控已将相邻业务功能合并：原 P3-T05（FAILED）与 P3-T06（30 天清理/E2E）合并为 **P3-T05 Invocation Record Completion（ACTIVE）**；原 P3-T07 Phase Closeout 调整为 **P3-T06**。这样减少中间提交/验收/文档切换，同时保留失败记录、生命周期及 E2E 的独立验收条件。开发导师在 P3-T05 内按顺序完成所有子项，无需中途回总控验收。具体任务边界以最新 `docs/current-phase.md` 为准。


### P3-T05 验收与阶段推进（2026-10-18）

8262303 实现 FAILED 记录及 UTC 03:00 的 30 天清理，dfcc70a 补充修复测试。静态代码与测试设计审查通过；P3-T05 DONE / ACCEPTED。GitHub 无 CI 记录，本次无新增 Maven 总量统计，待 P3-T06 Closeout 复核。阶段收尾统一检查功能、测试可靠性、架构、风险、文档、知识点与面试表达，**Phase 3 尚未 CLOSED，Phase 4 尚未冻结/启动**。


### P3-T06 收尾复核进展（2026-10-20）

`f3b0f94d6fbd79eb4305eeea7e2ec5fcd4c69c98` 已隔离 Provider timeout 测试并把 60s 临界竞态改为 1s read timeout / 3s Mock 延迟，Retention 测试已清理 Team/Application；提交仅涉及测试文件。剩余需修复 timeout 测试 Provider/Model/Deployment 的固定名称资源未释放问题，再形成最终 Phase 3 Closeout 报告。**P3-T06 ACTIVE；Phase 3 未 CLOSED；Phase 4 未启动。**


### P3-T06 收尾审查完成，待总控关闭确认（2026-10-20）

最终提交 `a2a68fe6b137aba78fdb359b7cd781c0fd228681` 解决独立超时测试在正常运行与断言失败后资源清理不全的问题：@AfterEach 按 Application→Deployment→Model→Provider→Team 删除并校验 HTTP 状态；原超时与记录断言保留。阶段报告见 `docs/phase-3-closeout.md`。功能/架构/测试代码静态评审无阻塞问题，CI 和精确本轮 Maven 汇总仍未独立取证。**P3-T06 待总控验收，Phase 3 暂未 CLOSED，Phase 4 NOT STARTED。**


## Phase 3 正式关闭 — 2026-10-20
P3-T01～T06 全部完成并经总控验收。最终业务交付与风险见 docs/phase-3-closeout.md。缺最终独立 Maven/CI 运行结果，不能使用较早的149项测试统计代替；TD-009仍开放。

## Phase 4 早期候选（历史，已被正式启动决策取代）
候选核心业务：**Application 级请求速率保护**，阻止单个 Application 短时间过量请求占用网关/Provider 能力。优先现有模块化单体的最简单正确方案，只有出现实测跨实例一致性问题时才论证 Redis。计划按“一个完整限流业务交付包 + 统一集成回归与阶段收尾”组织，不再拆多个低复杂度微任务。具体算法、表/API、限流边界和验收在正式启动 Phase 4 前由总控冻结；本节尚非 Phase 4 启动指令。

---

## Phase 4 — Application Rate Limiting（ACTIVE，2026-10-20）

**Business Goal：** 为每个 Application 配置独立的模型调用速率策略；网关在实际出站模型请求前原子消费令牌，超过额度返回本地 HTTP 429 APPLICATION_RATE_LIMITED（零 Provider 出站/零 InvocationRecord），保障单实例网关和上游资源。并不是精确配额或计费系统。

**当前选择：** MySQL V8 `application.requests_per_minute INT NULL` 只存策略（NULL 不限流）；单 JVM `ConcurrentHashMap.compute` + `System.nanoTime` 的令牌桶存实时令牌；管理 Basic 的 GET/PUT `/api/applications/{id}/rate-limit`；拒绝响应 Retry-After。选择过程与代价见 ADR-004。

**Frozen Tasks（按完整交付包，不拆微任务）：**

| Task | 完整交付 | 状态 |
|---|---|---|
| **P4-T01 — End-to-End Application Rate Limiting** | V8 Schema、管理策略 GET/PUT、Java 令牌桶原子并发、Runtime 429、内存回收、MockMvc+MySQL+Provider E2E、回归 | **ACTIVE** |
| **P4-T02 — E2E Hardening & Phase 4 Closeout** | 综合 Code Review、并发/测试稳定性修正、真实 Maven 回归、架构/技术债/文档/知识/面试总结、关闭结论 | NOT STARTED |

本阶段保持单实例正确性，**不得宣称多实例全局限流**；不做 Redis/MQ/ES、计费账本、日报配额、流式、路由、Nacos、微服务。请求令牌状态不落库，重启将重置桶。Phase 3 的 SUCCESS/FAILED 记录仍只覆盖实际进入 ModelProxyService 的业务调用。

正式实现合同见 `docs/current-phase.md`；**P4-T01 ACTIVE / P4-T02 NOT STARTED / Phase 5 NOT STARTED**。

Phase 4 默认保留旧 Application 的不限流行为，管理员主动配置 N RPM 才触发限制；当前已设计未编码的 V8/接口/算法不应写成实现事实。


## 2026-10-20 — 全局业务路线重新核对（优先级覆盖上方 P4 计划）

**P4-T01 DEVELOPMENT PAUSED**。当前最新的有效任务是：总控与用户先确认 AIGate 最终产品定位与整体端到端业务流程，随后逐层确认主体关系（用户/Team/Application）、额度归属（用户/组织/应用）、真实调用计量与治理顺序，以及“限流”和“额度扣减”的不同业务目标。此前 P4 单实例令牌桶设计视为**备选草案**，不代表最终 Phase 4 功能优先级；不得继续编码 V8/RateLimit API 直到产品路线确认。Phase 1～3 的已验收事实不变。后续整包任务按完整业务能力拆分。


## Enterprise AIGate Product Roadmap — DRAFT FOR USER REVIEW（2026-10-20）

> 本节是根据 **docs/product-business-rules.md** 所收敛需求，重新提出的下一阶段 **候选业务顺序**。**未得到用户批准、未开始执行**；旧 Phase 4 Application RPM token-bucket 方案和 ADR-004 继续 ON HOLD。P1～P3 CLOSED 的既有代码事实不变。每阶段的详细范围、任务包、技术方案需用户确认后由总控再冻结。

- **P4 候选 — 可信员工身份 + Agent 接入规则**：保留 Application/Agent 1:1，补员工独立凭证与 Agent Key 双身份、员工 Agent 权限、逻辑模型选择及准入，从已认证调用到真实 Provider 的可验证闭环。先使模型调用归属真实员工，避免按不可信 employeeId 扣费。
- **P5 候选 — 模型计量、内部积分与个人周额度**：Adapter 采集真实 Token usage，模型价格与版本、可靠用量/计费流水、每员工×Agent 免费周额度+员工基础周积分、调用前预留与完成后结算/失败待核对。先跑通最简真实结算闭环，再加强并发一致性。
- **P6 候选 — 团队额度池、额外发放和审批**：员工申请/团队主动发放，团队池发放时立即扣、员工额外积分有到期日、到期未用不返还，审批/发放流水可审计，兼容主团队和临时协作团队身份。
- **P7 候选 — 企业保底预算与三级服务档位**：正常积分耗尽降限流档，自动选择 Agent 预设允许的低成本保底逻辑 Model；每员工每周企业预算限额、低速率/低并发/最低优先级/提醒，有界等待和平台自己控制的基础容量；普通档基础治理。额度彻底耗尽规则先由业务确定。
- **P8 候选 — 同模型部署路由与可观测性**：对同一逻辑模型的多个部署观测健康、负载、延迟、错误及部署成本，按策略权重路由；先交付真实运行指标、告警和可验证的人工容量调整依据，再完善历史与容量趋势分析。
- **P9 候选 — 紧急任务资源保障**：按具体任务申请并审批临时额外额度/紧急档、有效期/撤销，调度保证高优先请求更早获取新释放的入口容量，兼顾最低档基础服务；测试公平性、成本和故障回归。
- **P10 候选 — 生产可靠性与容量演进**：管理身份生产化、模型出站 SSRF/网络安全、密钥轮换、CI、细粒度告警、历史容量分析/预测、分布式部署后的共享额度/路由一致性按真实压力选择技术，持续补强性能和故障恢复。**不预设 Redis/MQ/微服务一定引入**。

目标流程：管理员纳管 Agent/模型与策略 → 员工使用双凭证经网关调用 → 权限/额度/服务档位 → 已选逻辑 Model 的 Deployment 路由 → Provider → 可确认 Token 计费与积分账务 → 通知、审批、监控与容量治理。

在全部用户未确认的业务边界（保底总预算/耗尽行为、紧急任务归属与认证、未知用量对账、内部积分精度、不同部署价格与自动切换安全限制）定稿前，**不要启动 P4 或引导开发导师执行上述候选**。每 Phase 打通 Happy Path、测试验收与正式 Closeout 后再进入下一 Phase。


## 2026-10-20 — User Approval of Enterprise Business Roadmap

**正式决议：** 用户选择 approve，批准在保持 Phase 1~3 CLOSED 的前提下，按 **P4 可信员工身份/Agent 规则 → P5 模型计量与个人周额度 → P6 团队额度池/审批 → P7 保底与三级治理 → P8 同模型部署路由/监控 → P9 紧急任务保障 → P10 可靠性与必要演进** 的业务顺序推进产品规划。

**执行区分：** 顶层路线 APPROVED ≠ P4 ACTIVE；**P4-T01 继续 PAUSED / NO CODING**。旧 V8 requests_per_minute、TokenBucket/ADR-004 不能当作当前 Phase 4 工作单。须完成剩余业务规则、由总控重写 phase scope / main flow / architecture / tasks / acceptance / knowledge，并经用户明确批准后才能重启。

**已确认的最终保底耗尽规则（后续决议覆盖此前候选）：** 未获授权的第三方付费模型新调用硬停止；如果存在经批准、安全合规、有独立使用边界的自部署基础模型则可继续受限服务；紧急任务经审批动用单独预留、有限额的应急预算。保底和应急不得突破企业总授权上限，详见 docs/product-business-rules.md 第 6 节。


## 2026-10-20 — Final Product Scope & Budget Exhaustion Policy Accepted

用户已正式接受“分级降级 → 付费硬上限 → 经授权自部署基础服务（若存在）/紧急预算审批”的企业治理终态；并已批准 P4～P10 业务顺序。**整体产品蓝图 BUSINESS SCOPE CONFIRMED**；尚待量化、配置、技术实现的细节以后按对应 Phase 确认。

**规划执行状态**：P4 仅允许进行需求、领域关系、风险、流程、任务与验收草案的逐层设计。P4-T01 旧限流编码任务仍然 PAUSED，不自动切换 ACTIVE；原 V8 Application RPM/ADR-004 保持历史备选。完整的企业身份与 Agent 权限 P4 合同需要用户另行审核批准后才允许本地 AI 开发。

**下一步设计顺序（非本轮开发授权）**：确认 Employee 凭证如何被 AIGate 独立认证、Application Key 与员工凭证如何在一次 Runtime 请求中共同验证、员工是否有该 Agent 与 Model 的使用权、现有 Team/Employee/Application 结构的最低影响演进方案；先跑通一个真实员工调用指定 Agent 允许的逻辑 Model 这一条 Happy Path，再扩展测试与安全细节。


## 2026-10-20 — Technology-First Review Gate (after product scope approval)

用户提出将项目用于 Java 后端求职简历，要求**落实技术路线后才可启动 P4 或后续开发**。顶层业务 Phase 顺序依然 APPROVED，但详细技术引入计划**尚待讨论/批准**。总控创建 docs/technology-strategy.md，依据仓库真实 pom.xml 与已确认业务，提出 MySQL 并发准确计费、Spring Security 双身份、按实测多副本引 Redis Lua、真实异步事件后选 RocketMQ/Kafka 之一、Micrometer/Prometheus/Grafana、Docker/CI/Testcontainers/压力测试等候选。

原则：不是简历堆砌，而是每个新技术都有 AIGate 真实业务问题、最简替代、性能/正确性验证和 ADR；模块化单体优先，不预设 Nacos/Spring Cloud/ES/微服务必上。**P4-T01 继续 PAUSED / NO CODING；不可因本候选草案自动新增依赖或实现。**


## 2026-10-20 — SSE Target Confirmed / Microservices Conditional Only

用户明确将 **SSE 流式模型响应**加入目标技术能力，真实使用场景为企业 Agent 网页对话与逐 Token 输出，由 AIGate 在 Provider → Agent 之间流式透传。关键验收包括断连取消、超时、连接并发、流式用量/积分最终结算与无法确认用量时对账。P4 双身份完成后安排具体的流式代理交付包，候选在 P8 模型代理/路由能力中落实，最终 Phase 归属待细化；并不意味着要立即引入 WebFlux。

企业上万人**仅是员工总量背景**，不足以直接触发微服务。技术演进按：模块化单体→单体水平多副本+共享限流/权威 MySQL 账本→实测流式连接、网关流量与管理账务存在明显不同资源需求/故障隔离问题时，再**条件式**拆 Gateway Runtime（SSE/Provider/routing）与 Management/Control Plane；账务是否独立服务二次评估，不能让多个服务任意写同一个积分表。把“需要拆分”的负载/SLO/隔离证据写入 ADR 与压测报告，若多副本单体足够则不拆，亦是合理决定。潜在微服务试验最早在 P10 可靠性/容量演进审查；**不是 P10 必交微服务**。

技术事实与依据见 docs/technology-strategy.md 第 8 节。产品 Phase 顺序不改，详细技术采用依然 DRAFT / NOT APPROVED；**旧 P4-T01 继续 PAUSED / NO CODING**。


## 2026-10-20 — Two Business Extensions Confirmed; Phase Renumbering Proposal (PENDING APPROVAL)

User has **approved in-scope**:
- Enterprise AI Governance: classification/PII/secret protective rules, employee/Agent/Model/Provider compliance policy and versioned decision audit; keep prompts/responses out of persistent audit by default.
- Enterprise Analytics & FinOps: provider usage and true provider-cost **estimation** (distinct from employee internal points and issued Team allowance); Team/Agent/Model/Deployment trends, anomalies/alerts and cost optimization suggestions; authoritative billing remains transactional source of truth.

This is **product scope approval, not unconditional microservice approval**. Propose preserving already approved **P4→P9 order**, adding two complete business Phases and moving original reliability phase:

| Candidate Phase | Business goal / scope | Technology evidence (not auto-authorized) |
|---|---|---|
| **P4** | Employee + Agent double authentication, employee-Agent/Model authorization, basic approved Provider/Model allowlist | Security chains, authenticated principal binding, permission tests |
| **P5** | Authoritative Token usage + internal points, weekly basic/Agent allowance, reservation/settlement, unknown-cost reconciliation | MySQL ACID, idempotency, concurrency testing |
| **P6** | Team grants, Team pool, proactive issuance, approval and expiry audit | DB transaction & state-machine invariants; async notifications optional |
| **P7** | Fallback corporate budget, rate/normal/emergency service-tier foundations, cap/priority/fairness | JVM concurrency first, Redis Lua if multi-instance data shows need |
| **P8** | Same-Model Deployment routing, SSE response passthrough, gateway/deployment live pressure, alerts and capacity trends | MVC async/SSE, model-aware routing, Micrometer, Prometheus/Grafana |
| **P9** | Time-boxed emergency task priority and corporate emergency pool authorization | Verified task context, fairness, queue, atomic pool reservation |
| **P10 NEW** | Enterprise AI Safety & Compliance Governance: publish data classification/secret rules; enforce before external Provider call; safe metadata audit, authorization exception workflow | rule/policy versioning, cache invalidation, security test cases; Sse content moderation scope carefully limited |
| **P11 NEW** | Enterprise AI Cost Analytics & FinOps: per Team/Agent/Model/Deployment aggregated usage, external Provider expense estimation and burn rate, threshold anomaly alerts | ledger-originated reliable event/outbox → MQ/worker when needed, idempotent projections, trend/reporting |
| **P12 (formerly P10)** | Production readiness & architecture evolution: Docker/CI, load testing, reliable observability/SSRF/security hardening; evaluate modular monolith replicas vs 3–5 service decomposition | realistic peak SSE tests, exact billing concurrency, service outage/trace, ADR on actual service boundaries |

**Security staging caveat**: P4 baseline already enforces authenticated identity + basic permitted Model/Provider policy. Full P10 content-security and corporate compliance cannot be retroactively claimed in earlier Phases; do not expose unprotected intermediate demo as "safe for company-wide production". Simple preflight rules can be integrated earlier if business demands, P10 completes governed lifecycle and audit.

**Analytics staging caveat**: foundational P3/P5 usage, P8 traffic metrics are not FinOps full cost reporting. P11 develops derived projections from reliable ledger/price source, never recalculates spend as the debit authority.

**Service decomposition remains conditional:** 5 proposed domain owners (Gateway / Identity-Control / Quota-Billing / Governance / Analytics-FinOps). Start each as modules; measure multi-instance resource/failure differences, and then independently extract only justified boundaries in P12 with dedicated ADR. Additional Resource Operations service is NOT approved; capacity control stays inside P8/Control unless measured otherwise.

**Pending decision:** User approved the two extra businesses, but **has not yet specifically approved the P10/P11 insertion and P12 renumbering**. Previous P4–P10 sequence remains last approved formal phase numbering until user says yes. P4-T01 PAUSED / NO CODING.

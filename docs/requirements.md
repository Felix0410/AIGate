# AIGate Requirements

> 本文区分已经实现的 Phase 2 能力与尚未承诺的未来需求，不将实现推断为新需求。

## 已实现 / 已验收（P2-T01～T10）

1. 管理员通过 `/api/**` 配置 Team/Application、Provider、逻辑 Model 和具体 ModelDeployment。
2. ModelDeployment 关联 Provider、Model，保存 endpointUrl（API base URL）、remoteModelName、可选加密凭证及 enabled。
3. Application 可绑定、切换或解绑一个默认 ModelDeployment；禁用/缺失应明确拒绝运行时调用。
4. 每个 Application 可以创建多个 API Key；明文仅创建时返回，服务端存验证 hash，可显式 REVOKE；新 Key 不自动撤销旧 ACTIVE Key。
5. 运行面 `POST /v1/invoke` 只通过 Bearer API Key 识别 Application，不允许调用方指定内部 Application/Provider/Deployment ID。
6. 统一请求消息、temperature、maxTokens 等字段，由 OpenAI-Compatible Adapter 转换上游 JSON；响应为 content/model/finishReason。
7. 模型调用经同步 RestClient 发向配置的 Provider，使用独立 Provider Bearer（如有），不透传客户端 Key。
8. 失败按配置、上游 HTTP、网络超时、响应协议结构分类，向客户端返回安全稳定的 HTTP + Error Code，不回显敏感信息、不自动重试。
9. 应用级 E2E 测试包含配置创建、Key 生命周期、默认部署切换、成功与上游失败等。用户报告本地 Maven 138/0/0/0。

## 非本阶段交付 / 需要未来明确需求
多租户权限系统、正式管理员登录、模型路由、配额/限流、费用与用量账本、SSE、自动重试/熔断/降级、Redis/MQ、微服务、生产级出站网络防护等。未来是否做及顺序以确认后的 Phase 计划为准。

## 验收口径
功能代码/数据库/API 文档必须和 main 一致；测试应可重复执行；Phase 2 关闭需完整 P2-T11 Closeout 审核，不能仅凭测试数或文档更新关闭。


---

## Phase 2 最终状态（2026-10-18）

P2-T01～T11 已全部完成并通过总控收尾，**Phase 2 CLOSED / ACCEPTED**；功能与测试依据见 `docs/phase-2-closeout.md`。用户报告本地 138/0/0/0 测试通过，此证据不是 GitHub CI 门禁。

## Phase 3 前期需求讨论（历史；已由 2026-10-18 总控决策取代）

Phase 3 已由用户授权启动，目前仅**冻结 P3-T01 的目标：找出 Phase 2 后最值得做的单一业务问题，比较最简方案并确认最小范围。**

优先评估方向：Invocation Traceability——当前模型代理可以返回结果，但没有业务可查询的每次调用的成功/失败、所属 Application、目标 Deployment 或延迟历史记录。必须先确认目标用户真的需要查阅这些数据，以及现有日志为什么不足。

P3-T01 需回答：

- 谁查询调用信息、用来解决什么问题？哪些最小字段是必要的？
- 哪种方案最简单：保持现有日志、结构化日志、MySQL 最小记录、暂不做？
- 如果存储：如何避免保留 Prompt / 模型回答原文 / API Key / Provider Secret？如何限定保留周期和失败记录语义？
- 首个 Happy Path、测试计划、P3-T02 后续任务如何拆分？
- 为什么不在没有实际瓶颈时上 Redis、MQ、ES 或微服务？

**P3-T01 不等于批准 InvocationRecord 表或查询 API。** 在范围验收前不得将该候选方向写成已实现需求；P3-T02 尚未启动。


---

## Phase 3 已冻结业务需求（2026-10-18，总控决定）

**项目阶段：Phase 3 — Invocation Records & Basic Visibility。P3-T01 DONE，P3-T02 ACTIVE。**

**R3-01：** 通过认证和 `@Valid`、进入 Runtime Invoke 的请求，应产生最小可查询调用历史。未认证或参数非法的请求不产生 InvocationRecord。

**R3-02：** 成功时记录 Application ID、SUCCESS、耗时毫秒数和时间；失败时记录 FAILED 和已归类的安全错误码。不得保存 Prompt、模型回答、请求/响应 JSON、客户端 API Key、Provider Credential、内部 Provider URL。

**R3-03：** 记录属于 best-effort 业务排障数据，**记录写入失败不能覆盖或修改客户端原始 InvokeResponse/ApiErrorResponse**；不承诺 exactly-once、合规审计或准确计费。

**R3-04：** 管理员可使用已有 Basic 身份通过 `GET /api/applications/{applicationId}/invocations?limit=20` 查询特定 App 最近历史；默认 20，合法范围 1..100，按 ID 降序；不存在 Application 404，无记录 `[]`。

**R3-05：** 记录保留默认 30 天，使用最小定时清理避免表无限增长；数据生命周期和超界查询都要测试。

**R3-06：** MySQL V7 最小表结构：`id, application_id, status, error_code, duration_ms, created_at`；`application_id` FK CASCADE，索引 `(application_id,id)`。

**R3-07：** 基于现有模块化单体 + MySQL + MyBatis-Plus + Flyway + Testcontainers；不得为了记录业务增加 Redis/MQ/ES、微服务、计费系统或新 Provider SDK。

**历史原计划备注（已由新总控任务表取代）**：当时仅 P3-T02 可编码。当前 P3-T01～T04 已完成，P3-T05 正在执行合并后的失败记录、30 天保留和完整 E2E；P3-T06 为 Phase Closeout。开发以最新 docs/current-phase.md 为准。

正式架构取舍记录：`docs/adr/ADR-003-invocation-recording-persistence.md`；实际代码状态以 `docs/current-phase.md` 为准。


## Phase 3 Current Execution Update — 2026-10-18

**P3-T04 管理历史查询已实现并验收**：Basic 管理 GET /api/applications/{applicationId}/invocations，默认20/最大100，按 App 过滤排序，严格只返回六字段，错误/空记录合同明确。**P3-T05 ACTIVE**：一次开发失败调用记录、30 天自动清理和全链路 E2E。原 T06 清理任务并入 T05，原 T07 阶段收尾调整为 T06。此次变更只调整工作包和验收频率，R3-01～R3-07 业务要求、ADR-003 MySQL best-effort 方案不变。


## Phase 3 Final Requirements Acceptance — 2026-10-20

**R3-01～R3-07 已落实，Phase 3 CLOSED / ACCEPTED。** 正式交付：Flyway V7 的 InvocationRecord、成功/失败的安全元数据尽力入库（不覆盖模型调用结果）、Basic 管理端按 Application 最近 N 条查询（默认20，上限100）、每天 UTC03:00 清理30天前历史。记录表不存 Prompt、回复正文、Client/Provider 密钥或上游原始错误；不是计费/合规审计账本。P3-T06 已完成超时测试可靠性与夹具生命周期检查。历史前文旧的 ACTIVE/NOT STARTED 段落只代表当时进度，以 docs/current-phase.md 和 docs/phase-3-closeout.md 为准。**Phase 4 NOT STARTED。**


---

## Phase 4 已冻结需求 — Application Rate Limiting（PLANNED，2026-10-20）

**Phase 4 ACTIVE / P4-T01 ACTIVE；下列为已批准但尚未实现的业务需求。**

- **R4-01：** 管理员可按 Application 启用、设置、更改、关闭模型请求速率策略。默认不限流，保留已有接口行为。请求独立于旧 Application CRUD。
- **R4-02：** 策略 RPM 允许整数 1..10000；`enabled=false` 对应 DB NULL，`enabled=true` 需要有效 RPM。非法值 HTTP 400 `INVALID_RATE_LIMIT_POLICY`（非 JSON 请求按现有 INVALID_REQUEST），App 不存在404，管理 Basic 认证缺失401。
- **R4-03：** 经 Runtime API Key 认证和请求校验后，以 Authentication 中的 ApplicationIdentity.applicationId 限流；同 App 多个 Key 共享额度、不同 App 独立。
- **R4-04：** 单 JVM 内令牌桶容量 RPM、平均每分钟补 RPM tokens，原子消费1 token/有效调用，支持有限突发；不得宣传任何连续60秒绝对限制 RPM 次。
- **R4-05：** 平台拒绝 HTTP 429 `APPLICATION_RATE_LIMITED` + 整数秒 Retry-After；必须在 Provider 出站前返回，不得写 Phase 3 invocation_record；与上游 Provider 429→503 PROVIDER_RATE_LIMITED 严格区别。
- **R4-06：** 限流禁用时完全放行；策略更新在下一新请求生效，重复同值 PUT 不重置桶；桶状态重启丢失，不保存到 MySQL，10分钟无访问回收。
- **R4-07：** 保持 Phase 2/3 Security、模型 HTTP、SUCCESS/FAILED 记录及管理历史查询契约不变；非法 API Key 401/DTO 非法400 不消耗令牌；DB 策略读取失败不能默默 fail-open。
- **R4-08：** MySQL V8 修改 application 表增加 nullable requests_per_minute+CHECK，不修改 Flyway V1–V7；用 MockMvc+真实 MySQL Testcontainers+本地 Provider 和假时钟/并发测试覆盖。
- **R4-09：** 单实例、无 Redis/MQ/外部 RateLimiter 依赖，不实现精确配额/费用账本/多节点全局限流；未来实际跨实例问题出现后单独做技术选型。
- **R4-10：** 一个完整业务功能包 P4-T01 后统一验收，P4-T02 集中 E2E/质量/阶段 Closeout；不再拆低复杂度微任务。

原先 Phase 4 在早期文档中的“未来候选”现在已正式由总控批准启动；完整冻结执行合同见 `docs/current-phase.md`，重大架构取舍见 `docs/adr/ADR-004-application-rate-limiting.md`。


---

## Enterprise Agent Governance — Product Scope Confirmation（2026-10-20）

**本节为已确认的顶层产品定位；不是已经实现的接口或立即启动的开发任务。**

**In scope（最终产品目标）：** 管理企业已有 Agent 的登记/配置/启用/停用、接入身份、员工与团队使用权限；管理 Agent 可用的模型、安全准入策略、统一 Provider 流量；员工个人额度、团队额度池、按员工/团队/Agent 区分可用额度、临时额度申请审批；重要/紧急任务的预算与资源优先策略；模型用量、成本、请求压力及异常监控、必要管理操作审计。

**Out of scope（当前明确不做）：** Agent 业务逻辑开发、Agent 可视化工作流编排器、Agent 自身执行引擎、Agent 工具调用业务代码、模型训练/推理引擎开发。网页对话可视为一种外部 Agent/接入形态，但当前不据此要求在 AIGate 内建设聊天产品。

**能力边界：** 网关只能保证管理与观测经过 AIGate 的模型调用；若要关联一次外部 Agent 任务的多次调用，需要外部 Agent 提供受信任的任务/用户归属信息。第三方 Provider 的物理算力/GPU 实时负载并非网关天然可知。

**待确认（不可先行设计实现）：** User、Employee、Team、Application、Agent 的对应关系及具体调用身份；预算归属与扣减（个人/团队/Agent）、额度单位、申请审批、紧急任务优先级语义与接入协议；原 Phase 4 限流是否应先于这些业务能力开发。**P4-T01 PAUSED，ADR-004 ON HOLD。**


### Confirmed Enterprise Routing Constraint（2026-10-20）

**BR-ENTRY-01（已确认的顶层业务原则）：企业纳入 AIGate 治理的所有 Agent 模型请求必须经由 AIGate 统一入口。** 额度、权限、成本和请求压力的统计以经过网关的请求为事实基础。Agent 开发、工具/业务流程运行不在 AIGate 本阶段职责内。User/Employee/Team/Agent/Application 业务归属、真实终端员工身份传递与计费结算方案仍待确认，禁止凭客户端不可信 employeeId 直接扣额度；实现门禁维持 P4-T01 PAUSED。


## Confirmed Employee/Agent/Quota Requirements（2026-10-20）

用户确认以下**顶层业务需求**，仍未形成新 Phase 可执行实现合同；具体事实与待决事项详见 `docs/product-business-rules.md`：

- 员工主团队 + 临时其他团队协作任务，可在任务场景获额外额度；
- 管理员统一纳管企业已有 Agent，不开发 Agent 注册/编排/执行流程；受管 Agent 与网关 Application 按 1:1；
- 每次模型调用必须可靠归属实际员工，自动任务可使用单独管理的专用账号；
- 按周管理额度：员工有使用权限的 Agent 享免费额度，超过后用员工个人额度，个人不足向团队申请；
- 紧急任务须经具体任务申请获取额外额度/临时优先权；
- 每 Agent 的模型允许列表由管理策略决定，员工可主动选择模型；负载路由发生于同逻辑模型的不同 Deployment，**不是不同模型之间自动切换**。

尚未确认：按周重置时刻/预算额度单位、免费额度按个人Agent还是全体共享、跨团队申请向谁扣减、调用链中员工身份认证、并发预算与失败结算等。**任何数据库/协议/技术方案不因以上业务确认自动启动。P4-T01 PAUSED。**


## Weekly Allowance & Employee Authentication — Confirmed Requirements（2026-10-20）

已确认的企业目标需求（未实现）：
- 每员工×有权限 Agent 独立免费周额度；每周统一重置，员工个人基础周额度同周期刷新。
- 先使用该 Agent 免费积分、再消耗员工个人基础积分；仍不足时消耗员工通过申请或团队主动发放获得的补充积分。团队发放有独立额度、有效期和可追溯来源；**模型请求不用逐次分摊团队成本**。
- 模型计费以不同 Model 的 Token 单价换算企业内部积分；具体换算公式、缓存 Token/失败结算/并发预留待定。
- 员工在 Agent 调用时持**独立个人认证凭证**，与 Agent 的 Application API Key 同时校验；不能凭普通 employeeId 自称某员工。无人值守 Agent 可有独立专用账号。
- 团队补充发放机制必须防重复分配/结算，并保留来源；从团队池何时扣、未用到期余额是否返还尚待确定。
- 具体周刷新时刻/时区、不同 Agent/Team 的免费额度参数、临时紧急任务优先及同模型 Deployment 策略尚待确认。**P4-T01 PAUSED。**

完整已确认事实与待决清单在 `docs/product-business-rules.md`，不可将目标能力写为已实现。


## Confirmed Billing Semantics and Degraded Availability — 2026-10-20

基于用户对六项计费/额度方案的确认（尚未实现）：
- REQ-BILL-01：采用公司统一时区每周一 00:00 刷新每员工×已授权 Agent 免费积分与员工个人基础积分；未用不累积。
- REQ-BILL-02：团队额度发放一经批准/主动发送，**立刻从团队池扣除**并归员工可用，补充额度各自具有到期日；到期剩余额度**不返还**团队池。需准确发放与过期流水。
- REQ-BILL-03：不同逻辑模型的 Token 输入/输出等维度按各自单价折算内部积分，原始用量、价格版本与换算结果需可追溯。
- REQ-BILL-04：对失败/超时调用，确定真实用量才完成计费；不确定记为待核对。调用前预留资源，结束后按实际用量结算并解除多余预留；不可将 P3 尽力记录当计费事实。
- REQ-BILL-05：正常积分不足时，不直接以余额不足拒绝所有请求；变更为**最低速率+最低优先级+员工提醒**的受限可用服务模式（这是用户最后补充，覆盖选项 insufficient=A 中的“直接拒绝”）。保底预算来源、总上限及拥塞拒绝/排队规则**尚待确认**；未经确认不允许无限预算穿透。
- REQ-BILL-06：周刷新/团队发放/降级前后的积分可用余额需要正确处理并发，具体事务方案待业务定稿后选择。

任务门禁：P4-T01 PAUSED / NO CODING，原限流规则仍为非当前执行业务草案。事实来源 docs/product-business-rules.md。


## Confirmed Resource Governance Requirements（2026-10-20，未实现）

- REQ-GOV-01：员工正常额度耗尽后，企业公共保底预算为员工提供**每周有上限**的基础服务；进入限流档并提醒。必须具备有限服务边界，不能无限免费；具体额度/耗尽策略待定。
- REQ-GOV-02：额度耗尽时依 Agent 配置**自动切到经授权的保底逻辑模型**，这是正常模式“员工手动选择逻辑 Model、路由只在同模型部署”的显式例外；切换事实对请求方/用量归属应可见。
- REQ-GOV-03：在 AIGate 自己控制的入口侧为限流档预留少量并发容量，支持有界等待；对第三方 Provider 不作绝对响应时间承诺。低速率/最低优先级不靠纯人为 sleep 达成。
- REQ-GOV-04：三个服务档位仅为**限流档、普通档、紧急档**；紧急档只能来自可信、有效期内的业务任务审批，不放宽模型安全策略；速率、并发、排队优先级是可独立调节的约束。
- REQ-GOV-05：正常模式员工手选 Agent 允许的逻辑模型；AIGate 在相同逻辑模型的健康 Deployment 间按健康、负载、成本、延迟、策略权重选择。**多部署路由不等于对失败请求自动重试**，重试要另行确认。
- REQ-GOV-06：管理员查看每逻辑模型与具体 Deployment 的实时调用量、在途并发、延迟、错误和限流，提供历史趋势、告警、辅助扩缩容分析；只能承诺网关观察得到的数据，不可假称有第三方实际 GPU 负载或已支持自动扩缩容。
- REQ-GOV-07：保底总预算、每员工每周上限、额度彻底耗尽后最终拒绝/排队规则、基本并发预留的公平策略、排队超时和监控阈值留待最后产品规则审查。

目前为确定目标、等待 Phase 路线重排；**旧 P4-T01 PAUSED / NO CODING**。唯一较完整的累计业务来源：docs/product-business-rules.md。


## Roadmap Confirmation / Unresolved Last-Resort Budget Policy（2026-10-20）

**用户已确认 P4～P10 顶层 Phase 业务顺序**（身份与权限→周积分计量→团队额度→三档保底→同模型多部署路由和容量监控→任务紧急保障→生产演进），详见 docs/roadmap.md。这不是重新开始 P4 开发。**P4-T01 PAUSED / NO CODING**。

对“企业保底也耗尽”，用户选择 OTHER 并要求参考现有企业产品，不应强行替用户选“全部停止”或“必须审批”。待确认的终态策略可比较：付费模型硬预算上限、按 Agent 安全策略回退到无外部 API 逐次费用的自部署模型、审批后限额的紧急资金池。无模型成本不等于无 GPU/运维成本；如果要真正控制企业总支出，就不能在所有付费预算都无余额时继续不受限制地调用第三方模型。


## Approved Last-Resort Service and Budget Policy — 2026-10-20（业务已定，未实现）

**用户已确认**最终策略，覆盖前面“保底耗尽待决”之历史记录：
- 正常个人/Agent 免费/团队补充额度耗尽：进入限流档，使用有限的企业公用付费保底预算、Agent 预设安全授权保底模型、最低速率与优先级及基础并发，提醒员工。
- 任意必要的保底预算约束（每员工每周上限或企业保底总池）耗尽且无其他已批准来源：**不允许新的第三方付费模型调用，无限低速不代表可以透支**。
- 企业如有安全授权的自部署/无逐次第三方 API 费用基础模型，可在其单独的有限容量、并发、模型权限下维持基础使用；没有此资源时不虚构可用性。自部署仍有硬件和运营成本。
- 企业单独预留且受总授权限额约束的紧急任务预算，可经负责人/管理员审批发放指定积分、有效期、权限；审计审批和结算；应急资金用尽不得继续付费超支。
- 无合规基础模型且无有效应急预算时，新的付费调用应明确拒绝，提示员工申请或等待刷新。最低保证服务不是绝对 Provider SLA。
- 部署时区、具体限额、保底资源安全名单、受控队列与并发、紧急审批岗位仍为后续阶段细则。

**执行状态**：顶层企业路线及最后业务兜底规则均已 APPROVED；旧 P4 Application RPM 方案不因新需求而自动启动，**P4-T01 PAUSED / NO CODING**。

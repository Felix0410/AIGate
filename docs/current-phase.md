# AIGate Current Phase

> 本文件只记录**当前执行任务**及其冻结边界。已关闭的 Phase 2 全量验收记录见 `docs/phase-2-closeout.md`、`docs/progress.md` 和对应历史提交。

## 1. Current Phase

**Phase 3 — Business Goal & Minimal Scope Definition**

Status: **ACTIVE（范围定义中，尚未开始 P3 业务代码）**

Current Task: **P3-T01 — Phase 3 Business Goal & Scope Baseline（ACTIVE）**

Next Task: **P3-T02 — 待 P3-T01 验收后冻结，NOT STARTED**

- Phase 1: CLOSED
- Phase 2: **CLOSED / ACCEPTED**（P2-T01～T11）
- Phase 3: **ACTIVE / P3-T01**

> 2026-10-18 用户明确同意进入 P3。Phase 2 Closeout 已完成：完整单模型同步调用闭环、Phase 级 Code Review、文档、知识点和面试表达。用户报告本地 Maven **138 tests / 0 failures / 0 errors / 0 skipped**；该数据不是 GitHub CI。

## 2. Business Background

当前 AIGate 已能完成：

```text
Management 配置 Provider/Model/ModelDeployment
→ Application 绑定默认 Deployment
→ Application API Key 认证
→ POST /v1/invoke
→ ModelProxyService + ProviderAdapter + RestClient
→ OpenAI-Compatible Provider
→ InvokeResponse / 安全分类错误
```

Phase 2 解决了**调用是否能真实成功、调用失败如何正确表达**的问题。

但现有最小模型代理没有持久化调用事件、平台用量/成本记录或跨请求的业务可见性。这是一个**值得分析的下一步业务缺口**，并不自动意味着现在需要 Redis、MQ、分布式追踪或计费。

## 3. P3-T01 Business Goal

> 在已有模型调用闭环上，明确 Phase 3 究竟先解决哪个可验证的业务问题，并用最小方案冻结范围、主流程、实体/API 边界及验收标准。不要在需求确认前直接编码“调用记录/限流/路由”。

**首选待验证方向：最小调用可追踪性（Invocation Traceability）**：

- 能否回答某个 Application 最近是否调用成功、失败的类别与耗时？
- 如果无法回答，当前运维/排障的真实成本是什么？
- 是否需要按 Application 查询调用历史，还是简单服务日志就够？
- 如需保存数据，哪些最小元信息可保留、保留多久？（严禁默认落库 Prompt、模型输出、Bearer Key 或 Provider Secret）
- 这件事是否优先于 CI 自动验证和安全技术债？用户确认的业务价值决定最终 Scope。

这一方向是**候选题目，不是已经批准的数据表或新功能**。

## 4. Scope（本任务）

P3-T01 需要：

1. 从现有代码/文档核实当前能看到哪些调用信息，哪些真实缺失。
2. 比较最小候选：现有应用日志 / 结构化日志 / MySQL 保存少量元数据 / 暂不保存。
3. 明确业务价值、数据隐私、失败调用如何记录、吞吐与数据增长的初步权衡。
4. 形成一个**只有一个主目标**的 Phase 3 范围建议；写清 Scope / Out of Scope。
5. 冻结最简 Happy Path、可能涉及的数据模型/API（仅在有需求时）、测试策略。
6. 拆出可逐一验收的 P3-T02 及后续任务，避免一次加入所有优化。
7. 对真正重大技术决策视情况建 ADR；小范围约定无需形式化 ADR。

P3-T01 **不修改生产代码、不新增 Flyway、不引入中间件**，只是通过事实、业务价值和方案比较确认下一开发目标。

## 5. Architecture / Proposed Options

保留现有 **Spring Boot 模块化单体 + MySQL**。候选方案的评价顺序：

```text
Option A: 现有日志是否足以解决业务问题？
Option B: 增加少量结构化日志？
Option C: 如需可查询历史，单 MySQL 最小调用元数据记录？
Option D: 只有出现明确异步量/吞吐问题后再考虑 MQ 等
```

不得因为想学习 Redis / MQ / Elasticsearch 而先引入。

对任何拟采用的新技术，必须回答：
它是什么、解决什么、当前为何需要、不采用会怎样、替代方案、选择理由、新复杂度。

## 6. Main Flow（候选，未批准/未实现）

若最终选择“最小调用记录”，拟分析的流程：

```text
POST /v1/invoke + ApplicationIdentity
↓
既有 ModelProxyService / ProviderAdapter / RestClient
↓
成功或分类失败
↓
仅记录最小安全元信息（Application、Deployment、Outcome、Latency 等候选字段）
↓
根据需求决定是否提供管理端查询
```

不要在当前任务里直接实现拦截器、AOP 记录、Usage 表、收费算法或异步 MQ。

## 7. Tasks / Deliverables

P3-T01 完成时应提交到项目文档：

- `docs/requirements.md`：确定 Phase 3 Business Goal + 为什么是当前优先业务问题
- `docs/roadmap.md`：冻结 Phase 3 可验收任务路线、先 Happy Path 后增量优化
- `docs/architecture.md`：边界/方案取舍（计划与已实现分开）
- `docs/domain-model.md`、`docs/database-design.md`、`docs/api-design.md`：仅在确定需要时写**计划设计**，不把计划当现有能力
- `docs/progress.md`：P3-T01 验收结论
- ADR：重大技术决策才新增

**下一业务编码任务 P3-T02 由 P3-T01 最终 Scope 决定，目前 NOT STARTED。**

## 8. Acceptance Criteria

- [ ] 明确只围绕一个可解释的 Phase 3 核心业务问题
- [ ] 区分已证实痛点、假设以及未来可选增强
- [ ] 给出最小可运行的 Happy Path 及完整验证方式
- [ ] 解释为什么不用现有最简单方案、替代方案和成本
- [ ] 如涉及调用信息持久化，明确敏感信息禁存、数据清理/保留、失败路径和一致性语义
- [ ] 只拆必须做的第一批任务，不将 Redis/MQ/ES/微服务强行加入
- [ ] 必要核心文档已更新，与 Phase 2 已实现事实一致
- [ ] 开发导师 Code Review / 需求评审后回总控验收

## 9. Knowledge Points

本任务优先学会：**从已实现业务发现下一真实问题、如何比较日志与数据库记录、如何设计最小验收标准、如何识别安全与数据增长成本**。

## 10. Existing Technical Debt / Safety Gates

- TD-001：HTTP Basic 只是管理开发期身份方案（公网/多用户上线前要重新评估）。
- TD-009：没有 GitHub CI Test Gate；本地 138 项通过不等于自动 CI。
- TD-011：Provider endpointUrl SSRF 与出站目标限制，公网生产化前需处理。
- TD-010：Provider Master Key Rotation。
- 其余 TD-002～TD-008 延续 Phase 2，根据真实问题排期。

**P3-T01 ACTIVE。不要自动假设已经开始调用记录业务编码，也不要擅自启动 P3-T02。** 用户已授权：后续单任务验收通过且无阻塞时，自动更新文档并启动下一任务。

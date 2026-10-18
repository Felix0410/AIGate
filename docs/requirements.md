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

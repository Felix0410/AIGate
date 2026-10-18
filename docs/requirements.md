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

## Phase 3 — P3-T01 需求确认中（非已批准功能）

Phase 3 已由用户授权启动，目前仅**冻结 P3-T01 的目标：找出 Phase 2 后最值得做的单一业务问题，比较最简方案并确认最小范围。**

优先评估方向：Invocation Traceability——当前模型代理可以返回结果，但没有业务可查询的每次调用的成功/失败、所属 Application、目标 Deployment 或延迟历史记录。必须先确认目标用户真的需要查阅这些数据，以及现有日志为什么不足。

P3-T01 需回答：

- 谁查询调用信息、用来解决什么问题？哪些最小字段是必要的？
- 哪种方案最简单：保持现有日志、结构化日志、MySQL 最小记录、暂不做？
- 如果存储：如何避免保留 Prompt / 模型回答原文 / API Key / Provider Secret？如何限定保留周期和失败记录语义？
- 首个 Happy Path、测试计划、P3-T02 后续任务如何拆分？
- 为什么不在没有实际瓶颈时上 Redis、MQ、ES 或微服务？

**P3-T01 不等于批准 InvocationRecord 表或查询 API。** 在范围验收前不得将该候选方向写成已实现需求；P3-T02 尚未启动。

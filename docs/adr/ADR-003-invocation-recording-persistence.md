# ADR-003 — Invocation Metadata Persistence and Failure Isolation

- Date: 2026-10-18
- Status: **ACCEPTED (Phase 3 design, not implemented)**
- Scope: Phase 3 — Invocation Records & Basic Visibility
- Owner: AIGate project control

## 1. 背景

Phase 2 已实现同步非流式 `POST /v1/invoke`，能完成 Application API Key 认证、默认 Deployment 解析、上游 HTTP 调用和错误分类。现有管理端无法根据 Application 查询某次调用成功/失败与耗时的历史元数据。出现故障时，只有瞬时 HTTP 响应和非业务化日志，管理员很难完成简单排障。

## 2. 问题

在不引入不必要分布式基础设施的前提下，如何保留**最小、不含敏感内容、可查询、受控增长**的调用历史？记录系统失败时是否应该影响真实模型调用？

## 3. 候选方案

| 方案 | 收益 | 限制 |
|---|---|---|
| A. 不保存、仅依赖现有文本日志 | 零新增表与写库负担 | 难以按 Application 稳定查询、管理 API 难实现 |
| B. 增加结构化日志 | 较小代码改动；便于运维检索 | 没有日志平台时无法稳定提供业务查询；日志保留和权限另需管理 |
| C. **MySQL 少量元数据，同步尽力记录** | 复用既有数据库、Schema/SQL 可测、可用 HTTP Basic 管理查询 | 每次有效 Invoke 增加一次写入；保留策略和写入失败语义要明确 |
| D. MQ 异步事件 + 消费者 | 适合高吞吐解耦、缓冲突发写入 | 增加 Broker、消费者、重复消费、幂等、事件可靠投递和运维成本 |

## 4. 最终选择

选 **C：MySQL 最小调用元数据记录 + 同步尽力写入（best effort）**。

- 新建一张 `invocation_record`（Flyway V7，只有 Phase 3 实现后才成为实际 schema）。
- 保存 applicationId、status、errorCode、durationMs、createdAt；不保存原始请求/回复/凭证/URL。
- 仅在通过 Runtime 身份认证和请求校验、进入 Invoke 业务执行后记录；未认证的调用与非法请求不计入此表。
- `SUCCESS` 记录在正常 Provider 返回后写入；`FAILED` 记录在业务代理已分类或出现其他可安全归类的失败后写入；原始业务成功/异常结果保持不变。
- 同步记录写入失败时，只输出无敏感字段的告警，不修改原模型调用结果、不覆盖原始错误。**这不是金融计费账本，不保证每条 100% 持久化**。
- 初期按单 Application 返回最近若干条（默认 20，上限 100）；默认按 id 降序；不引入分页平台、聚合/价格计算。
- 约定调用记录默认保留 **30 天**；待主流程完成后再使用简单的 Spring 定时清理（P3-T06），并测试时间边界。当前任务尚未实施清理。
- Application 删除时记录使用 FK ON DELETE CASCADE；因为这是当前 Application 从属的排障历史，而不是合规不可删除审计日志。

## 5. 选择原因

现有 Spring Boot 模块化单体与 MySQL 可以直接满足记录和简单查询；真实业务尚无消息积压或跨服务投递压力。为小流量管理排障增加 MQ，会过早引入事件投递可靠性和消费幂等问题。

保存最小元数据、固定保留周期、限制查询结果，降低对用户 Prompt 和 Provider 凭证的隐私风险，也降低无限增长风险。

## 6. 优点

- 不新增中间件，开发和 Testcontainers E2E 易验证
- 查询权限沿用管理面 HTTP Basic
- 表与字段有明确语义，不需要解析非结构化日志
- 不改变 `POST /v1/invoke` 对客户端的 API 契约
- 记录写失败时不覆盖原始模型调用结果

## 7. 缺点 / 新复杂度

- 每次有效 Invoke 增加同步 DB 写，增加延迟和 MySQL 负载
- DB 记录失败可能丢历史，因此不能宣称精确计费、合规审计或 exactly-once
- 异常分类必须确保原始错误被继续抛出，不能吞掉 Provider 异常
- 需要索引、受限查询、30 天保留清理及隐私测试
- 如果服务在 Provider 成功后、保存前崩溃，调用已完成但记录可能缺失

## 8. 对未来的影响

- 将来若出现真实吞吐瓶颈，再依据量化数据比较批量入库/队列/Outbox 等方案。
- 将来若要**可靠计费账本**，必须单独设计计费数据、幂等、强一致性/可靠事件投递；不能直接拿本 best-effort 表当收费事实。
- 将来若要分布式追踪，可与 requestId/traceId 关联，但现在不虚构这一能力。
- 当前唯一必须保持的 API 行为是：观测记录系统不能覆盖真实代理的成功或失败结果。

## 9. 验证

P3-T02~T06 用 MySQL Testcontainers + MockMvc + JDK HttpServer 验证：成功记录、失败记录、记录写失败不改变业务响应、查询按 Application 隔离且限量、记录不含敏感内容、30 天保留清理。保留 Phase 2 全量回归作为基线。

**注意：本 ADR 是已批准的未来设计决策，不表示记录表或接口已经存在。**

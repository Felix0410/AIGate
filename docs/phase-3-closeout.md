# AIGate Phase 3 Closeout — 待总控最终确认
> 2026-10-20；此报告是开发导师基于 GitHub main 静态审查与既定本地测试约定形成的阶段收尾交付，**不是自动将 Phase 3 标记 CLOSED**。当前 P3-T06 待总控验收，Phase 4 未启动。

## 1. Business Goal 与交付范围
Phase 2 已实现模型同步代理，但缺乏按 Application 查询的成功/失败历史。Phase 3 增加用于基础排障的最小调用元数据：SUCCESS/FAILED、稳定安全 errorCode、durationMs、createdAt。历史可经管理 HTTP Basic 查询，自动清理 30 天前数据；不存 Prompt、回复、密钥或上游原始错误。不承担精确计费或严格审计。

## 2. 已实现业务闭环
1. Runtime Bearer API Key 验证 + @Valid 后进入 RuntimeInvokeController / InvocationTrackingFacade。
2. Facade 调用 ModelProxyService / Provider：成功 best-effort 写 SUCCESS/errorCode=null，失败按异常类型写 FAILED/稳定分类码并 `throw original`；记录写入失败不会覆盖成功响应或原异常。
3. MySQL Flyway V7 `invocation_record(id,application_id,status,error_code,duration_ms,created_at)`；FK Application CASCADE、非负耗时和状态 CHECK、`(application_id,id)` 索引。
4. 管理端 `GET /api/applications/{applicationId}/invocations?limit=20`，默认20、允许1..100、按 Application 隔离、id DESC、只返回六字段；不存在 Application 404、非法 limit 400、存在无记录 200 []。
5. Scheduler 每日 UTC 03:00，`Instant.now().minus(Duration.ofDays(30))`，删除 `created_at < cutoff`，等于 cutoff 保留；清理失败固定安全告警。

## 3. 分任务验收依据
| Task | 实现/依据 | 状态 |
|---|---|---|
| P3-T01 | ADR-003 和 requirements 范围冻结 | ACCEPTED |
| P3-T02 | V7 Entity/Mapper、MySQL 测试；本地基线 149/0/0/0 仅属于当时提交 | ACCEPTED |
| P3-T03 | `ee38c95` SUCCESS best-effort | ACCEPTED |
| P3-T04 | `aae1546` 管理历史查询 | ACCEPTED |
| P3-T05 | `8262303` FAILED+Retention+E2E；`dfcc70a` 测试补强 | ACCEPTED |
| P3-T06 | `f3b0f94` 超时测试隔离；`a2a68fe` 测试资源清理；本报告 | 待总控确认 |

## 4. Code Review / Architecture Check
- Controller 不接管 Provider HTTP；TrackingFacade 只负责记录边界，ModelProxyService 编排，ProviderAdapter 负责协议，RestClient 负责网络，Mapper 只持久化，Scheduler 负责生命周期。
- Provider HTTP 与 MySQL 写入不处于长事务；同步记录本身增加响应延迟且允许丢记录，符合 ADR-003 的 best-effort 定位。
- 安全：请求校验/鉴权失败不记入运行时记录；Runtime Bearer 与管理 Basic 分离；持久化和固定告警不输出 Prompt、Credential、API Key 或上游原始 body。
- 数据增长：当前 `(application_id,id)` 有利于最新记录查询，不直接优化 `created_at` 清理。大数据量时评估时间索引和分批删除；多实例 @Scheduled 重复执行问题未来由实际部署触发评估。
- 没有引入 Redis/MQ/ES、微服务、账本、重试或分布式锁；本阶段无需新 ADR。

## 5. Test Check 与可信证据边界
- 静态审阅的测试场景：MockMvc + Spring Security + MySQL Testcontainers + 本地 Mock HTTP Provider；SUCCESS、上游错误分类、非法请求零记录、管理端 SUCCESS/FAILED 混合历史、UTC cutoff 早于/等于/晚于、Scheduler 异常隔离、Mapper 保存异常不覆盖原业务结果。
- P3-T06 将原本 Mock 延迟60秒与默认 read timeout60秒的临界竞态，改成独立测试上下文 1 秒读超时、3 秒服务端延迟；真实 HTTP 504/PROVIDER_TIMEOUT、单条 FAILED、无重试断言保持。
- `a2a68fe` 把测试成功完成/断言异常后的夹具清理放在 @AfterEach，以 Application→ModelDeployment→Model→Provider→Team 顺序调用真实删除接口并检查 HTTP 200。
- **没有独立获取本轮 Maven `Tests run / Failures / Errors / Skipped / BUILD SUCCESS` 汇总或 GitHub CI 状态**；按照用户长期约定，收到“已提交”代表用户本地测试已通过。不能把历史 P3-T02 的149项统计宣称为 P3-T06 总量。
- 非阻塞测试债：若超时测试的多步 setup 在构造完整 TimeoutSetup 之前中途失败，可能留有局部测试数据；没有当前证据表明这阻塞本阶段。

## 6. 技术债与准生产边界
- TD-001：管理面 Basic 是开发阶段的最小认证边界，公网/多租户前强化。
- TD-009：缺 GitHub CI 自动测试 Gate，影响自动、独立的测试证据。
- TD-010：Provider Master Key 轮换和应急方案尚未完成。
- TD-011：Provider endpointUrl 出站 SSRF/目标限制应在开放公网或不可信管理场景前解决。
- 流量增长后再评估 best-effort 同步落库、created_at 索引/分批清理；不提前引入 MQ 或微服务。

## 7. 文档、知识和面试沉淀
实际架构说明在 `architecture.md`、`module-design.md`，API 在 `api-design.md`，数据库在 `database-design.md`，时间线在 `roadmap.md` 与 `progress.md`，知识及面试表达在 `knowledge-map.md` 和 `interview-notes.md`。较早 PLANNED/ACTIVE 文本为历史快照，当前执行状态以 `current-phase.md` 为准。
重点可解释：为何已有日志不够、为何 MySQL 而非 MQ、如何保持原异常对象和数据库异常隔离、为何最近 LIMIT 20 而非直接分页、UTC 清理边界与单实例调度局限。

## 8. Closeout 建议与执行 Gate
**建议总控将 P3-T06 验收通过并关闭 Phase 3（以前述本地测试约定和无独立 CI 证据的明确边界为前提）**。生产核心路径和阶段测试的静态 Code Review 未发现阻塞问题；遗留风险已记录，不以“Phase Closed”掩盖生产化债务。

**本文件提交不代表阶段正式关闭。只有总控确认后才由总控修改 `current-phase.md` 为 Phase 3 CLOSED；Phase 4 另行冻结和启动。**

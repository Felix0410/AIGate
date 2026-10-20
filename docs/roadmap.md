# AIGate Roadmap

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

## Phase 4 — NOT STARTED（总控候选方向，待用户确认）
候选核心业务：**Application 级请求速率保护**，阻止单个 Application 短时间过量请求占用网关/Provider 能力。优先现有模块化单体的最简单正确方案，只有出现实测跨实例一致性问题时才论证 Redis。计划按“一个完整限流业务交付包 + 统一集成回归与阶段收尾”组织，不再拆多个低复杂度微任务。具体算法、表/API、限流边界和验收在正式启动 Phase 4 前由总控冻结；本节尚非 Phase 4 启动指令。
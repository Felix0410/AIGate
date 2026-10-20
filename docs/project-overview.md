# AIGate Project Overview

## 项目定位
AIGate 是用于学习与验证现代 Java 后端设计的 AI 模型网关项目。目标不是只调用一个模型 API，而是形成可运行、可验证、可解释并能逐步演进的业务系统。

## 当前实现边界（2026-10-18）
**Phase 1/2/3 均 CLOSED / ACCEPTED（Phase 3 于 2026-10-20 关闭）；Phase 4 NOT STARTED。**

技术基线：Java 21、Spring Boot、Spring MVC、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway、Jackson 3；模块化单体 + 单 MySQL，非微服务。

管理面 `/api/**` 使用临时 HTTP Basic 管理 Team、Employee、Application、Provider、Model、ModelDeployment、Application API Key。运行面 `/v1/**` 使用 Application Bearer Key，`POST /v1/invoke` 解析默认 ModelDeployment，借 ProviderAdapter 转换 OpenAI-Compatible 协议并经 RestClient 同步发送真实 HTTP。异常统一映射为脱敏 ApiErrorResponse。

应用 Key 以高熵 Secret 的 SHA-256 hash 校验；Provider Credential 以 AES-GCM 加密存储，调用时解密，两者不能混用。

## 可验证性与限制
MockMvc + MySQL Testcontainers + 本地 JDK HttpServer 验证应用级 E2E。用户报告本地 Maven 138 tests、0 failures、0 errors、0 skipped；尚无 GitHub CI Gate。未实现自动重试、熔断、SSE、配额/计费、Redis、MQ、分布式部署或多模型路由。

生产化前需重新审视管理面认证、Provider Endpoint SSRF/网络限制、自动化 CI、安全可观测性及主密钥轮换等技术债。

## 工作规则
业务问题 → 最简可运行方案 → 主流程 → 验证 → 真实问题 → 架构演进。以 GitHub 代码和验收记录为事实来源，聊天与历史草稿不替代项目文档。


## Phase 3 当前执行任务（2026-10-18）
Phase 2 的收尾已通过，见 `docs/phase-2-closeout.md`。Phase 3 已于 2026-10-20 CLOSED / ACCEPTED；调用元数据及管理历史查询已实现，具体见 Phase 3 关闭记录。


## Phase 3 实际功能（2026-10-20）
MySQL V7 调用记录表；合法 Invoke 成功写 SUCCESS、失败写 FAILED 和固定安全错误码；System.nanoTime 记录耗时，写库失败不覆盖 Provider 结果。管理 Basic 的 GET /api/applications/{id}/invocations 默认20、上限100、按 Application 隔离。每天 UTC 03:00 清理超过30天的记录。非计费/审计账本，不保存原始 Prompt、回答及密钥。P3-T06 测试超时边界与夹具资源清理已审查。无本轮独立 Maven/CI 数字，见 docs/phase-3-closeout.md。

---

## Phase 4 正式启动（2026-10-20，业务方案已冻结 / 尚未实现）

**Phase 4 — Application Rate Limiting：ACTIVE / P4-T01 ACTIVE**。

Phase 1 解决 Team/Employee/Application 归属与基础管理；Phase 2 建立真实单模型同步代理；Phase 3 加入 SUCCESS/FAILED 排障记录、管理历史查询与30天清理。**Phase 4 新业务目标是保护合法 Application 的模型请求速率**，避免某个应用短时间过多请求压垮网关或 Provider。

总控已在 ADR-004 和 docs/current-phase.md 冻结：MySQL V8 持久化每 App RPM 约束（NULL 默认不限制）+ JVM 内存原子令牌桶（System.nanoTime、ConcurrentHashMap.compute）+ Basic 管理 GET/PUT 速率策略 + Runtime 本地 429 APPLICATION_RATE_LIMITED（Retry-After，零 Provider HTTP / InvocationRecord）。

这是**单实例、平均速率+有限突发**，不保证任意滚动60秒精确次数或多实例统一额度；暂时不引入 Redis/MQ/Nacos/微服务，不把它当作费用/配额账本。V8 与新增 API 在 P4-T01 实现/验收前均为 PLANNED。任务按完整业务交付包划分：P4-T01 完整实现+测试；P4-T02 E2E硬化+Phase Closeout。开发导师只处理编码和测试，**docs/ 由总控维护**。


---

## Product Positioning Decision — 2026-10-20（产品顶层范围，已确认）

**AIGate 是面向企业内部的 AI Agent 统一接入、模型流量治理与资源管理平台。**

- 企业已有各种 Agent（包括网页对话入口、研发/知识库/业务 Agent），Agent 的开发、编排和业务执行可位于其他框架或系统；**AIGate 不负责开发 Agent、可视化编排、Agent 执行引擎或工具业务实现**。
- AIGate 负责登记、配置、启停和管理 Agent 的接入身份、员工及团队使用权限、允许的模型与安全策略、模型流量及优先级治理、用量/成本归属、额度与团队资源调配、运行监控和管理操作审计（这些为目标能力，非都已实施）。
- AIGate 只能直接观察**经过网关的模型调用**；外部 Agent 内部的步骤/任务执行过程必须依赖 Agent 或外部系统提供任务关联信息，不能声称天然拥有。
- 用户已明确：员工个人额度、团队额度池、不同团队/Agent 差异化额度、紧急任务增加额度与优先使用权、多模型选项及安全/定价差异、按模型查看网关观测到的压力，是目标业务方向；**具体归属/结算/优先级规则尚未确认**。
- 不将当前 Application 直接等同于 Agent，也不将 Employee 直接当作登录 User；映射关系待下一层确定。现有 Phase1~3 是已实现的底座。
- **Phase4 P4-T01 编码已暂停**，之前的 Application 令牌桶 ADR-004 是候选草案；顶层流程和主体/额度归属逐层确定前不重新启动。


### Unified AI Model Entry — Confirmed Product Principle（2026-10-20）

用户确认：**所有纳入公司 AIGate 管理的 Agent，模型调用统一经过 AIGate**，作为认证、模型准入、额度/成本归属、流量策略与网关可观测性的统一执行入口。Agent 本身的开发和执行仍由外部系统负责。平台不能仅靠接口约定物理阻止旁路调用；后续需要结合 Provider 凭证集中保管、外部网络出口规则与接入治理来确保覆盖，具体机制待后续层级确认。

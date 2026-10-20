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
# AIGate Roadmap

> 以实际交付为准；计划与已实现功能分开记录。

## Phase 1 — Foundation & Core Identity
Status: **CLOSED**

已实现 Team、Employee、Application 的基础管理，Spring MVC + MyBatis-Plus + MySQL + Flyway、参数校验、全局异常、HTTP Basic 管理面认证及集成测试。

## Phase 2 — Model Registry & Single Model Proxy
Status: **CLOSEOUT IN PROGRESS（P2-T11 ACTIVE）**

P2-T01～T10 已验收：Provider/Model/ModelDeployment 注册；Provider 凭证 AES-GCM；Application 默认 Deployment；Application API Key 创建/撤销与运行时认证；统一 Invoke DTO；OpenAI-Compatible Adapter；同步 RestClient 真实模型代理；安全分类的 Provider 错误；E2E 验证。

业务闭环：管理面配置 Deployment → Application 绑定 → 创建 API Key → `POST /v1/invoke` → 真实上游 HTTP → 成功 InvokeResponse / 失败 ApiErrorResponse。

测试证据：用户报告本地 `./mvnw test` 汇总 **138 run / 0 failures / 0 errors / 0 skipped**；暂无 GitHub CI 门禁，不能宣称 CI green。

P2-T11 待办：文档一致性、Phase 级 Review、技术债优先级与面试表达、总控关闭审批。**尚未标记 Phase 2 CLOSED。**

## Phase 3 — Planned
Status: **NOT STARTED**

具体范围以进入该阶段时经确认的 `docs/current-phase.md` 和需求文档为准。不得根据 Phase 2 的存在自行推断 Phase 3 一定要增加 Redis/MQ/微服务、路由或配额。

## 生产化前注意
TD-001：管理面 HTTP Basic 仅为初期安全基线；TD-009：缺少自动 CI 测试门禁；TD-011：可配置 Provider Endpoint 的 SSRF / 出站网络目标限制。应按部署风险处理，而不是无条件塞进下一阶段。


# ADR-001: Phase 1 Authentication Baseline

## Status

Accepted

## Context

Phase 1 的管理 API 最初完全匿名开放。

当前需要建立最小安全边界，防止 `/api/**` 被匿名访问。

但此时 AIGate 的最终身份体系尚未确定。

未来可能存在不同身份来源：

- 后台管理员
- Employee
- Application
- 第三方用户
- API Client

因此当前阶段不希望过早绑定最终认证模型。

## Problem

需要解决：

> Phase 1 管理 API 不能匿名访问。

同时需要避免：

> 为尚未出现的最终认证需求提前设计复杂身份体系。

## Options Considered

### Option 1: HTTP Basic

优点：

- Spring Security 原生支持
- 实现简单
- 易于调试
- 足够满足当前管理 API 的最小认证需求

缺点：

- 不适合作为最终互联网用户认证方案
- 不具备丰富身份上下文
- 不适合复杂权限模型

### Option 2: JWT

优点：

- 无状态
- 适合 API 认证
- 可以携带用户声明

缺点：

- 当前没有明确 User / Account 模型
- 会提前引入 Token 生命周期、刷新、签名、撤销等复杂度
- 可能错误地把 Employee 直接等同于登录用户

### Option 3: OAuth2 / OIDC

优点：

- 标准化身份协议
- 适合第三方 Identity Provider

缺点：

- 当前阶段明显过重
- 尚未存在对应身份集成需求

### Option 4: No Authentication

优点：

- 最简单

缺点：

- `/api/**` 完全匿名
- 已经不符合当前阶段最低安全要求

## Decision

Phase 1 使用 Spring Security + HTTP Basic。

规则：

```text
/api/**
→ authenticated

Swagger / OpenAPI
→ permitAll
```

认证账号通过环境变量提供。

Employee 当前不作为登录账号。

## Why

当前真实问题只是：

> 管理 API 需要一个最小认证边界。

HTTP Basic 能以最低复杂度解决该问题，同时不会绑定未来最终身份设计。

## Consequences

### Benefits

- 管理 API 不再匿名
- Spring Security 链路已经建立
- 后续可以自然替换认证方式
- 当前代码复杂度较低

### Costs

- 只能作为临时认证方案
- 当前没有 Role / Permission
- 当前账号模型非常简单

## Future Impact

当未来出现以下需求时，需要重新评估认证架构：

- Employee / User 登录
- Application Credential
- API Key
- 第三方 OAuth
- Role / Permission
- Multi-Tenant 身份隔离

届时应该新增新的 ADR，而不是直接修改本 ADR 的历史结论。

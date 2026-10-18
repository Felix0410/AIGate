# AIGate Domain Model

## 现有业务对象
```text
Team
├── Employee
└── Application
    ├── 1:N ApplicationApiKey
    └── defaultDeploymentId → ModelDeployment

ModelDeployment
├── providerId → Provider
└── modelId → Model
```

- **Team**：当前组织归属的基础单位；Employee 与 Application 属于 Team。
- **Application**：消费 AI 能力的运行时业务身份；默认部署从 `defaultDeploymentId` 解析。
- **ApplicationApiKey**：Application 的可撤销机器访问凭证（keyId、Secret hash、状态），一个 Application 可有多把 Key。
- **Provider**：外部模型服务提供方，当前支持 OPENAI_COMPATIBLE 类型。
- **Model**：逻辑模型目录项，不等同于某个可访问 HTTP 地址。
- **ModelDeployment**：真正可调用的部署配置；包括 endpointUrl、remoteModelName、enabled 与可选 encryptedCredential；关联 Provider 和 Model。

## 为什么分开
Application 与认证 Key 分离，使撤销一个 Key 不需要删除 Application；逻辑 Model 与 Deployment 分离，使同一逻辑模型能够拥有独立的供应商和出站配置；Provider 与 Deployment 分离，使供应商身份不等于具体可调用实例。

目前一个 Application 有单个默认 Deployment，不等同于已实现路由/负载均衡。应用在每次 Invoke 时从数据库读取当前默认目标，因此变更后的下一次调用生效。

## 数据与安全约束
详见 `database-design.md` 中 V1–V6、唯一约束及 FK；入站 Key hash 和出站 Credential AES-GCM 作用不同，Provider Credential 不属于 Application API Key。

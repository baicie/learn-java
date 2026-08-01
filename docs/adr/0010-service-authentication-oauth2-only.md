---
title: 服务间鉴权统一为 OAuth2
type: adr
status: accepted
phase: phase-8
owner: ai
created: 2026-07-30
updated: 2026-07-30
related:
  - docs/adr/0003-aiops-agent-boundary.md
  - docs/adr/0008-standardize-aiops-agent-port.md
  - docs/adr/0009-service-to-service-authentication.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# ADR 0010: 服务间鉴权统一为 OAuth2

## 状态

已接受（2026-07-30）。

本 ADR 替代 ADR 0003 中“Java 与 Agent 通过静态 internal token 鉴权”的决策、ADR 0008
中“HTTP + internal token”的鉴权陈述，以及 ADR 0009 的“静态 token 兼容模式”。ADR 0003
中的 Agent 部署边界、禁止直连主数据库与契约稳定性决策，ADR 0008 的 Agent 端口标准化决策，
以及 ADR 0009 中的 OAuth2、Diagnosis Grant、Kubernetes ServiceAccount、NetworkPolicy 与
可选 mTLS 决策继续有效。

## 背景

静态 Bearer Token 要求 Java 和 Agent 双向分发、同步和轮换长期凭据，无法表达 audience、
端点 scope 或服务主体。OAuth2 与 static 双模式还会扩大配置、测试和部署分支，并存在生产
误启用兼容模式的风险。

项目尚未形成必须兼容静态 Token 的外部部署契约，因此现在移除比继续维护迁移模式成本更低。

## 决策

Java 与 Agent 之间的所有服务调用只支持 OAuth2 Client Credentials：

- Java server/worker 调用 Agent 时获取短期 JWT，audience 为 `aiops-agent-api`。
- Agent 调用 Java 内部 API 时获取短期 JWT，audience 为 `aegisops-internal-api`。
- 所有端点继续校验 issuer、expiry、subject、audience 与 endpoint scope。
- 删除 `auth mode=static`、静态 Token Header、配置字段、环境变量和部署参数。
- Diagnosis Grant 继续绑定 `tenantId + incidentId + traceId`，不属于静态服务 Token。
- Zabbix Webhook 签名密钥属于外部 Webhook 完整性校验，不受本决策影响。

本地 `infra/docker-compose.yml` 自动启动 Keycloak 并导入开发 realm。server、worker、
agent 使用三个独立 client；开发 client secret 只允许出现在本地默认配置。生产 Compose 和
Helm 必须由外部 IdP/Keycloak 注入端点及独立 client secret，缺失时配置渲染或启动失败。

发布运行时验证可以使用 `deploy/docker-compose.idp.yml` 启动临时 Keycloak，但该 overlay
不属于生产默认拓扑。

## 影响

正向影响：

- 服务鉴权只有一条代码和配置路径。
- 不再分发可直接调用 API 的长期共享 Bearer Token。
- audience、scope、主体和吊销策略由 IdP 统一管理。
- 本地开发通过一条 Compose 命令获得与生产一致的协议。

代价：

- 本地启动 AI 诊断链路前必须运行 Keycloak。
- 私有化和生产部署必须准备三个 OAuth2 client。
- IdP 不可用时无法获取新 Token，调用方只能使用缓存中尚未过期的 Token。

## 备选方案

- 保留开发静态模式：拒绝。双模式本身就是维护和误配置来源。
- 只在 Helm 禁止 static、代码继续保留：拒绝。隐藏分支仍会退化和绕过测试。
- 只使用 mTLS：拒绝。mTLS 不表达端点 scope 和 Diagnosis Grant 上下文。
- 新建 AegisOps auth-service：拒绝。复用 Keycloak/企业 IdP，避免过早微服务。

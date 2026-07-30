---
title: Java 与 aiops-agent 服务间鉴权
type: adr
status: accepted
phase: phase-8
owner: ai
created: 2026-07-30
updated: 2026-07-30
related:
  - docs/adr/0003-aiops-agent-boundary.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# ADR 0009: Java 与 aiops-agent 服务间鉴权

## 状态

已接受（2026-07-30）。

## 背景

ADR 0003 确定 Java 与 Python Agent 通过 HTTP 解耦，但当时使用单一静态 token，并依赖
`X-Tenant-Id` 传递租户。该方式无法区分 server、worker、agent 的服务身份，无法按端点授权，
token 泄露后的影响范围过大，也不能证明 Agent 回调属于哪一次 Incident 诊断。

本 ADR 只替代 ADR 0003 中的服务间鉴权方式，不改变以下边界：

- Java 后端仍是模块化单体，不新增业务 `auth-service`。
- aiops-agent 不直连 PostgreSQL、MinIO、ClickHouse。
- aiops-agent 不触发 Runner，不绕过审批。
- Java 与 Agent 的 HTTP contract version 保持独立演进。

## 决策

### 1. 生产服务身份使用 OAuth2 Client Credentials

生产环境复用企业 IdP 或 Keycloak。server、worker、agent 分别使用独立 OAuth2 client 获取
短期 JWT access token，不在本项目中新增认证微服务。

JWT 至少校验：

- 签名、issuer、过期时间。
- 目标服务 audience。
- 调用方 subject。
- 当前端点所需 scope。

Java 调 Agent 使用 `aiops-agent-api` audience；Agent 回调 Java 使用
`aegisops-internal-api` audience。端点 scope 见内部服务鉴权 API 文档。

### 2. Java 签发 Diagnosis Grant

server 或 worker 发起诊断时签发短期 HMAC-SHA256 JWT，至少绑定：

- `tenantId`
- `incidentId`
- `traceId`
- `issuer`
- `audience`
- `issuedAt` / `expiresAt`

Grant 默认有效期 300 秒。签名密钥只进入 server 和 worker，不进入 Agent。Agent 将 Grant
视为不透明凭据，只在诊断期间向 Java 内部工具调用传播
`X-AegisOps-Diagnosis-Grant`。

Java 验证 Grant 后从签名声明恢复 `TenantContext`，不把请求中的 `X-Tenant-Id` 作为授权
来源。服务 access token 证明“谁在调用”，Diagnosis Grant 证明“代表哪次诊断调用”。

### 3. 部署身份和网络边界独立

Kubernetes 中 server、worker、runner、agent 使用独立 ServiceAccount 和独立 Secret。
NetworkPolicy 默认拒绝入站，只允许 server/worker 调 Agent、Agent 调 server 内部端点。

启用 Istio 时使用 STRICT mTLS，并通过 AuthorizationPolicy 限制：

- Agent 诊断端点只接受 server/worker ServiceAccount。
- `/internal/agent/*` 只接受 agent ServiceAccount。

mTLS 是传输与工作负载身份保护，不能替代 OAuth2 的 audience/scope 授权。

### 4. 静态 token 仅作为显式兼容模式

Docker Compose 开发或受限迁移环境可以显式设置 `auth mode=static`，但必须拆分：

- Java → Agent：`AIOPS_JAVA_TO_AGENT_TOKEN`
- Agent → Java：`AIOPS_AGENT_TO_JAVA_TOKEN`

两个 token 不得复用。生产 Helm 默认使用 OAuth2，不提供静态 token 默认值。

## 影响

正向影响：

- 服务身份、目标 audience 和端点权限可以独立吊销与审计。
- Agent 不能通过伪造租户 header 跨租户调用 Java 内部 API。
- Agent 不持有 Diagnosis Grant 签名密钥，不能自行扩大诊断授权。
- Kubernetes Secret 与网络访问按组件最小化。

代价：

- 生产部署必须预先配置 IdP client、audience、scope 和密钥轮换。
- Diagnosis Grant 密钥轮换需要协调 server 与 worker。
- Compose 发布环境需要配置三个新 Secret，旧单 token 配置不再兼容。

## 备选方案

- 单一静态 token：拒绝。无法区分调用方、目标和端点权限，泄露影响范围过大。
- 只使用 mTLS：拒绝。它能认证工作负载，但不表达 audience、endpoint scope 和诊断上下文。
- 新建业务 auth-service：拒绝。MVP 阶段会引入过早微服务，企业 IdP 已提供所需能力。
- 让 Agent 签发租户凭据：拒绝。Agent 不应拥有可扩大租户授权范围的签名密钥。

## 验证

- Java 与 Python 单元测试覆盖 audience、scope、过期、缺失 Grant 和伪造租户 header。
- Helm 渲染门禁覆盖独立 ServiceAccount、Secret、NetworkPolicy、STRICT mTLS 和
  AuthorizationPolicy。
- 完整本地门禁通过 `bash scripts/ci/verify-local.sh` 执行。

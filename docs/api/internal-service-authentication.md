---
title: 内部服务鉴权契约
type: api
status: accepted
phase: phase-8
owner: ai
created: 2026-07-30
updated: 2026-07-30
related:
  - docs/adr/0009-service-to-service-authentication.md
  - docs/deployment/production-security-checklist.md
---

# 内部服务鉴权契约

## 生产模式

生产环境使用 OAuth2 Client Credentials。调用方发送：

```http
Authorization: Bearer <short-lived-service-jwt>
```

JWT 必须匹配目标 audience 和端点 scope：

| 调用方向              | audience                | 端点                                      | scope               |
| --------------------- | ----------------------- | ----------------------------------------- | ------------------- |
| server/worker → agent | `aiops-agent-api`       | `POST /v1/diagnose`                       | `agent:diagnose`    |
| server/worker → agent | `aiops-agent-api`       | `POST /v1/diagnose/resume`                | `agent:resume`      |
| server → agent        | `aiops-agent-api`       | `POST /v1/work-record/generate`           | `agent:work-record` |
| agent → server        | `aegisops-internal-api` | `/internal/agent/evidence/*`              | `evidence:read`     |
| agent → server        | `aegisops-internal-api` | `/internal/agent/tools/search-cases`      | `cases:read`        |
| agent → server        | `aegisops-internal-api` | `/internal/agent/plugins/tools/authorize` | `plugin:authorize`  |
| agent → server        | `aegisops-internal-api` | `POST /internal/agent/memories/search`    | `memory:read`       |
| agent → server        | `aegisops-internal-api` | `POST /internal/agent/memories`           | `memory:write`      |
| agent → server        | `aegisops-internal-api` | `GET /internal/agent/checkpoints/*`       | `checkpoint:read`   |
| agent → server        | `aegisops-internal-api` | 写入 `/internal/agent/checkpoints/*`      | `checkpoint:write`  |

## Diagnosis Grant

Java 发起诊断时还必须发送：

```http
X-AegisOps-Diagnosis-Grant: <short-lived-signed-grant>
```

Agent 在本次诊断的 Java 工具调用中原样传播该 header。Java 校验签名、audience、issuer 和
有效期，并从 Grant 恢复租户上下文。`X-Tenant-Id` 仅保留为兼容与可观测字段，不参与授权
决策。

Grant 签名密钥至少 32 字节，只配置在 server/worker。默认 TTL 为 300 秒，不能进入 Agent
容器、日志、trace attribute 或错误响应。

## 静态兼容模式

静态模式仅用于本地开发或受限迁移，并且需要显式启用：

```text
Java -> Agent: X-AegisOps-Internal-Token
Agent -> Java: X-AIOPS-INTERNAL-TOKEN
```

两个方向使用不同 token。即使处于静态模式，诊断请求和 Agent 回调仍必须携带 Diagnosis
Grant。

## 错误语义

- `401`：缺少、过期或无效的服务凭据或 Diagnosis Grant。
- `403`：服务身份有效，但缺少目标端点所需 scope。
- `503`：服务鉴权模式或 IdP/JWKS 配置不可用。

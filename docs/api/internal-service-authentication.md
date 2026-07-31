---
title: 内部服务鉴权契约
type: api
status: accepted
phase: phase-8
owner: ai
created: 2026-07-30
updated: 2026-07-31
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/deployment/production-security-checklist.md
---

# 内部服务鉴权契约

## 服务身份

生产环境使用 OAuth2 Client Credentials。调用方发送：

```http
Authorization: Bearer <short-lived-service-jwt>
```

JWT 必须匹配目标 audience 和端点 scope：

服务 JWT 必须包含可验证的 `iss`、`aud`、`sub`、`scope`、`iat` 和 `exp`。Token 生命周期
必须满足 `0 < exp - iat <= 300s`，并且 `iat` 不得超过验证方当前时间 30 秒以上。

| 调用方向              | audience                | 端点                                       | scope               |
| --------------------- | ----------------------- | ------------------------------------------ | ------------------- |
| server/worker → agent | `aiops-agent-api`       | `POST /v1/diagnose`                        | `agent:diagnose`    |
| server/worker → agent | `aiops-agent-api`       | `POST /v1/auth/probe`                      | `agent:diagnose`    |
| server → agent        | `aiops-agent-api`       | `POST /v1/diagnose/resume`                 | `agent:resume`      |
| server/worker → agent | `aiops-agent-api`       | `POST /v1/work-record/generate`            | `agent:work-record` |
| agent → server        | `aegisops-internal-api` | `POST /internal/agent/auth/probe`          | `evidence:read`     |
| agent → server        | `aegisops-internal-api` | `/internal/agent/evidence/*`               | `evidence:read`     |
| agent → server        | `aegisops-internal-api` | `/internal/agent/tools/search-cases`       | `cases:read`        |
| agent → server        | `aegisops-internal-api` | `/internal/agent/plugins/tools/authorize`  | `plugin:authorize`  |
| agent → server        | `aegisops-internal-api` | `POST /internal/agent/memories/search`     | `memory:read`       |
| agent → server        | `aegisops-internal-api` | `POST /internal/agent/memories`            | `memory:write`      |
| agent → server        | `aegisops-internal-api` | 保留：`GET /internal/agent/checkpoints/*`  | `checkpoint:read`   |
| agent → server        | `aegisops-internal-api` | 保留：写入 `/internal/agent/checkpoints/*` | `checkpoint:write`  |

Checkpoint 路径当前仅作为 scope 与客户端契约保留，Java Controller 尚未实现；在对应服务端
接口落地前，不应将其视为可调用的生产 API。

## Diagnosis Grant

Java 发起诊断时还必须发送：

```http
X-AegisOps-Diagnosis-Grant: <short-lived-signed-grant>
```

Agent 校验该 header 存在，并在本次诊断的 Java 工具调用中原样传播；Agent 不持有 HMAC
密钥，因此不负责验证 Grant 内容。Java 校验签名、audience、issuer 和有效期，并从 Grant
恢复租户上下文。`X-Tenant-Id` 仅保留为兼容与可观测字段，不参与授权决策。

Evidence 请求中的 `tenantId + incidentId + traceId` 必须与 Grant 完全一致。Memory 请求使用
`incident` scope 时，`scopeId` 必须等于 Grant 中的 `incidentId`；其他 Memory scope、历史
案例检索和插件策略仍受 Grant 中的租户边界约束。

Grant 签名密钥至少 32 字节，只配置在 server/worker。默认 TTL 为 300 秒，不能进入 Agent
容器、日志、trace attribute 或错误响应。

## 部署鉴权探针

`POST /v1/auth/probe` 和 `POST /internal/agent/auth/probe` 仅回显已验证的服务主体与合成
Diagnosis Grant 上下文，不读取或写入业务数据，也不会触发诊断、Runner、审批或通知。
Java 端 probe 必须同时通过 `evidence:read` scope 和 Diagnosis Grant 校验。

生产部署脚本会在容器健康检查后运行 `deploy/scripts/verify-service-auth-oauth.py`：先检查 JWKS，
再分别为 server、worker、agent 换取并校验短期 Token，最后验证 Java → Agent 和
Agent → Java 两个实际 HTTP 调用方向。脚本和错误信息不得输出 Token、client secret、
Diagnosis Grant 或完整 claims。

## 错误语义

- `401`：缺少、过期或无效的服务凭据；Agent 侧 Grant 缺失；Java internal API 侧 Grant
  缺失、过期或无效。
- `403`：服务身份有效，但缺少目标端点所需 scope，或请求上下文超出 Diagnosis Grant。
- `503`：IdP/JWKS 配置或依赖不可用。

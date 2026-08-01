---
title: Phase 8.0：SaaS 多租户与生产安全加固
type: design
status: accepted
phase: phase-8
owner: ai
created: 2026-06-30
updated: 2026-07-30
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/private-deployment.md
  - docs/deployment/offline-package.md
  - docs/deployment/production-security-checklist.md
---

# Phase 8.0：SaaS 多租户与生产安全加固

## 1. 状态与适用范围

Phase 8.0 把 AegisOps 从功能型 MVP 推进到可做 SaaS 多租户隔离和私有化部署的生产安全
基线，重点是租户上下文、内部 API 鉴权、Diagnosis Grant、限流、审计和部署门禁，不新增
业务能力或 Runner 执行路径。

本文是已接受的 Phase 设计，不是 ADR。最初版本中的 Java/Agent 静态 Token 方案已由
[ADR 0010](adr/0010-service-authentication-oauth2-only.md) 取代；本文已删除旧配置、代码和
测试示例。服务间鉴权现在只支持 OAuth2 Client Credentials，不提供静态 Token 回退。
ADR 0003 的 Agent 边界和 ADR 0009 中未被取代的部署隔离决策继续有效，历史 ADR 保持不变。

实现和部署时，以以下文档为准：

- 服务间鉴权决策：`docs/adr/0010-service-authentication-oauth2-only.md`
- Header、audience、scope 与错误语义：`docs/api/internal-service-authentication.md`
- 生产部署检查：`docs/deployment/production-security-checklist.md`

## 2. 目标与非目标

### 2.1 目标

1. `/api/**` 的业务访问必须来自有效用户身份，并且服务端能够建立租户上下文。
2. `/internal/agent/**` 必须同时验证服务身份和本次诊断授权上下文。
3. server、worker、agent 使用独立 OAuth client、短期 JWT、固定 audience 和最小 scope。
4. 租户上下文只能从可信身份或 Diagnosis Grant 恢复，不能由调用方 Header 单独声明。
5. 认证失败、scope 越权、Grant 失败、IdP/JWKS 不可用和限流均可审计。
6. 私有化、离线和 Kubernetes 部署缺少安全配置时必须失败关闭。

### 2.2 非目标

- 不新建 AegisOps `auth-service`，生产环境复用企业 IdP 或 Keycloak。
- 不实现完整计费、billing quota、SAML 或企业用户 SSO。
- 不允许 AI 直接执行 SSH、Ansible、Webhook 或绕过 Runner 审批。
- 不把 server、worker、runner 拆成微服务；架构仍是模块化单体加外挂 Agent 和独立 Runner。
- 不把 `X-Tenant-Id`、网络位置、Kubernetes ServiceAccount 或 mTLS 单独当作业务授权凭据。

## 3. 服务间身份模型

Java 与 Agent 的调用分为两个方向：

```text
aiops-server / aiops-worker（发起 Incident 诊断）
  -> OAuth2 access token (aud: aiops-agent-api)
  -> Diagnosis Grant (tenantId + incidentId + traceId)
  -> aiops-agent

aiops-agent
  -> OAuth2 access token (aud: aegisops-internal-api)
  -> 原样传播 Diagnosis Grant
  -> /internal/agent/**
```

IdP 必须创建三个 confidential client：

| client         | 调用目标          | audience                | scopes                                                                                                |
| -------------- | ----------------- | ----------------------- | ----------------------------------------------------------------------------------------------------- |
| `aiops-server` | Agent API         | `aiops-agent-api`       | `agent:diagnose agent:resume agent:work-record`                                                       |
| `aiops-worker` | Agent API         | `aiops-agent-api`       | `agent:diagnose agent:work-record`                                                                    |
| `aiops-agent`  | Java internal API | `aegisops-internal-api` | `evidence:read cases:read checkpoint:read checkpoint:write memory:read memory:write plugin:authorize` |

三个 client secret 必须独立生成、独立注入和独立轮换。访问 Token 必须包含可验证的 `iss`、
`sub`、`aud`、`scope`、`iat` 和 `exp`；调用方只缓存尚未过期的 Token，不能把 Token 写入
日志、trace、错误响应或持久化配置。

## 4. Diagnosis Grant

OAuth2 Token 证明“哪个服务可以调用哪个端点”，Diagnosis Grant 证明“这次调用可以访问
哪个租户和 Incident”。两者用途不同，必须同时满足。

Java 发起诊断时签发短期 Grant，并通过以下 Header 发送：

```http
X-AegisOps-Diagnosis-Grant: <short-lived-signed-grant>
```

Grant 至少绑定：

```text
issuer
audience = aegisops-internal-api
tenantId
incidentId
traceId
issuedAt
expiresAt
jti
```

Agent 不持有 Grant 签名密钥，只能在该次诊断的下游工具调用中原样传播 Grant。Java 验证
签名、issuer、audience 和有效期后，从 Grant 恢复 `TenantContext`。Grant 是强制要求，不
存在关闭开关；缺失或无效时请求必须失败。

`X-Tenant-Id` 只可作为兼容和可观测字段，不能参与授权决策。即使该 Header 与 Grant 中的
租户一致，也不能替代 Grant；该 Header 也不能覆盖 Grant 中的授权上下文。

Grant 默认 TTL 为 300 秒，签名密钥至少 32 字节，只允许注入 server/worker。生产和离线
部署不得使用仓库中的开发默认值。

## 5. 请求安全边界

### 5.1 Public API

`/api/**` 必须验证用户身份和 RBAC，并由服务端建立租户上下文。业务查询必须包含
`tenantId` 条件；客户端参数不能覆盖已认证用户的租户。匿名端点使用独立的低限额，敏感
操作必须生成审计记录。

### 5.2 Internal Agent API

`/internal/agent/**` 的处理顺序为：

1. 验证 OAuth2 Bearer JWT 的签名、issuer、expiry、subject 和 audience。
2. 按目标端点验证 scope，不允许宽泛 client 跨端点调用。
3. 验证 Diagnosis Grant，并从 Grant 恢复租户和 Incident 上下文。
4. 使用已恢复的租户上下文执行限流、业务查询和审计。
5. 请求结束后清理 `TenantContext`，防止线程复用导致跨租户污染。

错误语义固定为：

- `401`：服务 Token 或 Diagnosis Grant 缺失、过期或无效。
- `403`：服务身份有效，但缺少目标端点 scope 或发生授权上下文冲突。
- `429`：已认证租户超过限流。
- `503`：IdP/JWKS 配置错误、不可达或暂时不可用。

## 6. 配置契约

以下示例只展示配置形状。生产值必须由 Secret 管理系统、Compose secret 或 Kubernetes
Secret 注入，不得在 values、镜像或仓库中写入真实 secret。

### 6.1 Java server/worker

```yaml
aiops:
  security:
    internal-agent-jwt-issuer-uri: ${AIOPS_INTERNAL_AGENT_JWT_ISSUER_URI}
    internal-agent-jwt-jwk-set-uri: ${AIOPS_INTERNAL_AGENT_JWT_JWK_SET_URI}
    internal-agent-jwt-audience: aegisops-internal-api
    diagnosis-grant-secret: ${AIOPS_DIAGNOSIS_GRANT_SECRET}
    diagnosis-grant-audience: aegisops-internal-api
    diagnosis-grant-issuers: aiops-server,aiops-worker
  agent:
    auth:
      token-uri: ${AIOPS_AGENT_OAUTH2_TOKEN_URI}
      client-id: ${AIOPS_AGENT_OAUTH2_CLIENT_ID}
      client-secret: ${AIOPS_AGENT_OAUTH2_CLIENT_SECRET}
      scope: ${AIOPS_AGENT_OAUTH2_SCOPE}
    grant:
      issuer: ${AIOPS_AGENT_GRANT_ISSUER}
      audience: aegisops-internal-api
      secret: ${AIOPS_DIAGNOSIS_GRANT_SECRET}
      ttl-seconds: 300
```

server 的 client 与 scope 必须配置为 `aiops-server` 和
`agent:diagnose agent:resume agent:work-record`；worker 必须配置为 `aiops-worker` 和
`agent:diagnose agent:work-record`。两者不能共用 client secret。

### 6.2 Python Agent

```dotenv
AIOPS_AGENT_INBOUND_OAUTH2_ISSUER=https://idp.example/realms/aegisops
AIOPS_AGENT_INBOUND_OAUTH2_JWKS_URL=https://idp.example/realms/aegisops/protocol/openid-connect/certs
AIOPS_AGENT_INBOUND_OAUTH2_AUDIENCE=aiops-agent-api
AIOPS_AGENT_OUTBOUND_OAUTH2_TOKEN_URL=https://idp.example/realms/aegisops/protocol/openid-connect/token
AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_ID=aiops-agent
AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_SECRET=<secret-manager-reference>
AIOPS_AGENT_OUTBOUND_OAUTH2_SCOPE=evidence:read cases:read checkpoint:read checkpoint:write memory:read memory:write plugin:authorize
```

Agent 只接收自己的 OAuth client secret，不接收 server/worker client secret，也不接收
Diagnosis Grant 签名密钥。获取 Token 和携带 Bearer/Grant 的内部请求必须绕过环境 HTTP
代理，避免凭据被代理进程截获。

## 7. 限流与审计

限流键必须来自已验证的用户租户或 Diagnosis Grant 租户，不能直接使用未验证 Header。
public API、internal Agent API 和匿名 API 使用独立 bucket。单实例内存限流只适用于开发或
单副本部署；多副本 SaaS 需要共享限流后端，但不能因此改变认证和 Grant 边界。

`tenant_security_event` 至少覆盖：

```text
tenant_missing
internal_auth_failed
internal_auth_forbidden
internal_auth_unavailable
diagnosis_grant_invalid
rate_limited
quota_exceeded
cross_tenant_denied
internal_auth_succeeded
```

审计可以记录 client subject、端点、tenantId、incidentId、traceId、requestId 和失败类型，
但不得记录 access token、client secret、Grant 原文或 Grant 签名密钥。事件类型契约通过新增
Flyway migration 演进，已发布 migration 不得修改。

## 8. 部署要求

- 本地开发可由 `infra/docker-compose.yml` 启动 Keycloak 并导入开发 realm。
- 生产 Compose 和 Helm 使用企业 IdP/Keycloak，缺少 issuer、JWKS、token endpoint、client
  secret 或 Diagnosis Grant secret 时必须失败关闭。
- 离线包不携带 IdP 镜像；包内 README 必须自包含三个 client 的 audience/scope 契约。
- Kubernetes 中 server、worker、agent、runner 使用独立 ServiceAccount 和 Secret，并用
  NetworkPolicy 限制调用方向。
- 启用 service mesh 时可叠加 STRICT mTLS 和 AuthorizationPolicy，但 mTLS 不替代 OAuth2
  scope 或 Diagnosis Grant。
- client secret 轮换按 client 独立执行；旧 secret 的吊销窗口不得长于短期 Token 的最大
  有效期。

## 9. 验收标准

1. 静态服务 Token 的生产代码、配置、部署参数和可执行文档示例均不存在。
2. 三个 client 获取的 Token 具有预期 issuer、audience、scope 和短期 expiry。
3. server/worker 带对应 OAuth2 Token 和 Diagnosis Grant 才能调用 Agent 诊断端点。
4. Agent 带 OAuth2 Token 和原始 Diagnosis Grant 才能调用 Java internal API。
5. 缺少 `exp`、错误 audience、错误 scope、无效 Grant 和 IdP/JWKS 故障均按约定失败。
6. Grant 中的 tenantId、incidentId 和 traceId 在整个诊断工具链保持一致。
7. `X-Tenant-Id` 单独存在时不能建立内部授权上下文。
8. 认证、授权、Grant 和限流失败均产生不泄露凭据的安全审计。
9. 私有化和离线部署使用外部 IdP，生成的 secret 文件不进入 Git 或离线包。

## 10. 验证入口

```bash
bash scripts/ci/docs.sh
bash scripts/ci/backend.sh
bash scripts/ci/agent.sh
bash scripts/ci/deployment.sh
bash scripts/ci/release-preflight.sh
```

发布流水线还必须执行真实 Keycloak runtime smoke：为三个 client 换取 Token，校验 claim，
再验证 Java、Agent 和 Diagnosis Grant 的双向调用链。只做静态 Compose/Helm 渲染不足以接受
本设计。

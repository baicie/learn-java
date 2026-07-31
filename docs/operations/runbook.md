---
title: AegisOps Production Runbook
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-01
related:
  - docs/adr/0009-service-to-service-authentication.md
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/api/internal-service-authentication.md
---

# AegisOps Production Runbook

## 1. Pod Not Ready

```bash
kubectl get pods -n aegisops
kubectl describe pod <pod> -n aegisops
kubectl logs <pod> -n aegisops --tail=200
```

Check:

- readiness path
- external PostgreSQL
- Redis
- IdP / JWKS 连通性
- OAuth2 client secret
- Diagnosis Grant 配置
- image pull secret
- resource limits

## 2. High 5xx Rate

Check dashboard:

- HTTP 5xx Rate
- p95 latency
- JVM memory
- DB connectivity

Commands:

```bash
kubectl logs deploy/aegisops-aegisops-server -n aegisops --tail=200
```

## 3. Agent High Error Rate

Check:

```bash
kubectl get pods -n aegisops -l app.kubernetes.io/component=agent
kubectl logs deploy/aegisops-aegisops-agent -n aegisops --tail=200
```

Check env:

- `AIOPS_AGENT_INBOUND_OAUTH2_ISSUER`
- `AIOPS_AGENT_INBOUND_OAUTH2_JWKS_URL`
- `AIOPS_AGENT_INBOUND_OAUTH2_AUDIENCE`
- `AIOPS_AGENT_WORKFLOW_API_BASE_URL`
- `AIOPS_AGENT_EVIDENCE_BASE_URL`

## 4. Internal Agent 401

依次检查：

- 服务 JWT 的 issuer、audience、expiry 和目标端点 scope。
- IdP token endpoint 与 JWKS endpoint 是否可达。
- `X-AegisOps-Diagnosis-Grant` 是否存在、过期或 issuer 不允许。
- server 与 worker 的 `AIOPS_DIAGNOSIS_GRANT_SECRET` 是否一致。
- 对应组件的 OAuth2 client id、client secret 与端点 scope 是否匹配 IdP 配置。

## 5. Tenant Missing

内部 API 的租户授权来自有效 Diagnosis Grant。`X-Tenant-Id` 可以继续发送用于兼容和
可观测，但不得作为授权来源。若出现 `TENANT_REQUIRED` 或 `DIAGNOSIS_GRANT_INVALID`，检查：

- Grant 是否包含非空 `tenantId`、`incidentId`、`traceId`。
- Grant audience 是否为 `aegisops-internal-api`。
- 公共 API 是否能从用户 JWT 恢复租户。

## 6. 首次从静态 Token 升级到 OAuth2

首次升级必须安排协调变更窗口，同时升级 server、worker 和 agent，不能长期运行混合版本。
上线前先在 IdP 创建并验证三个独立 confidential client：

| client         | audience                | 必需 scope                                                                                            |
| -------------- | ----------------------- | ----------------------------------------------------------------------------------------------------- |
| `aiops-server` | `aiops-agent-api`       | `agent:diagnose agent:resume agent:work-record`                                                       |
| `aiops-worker` | `aiops-agent-api`       | `agent:diagnose agent:work-record`                                                                    |
| `aiops-agent`  | `aegisops-internal-api` | `evidence:read cases:read checkpoint:read checkpoint:write memory:read memory:write plugin:authorize` |

在变更窗口开始前完成以下预验证：

1. discovery、token 和 JWKS endpoint 从目标运行网络可达。
2. 三个 client 分别换取 Token，且 secret 互不相同。
3. Token 包含正确的 `iss`、`sub`、`aud`、`scope`、`iat` 和 `exp`，并满足
   `0 < exp - iat <= 300s`。
4. server 与 worker 配置相同的 Diagnosis Grant audience、issuer allowlist 和至少 32 字节
   的签名密钥；Agent 容器中不存在该签名密钥。
5. 新部署描述符中的 server、worker、agent 均声明 `AIOPS_SERVICE_AUTH_CONTRACT=oauth2-v1`。

首次切换后禁止自动回滚到旧静态 Token 版本。若 IdP、scope、audience、JWKS 或 secret 配置
有误，应暂停新的 AI 诊断请求并前向修复配置，再逐个重启受影响 workload。不要重新启用旧
Header、长期共享 Token 或 static 兼容分支。

完成首次升级后，后续版本回滚只允许使用满足以下条件的完整历史部署：

- server、worker、agent 三个历史容器都带 `oauth2-v1` 契约标记。
- 历史镜像、部署描述符和 client 配置属于同一次已验证发布。
- 回滚不会恢复已经轮换或吊销的 client secret。

任何组件缺少契约标记时，回滚门禁必须拒绝该历史版本。

### 6.1 VM 发布描述符恢复语义

VM 发布不得在验证前覆盖 active 描述符：

- `docker-compose.app.yml` 是 active，只对应最后完整通过健康检查与双向 OAuth2 探针的版本。
- `docker-compose.app.candidate.yml` 是本次待发布版本。
- `docker-compose.app.previous.yml` 是 candidate 校验通过后，对 active 的原子快照。

拉镜像或端口预检失败时，运行容器和 active 均保持不变。新容器启动后失败时，脚本使用
previous 与部署前捕获的不可变镜像执行回滚；即使回滚健康检查或鉴权探针失败，也不得把
candidate 写入 active。只有新版本全部验证成功后，candidate 才原子提升为 active。连续发布
时必须始终从 active 生成 previous，不能从上一次失败遗留的 candidate 生成。

## 7. OAuth2 故障人工恢复

按错误类型处理：

- `401 internal_auth_failed`：检查 Bearer 格式、签名、issuer、audience、`iat/exp` 和主体。
- `403 internal_auth_forbidden`：检查 client scope 与目标端点 scope，不扩大其他 client 权限。
- `503 internal_auth_unavailable`：检查 IdP/JWKS 网络、DNS、证书和服务状态。
- `diagnosis_grant_missing` / `DIAGNOSIS_GRANT_INVALID`：检查 Java 签发和 Agent 原样传播路径，
  不把 `X-Tenant-Id` 当作替代凭据。

恢复步骤：

1. 暂停触发新的 AI 诊断，保留已有 Incident、审计和执行记录。
2. 从对应 Pod 验证 discovery、JWKS 和 token endpoint 连通性，不输出 Token 或 client secret。
3. 分别核对三个 client 的 audience、scope、启用状态和 secret 版本。
4. 通过 Secret 管理系统修正配置，只滚动重启使用该 Secret 的 workload。
5. 观察结构化事件 `internal_auth_failed`、`internal_auth_forbidden`、
   `internal_auth_unavailable` 和 `diagnosis_grant_missing` 是否停止增长。
6. 通过后述非破坏性检查确认 IdP 契约，再在指定的合成租户和 Incident 上恢复业务烟测。

## 8. 生产健康后的非破坏性 OAuth2 检查

`deploy/scripts/deploy-app.sh` 在所有容器通过健康检查后自动运行
`deploy/scripts/verify-service-auth-oauth.py`。该脚本执行以下非破坏性检查：

1. JWKS endpoint 返回至少一个签名公钥。
2. server、worker、agent 三个 client 分别换取短期 Token，并在内存中校验
   `iss/sub/aud/scope/iat/exp`。
3. server 和 worker 分别调用 Agent 的 `POST /v1/auth/probe`。
4. agent 携带合成 Diagnosis Grant 调用 Java 的 `POST /internal/agent/auth/probe`，并核对
   返回的 `tenantId + incidentId + traceId`。

两个 probe 都不读写业务数据，不触发诊断、Runner、审批或通知。命令输出禁止包含 Token、
client secret、Diagnosis Grant 或完整 claims。探针失败会使部署失败；只有旧部署的
server、worker、agent 都声明 `AIOPS_SERVICE_AUTH_CONTRACT=oauth2-v1` 时，部署脚本才允许
自动回滚，并在回滚后重新运行同一探针。

`scripts/ci/verify-service-auth-runtime.py` 会幂等创建并清理合成 Incident 与 change marker，
用于 CI 或预发布环境验证完整的 Java -> Agent -> Java 回调路径，不应直接对生产数据库运行。
生产需要完整业务烟测时，必须使用已批准的合成租户和 Incident，并确认不会触发 Runner、
自动化审批或外部通知。

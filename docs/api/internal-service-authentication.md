---
title: 内部服务鉴权契约
type: api
status: accepted
phase: phase-8
owner: ai
created: 2026-07-30
updated: 2026-08-01
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/designs/phase-8/2026-08-01-mtls-task-grants.md
  - docs/deployment/production-security-checklist.md
---

# 内部服务鉴权契约

## 工作负载身份

App 与 Agent 使用双向 TLS，不再发送服务 Bearer Token，也不依赖 OAuth2 Token Endpoint、
Keycloak 或 JWKS。

| 调用方向    | TLS 身份 URI SAN                               | 目标端口 |
| ----------- | ---------------------------------------------- | -------- |
| App → Agent | `spiffe://aegisops.local/service/aegisops-app` | `9008`   |
| Agent → App | `spiffe://aegisops.local/service/aiops-agent`  | `8443`   |

证书信任按角色隔离：Agent 只信任 control-plane CA；App 的内部 Connector 只信任 agent CA。
App 公共端口 8080 不接受内部 Agent 回调，8443 不映射到宿主机或公网。健康检查可以只验证 TCP/TLS
状态，但不能以跳过证书校验作为探针。

## Diagnosis Grant

诊断请求必须发送：

```http
X-AegisOps-Diagnosis-Grant: <short-lived-Ed25519-JWT>
```

Grant 必须包含并校验 `iss`、`sub=diagnosis:<diagnosisId>`、`aud`、`scope`、`tenantId`、
`incidentId`、`diagnosisId`、`traceId`、`iat`、`exp`、`jti`。TTL 最长 300 秒，算法固定为
`EdDSA`，并通过 `kid` 支持当前/前一把公钥轮换。

| 调用方向    | audience                | 端点                                       | scope               |
| ----------- | ----------------------- | ------------------------------------------ | ------------------- |
| App → Agent | `aiops-agent-api`       | `POST /v1/diagnose`                        | `diagnosis:execute` |
| App → Agent | `aiops-agent-api`       | `POST /v1/diagnose/resume`                 | `diagnosis:resume`  |
| Agent → App | `aegisops-internal-api` | `POST /internal/agent/auth/probe`          | `evidence:read`     |
| Agent → App | `aegisops-internal-api` | `/internal/agent/evidence/*`               | `evidence:read`     |
| Agent → App | `aegisops-internal-api` | `/internal/agent/tools/search-cases`       | `cases:read`        |
| Agent → App | `aegisops-internal-api` | `/internal/agent/plugins/tools/authorize`  | `plugin:authorize`  |
| Agent → App | `aegisops-internal-api` | `POST /internal/agent/memories/search`     | `memory:read`       |
| Agent → App | `aegisops-internal-api` | `POST /internal/agent/memories`            | `memory:write`      |
| Agent → App | `aegisops-internal-api` | 保留：`GET /internal/agent/checkpoints/*`  | `checkpoint:read`   |
| Agent → App | `aegisops-internal-api` | 保留：写入 `/internal/agent/checkpoints/*` | `checkpoint:write`  |

Agent 在执行诊断前验证 Grant，并在 Java 工具调用中原样传播。App 再次验证后从 Grant 恢复
`TenantContext`；`X-Tenant-Id` 只用于兼容和可观测，不参与授权。Evidence 请求的
`tenantId + incidentId + traceId` 必须与 Grant 完全一致。

默认签发 scope 只有 `diagnosis:execute` 和 `diagnosis:resume`。启用表中某项内部工具能力时，
部署方必须通过 `AIOPS_DIAGNOSIS_GRANT_SCOPES` 仅加入实际需要的 scope；未启用的工具不得固定
授予。Resume 请求、Grant 与 checkpoint 状态必须同时匹配 tenant、Incident、diagnosis 与
trace 四项上下文，仅租户相同不足以恢复任务。

`POST /v1/work-record/generate` 不具备 Incident 诊断上下文，仅要求有效 App mTLS 身份；它不接收
Diagnosis Grant。Checkpoint 路径目前只保留 scope 与客户端契约，在服务端接口落地前不应视为
可调用的生产 API。

## Execution Grant

App 创建普通、重试或回滚执行时，必须写入 Ed25519 Execution Grant、稳定快照 SHA-256 和到期
时间。Grant 绑定 `tenantId + incidentId + executionId + planId + mode + executionKind +
snapshotSha256 + maxDurationSeconds`，audience 固定为 `aiops-runner`，scope 必须包含
`runbook:execute`。

Runner 原子领取任务后、改变任何步骤状态或调用任何 `StepExecutor` 前，必须完成以下检查：

```text
Ed25519 签名与 kid
issuer / audience / scope / subject
iat / exp / maxDurationSeconds
租户、Incident、Execution、Plan、Mode
审批 ID 与审批快照
执行类型、回滚引用、重试次数与超时
按 sequence 排序的全部不可变步骤字段 SHA-256
live 审批状态为 approved，approvalId/planId 一致，approvedCount 达到 requiredApprovals 且至少为 1
```

验证失败时 Runner 将执行标记为 failed，写入不含 Token、参数或完整 claims 的
`execution_audit_event`，且执行器调用次数必须为零。历史升级记录缺少 Grant 时同样失败关闭。

## 密钥分配

```text
aegisops-app: Grant private key + public key
aiops-agent:  Grant public key
aiops-runner: Grant public key
```

Grant 私钥只能进入 App。证书私钥按组件分别挂载，Runner 不需要 mTLS 证书。密钥和证书不得
进入镜像、Git、日志、trace attribute 或错误响应。

轮换时接收方必须同时加载当前和前一把公钥；签发方只使用当前私钥。Compose `--force` 自动
保存一代 previous 公钥与 `kid`，再次轮换前必须等待更早公钥签发的任务完成或过期。

## 错误语义

- `401`：mTLS 身份缺失/无效，或 Grant 缺失、过期、签名/issuer/audience 无效。
- `403`：工作负载身份有效，但 scope 或资源上下文不允许当前操作。
- `503`：证书、公钥或内部认证配置无法加载。
- Runner Grant 拒绝不通过 HTTP 返回；执行记录变为 `failed` 并生成脱敏审计事件。

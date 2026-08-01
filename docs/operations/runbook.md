---
title: AegisOps Production Runbook
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-01
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
---

# AegisOps Production Runbook

## 1. 运行拓扑

```text
诊断模式:   aegisops-app + aiops-agent + PostgreSQL
自动化模式: 诊断模式 + aiops-runner
```

外部只访问 App 8080。App 8443、Agent 9008 和 Runner 状态端口仅在内部网络可达。默认一键安装：

```bash
bash deploy/install.sh
```

启用自动化：

```bash
bash deploy/install.sh --mode automation
```

安装器默认拒绝覆盖已有安全材料。证书/密钥轮换必须先验证数据库凭据、当前/前一把公钥窗口和
回滚材料，不能直接删除 `deploy/runtime`。

## 2. 容器未就绪

```bash
docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai ps
docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai logs --tail=200 aegisops-app aiops-agent
```

依次检查：

- PostgreSQL 与 `aegisops_app` 凭据；Runner 另查 `aegisops_runner`。
- App/Agent 证书文件、私钥权限、角色 CA 与证书有效期。
- Grant 当前 `kid`、公钥文件和仅 App 可见的私钥。
- App 8443 与 Agent 9008 的内部 DNS/网络连通性。
- 镜像拉取、资源限制与只读文件系统的临时目录。

不要通过关闭证书校验、回退 HTTP、重新启用静态 Token 或 OAuth2 兼容代码恢复服务。

## 3. Agent 高错误率

重点核对：

```text
AIOPS_AGENT_TLS_CERTIFICATE_FILE
AIOPS_AGENT_TLS_PRIVATE_KEY_FILE
AIOPS_AGENT_TLS_CLIENT_CA_FILE
AIOPS_AGENT_DIAGNOSIS_GRANT_PUBLIC_KEY_FILE
AIOPS_AGENT_DIAGNOSIS_GRANT_KEY_ID
AIOPS_AGENT_WORKFLOW_API_BASE_URL
AIOPS_AGENT_EVIDENCE_BASE_URL
```

`401` 通常表示缺失/无效/过期 Grant；`403` 通常表示 scope 或资源上下文不匹配；TLS 握手失败
应检查证书链、DNS SAN/URI SAN、时间同步和角色 CA，不会表现为业务 HTTP 状态码。

## 4. Internal Agent 401/403

依次检查：

1. 请求是否进入 App 8443，而不是公共 8080。
2. Agent 证书是否由 agent CA 签发，URI SAN 是否为
   `spiffe://aegisops.local/service/aiops-agent`。
3. `X-AegisOps-Diagnosis-Grant` 是否由当前/前一把 Ed25519 公钥验证通过。
4. Grant 是否包含双 audience、端点所需 scope，以及匹配的 tenant/incident/diagnosis/trace。
5. 主机时间偏差是否超过允许窗口。

内部租户授权只来自有效 Diagnosis Grant。`X-Tenant-Id` 可以用于可观测，但不能作为替代凭据。

## 5. Runner 拒绝任务

查询执行记录和 `execution_audit_event`，关注 `execution_grant_rejected` 等脱敏事件。检查：

- Grant 是否缺失、过期、`kid` 未加载或 issuer/audience/scope 错误。
- `execution_snapshot_sha256` 是否与当前执行行及按 sequence 排序的步骤一致。
- 审批 ID/快照、mode、执行类型、回滚引用、重试次数或 timeout 是否被修改。
- Runner 是否使用独立账号，且权限覆盖领取、心跳、状态、产物和审计所需表。

Grant 拒绝时不得手工改状态绕过验证，也不得把私钥挂入 Runner。应停止领取新任务，修复 App
签发或数据库一致性后重新走创建/审批流程。

## 6. 非破坏性 mTLS 检查

部署探针 `deploy/scripts/verify-internal-mtls.py` 使用客户端证书建立真实 TLS 连接，校验服务端
证书链、DNS 与预期 SPIFFE URI SAN。探针不得打印私钥、Grant、数据库密码或完整证书内容。

完整 Java → Agent → Java 业务烟测必须使用已批准的合成租户和 Incident，并确认不会触发
Runner、自动化审批或外部通知。

## 7. 发布与回滚

VM 发布不得在验证前覆盖 active 描述符或现有安全材料。新镜像应先作为 candidate 启动，依次
通过 PostgreSQL migration、App/Agent 健康、双向 mTLS 和合成诊断检查后再提升为 active。

回滚必须使用同一次已验证发布的镜像、Compose/Helm 描述符、证书信任窗口和 Grant 公钥集合。
如果新版本已经写入不可逆 migration，先按 migration 兼容性评估，不得只回滚镜像。Runner 已
领取的任务保留审计记录，不得删除数据库行来伪造回滚成功。

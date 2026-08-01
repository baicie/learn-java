---
title: AegisOps Private Deployment
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-01
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
---

# AegisOps Private Deployment

## 部署方式

推荐使用 Helm 部署：

```bash
helm upgrade --install aegisops deploy/helm/aegisops \
  -n aegisops --create-namespace \
  -f deploy/helm/aegisops/values-private.yaml \
  -f deploy/generated-secrets.values.yaml
```

## 外部依赖

生产必需依赖只有 PostgreSQL。以下中间件按实际能力启用：

- PostgreSQL
- Redis（多副本共享限流/缓存）
- ClickHouse（高吞吐事件明细）
- MinIO（附件与产物）
- VictoriaMetrics（指标证据）

内部 App/Agent 通信不需要企业 IdP 或 Keycloak。用户登录仍使用 App 的 JWT/RBAC；如需接入
企业用户 OIDC，应作为公共身份接入单独配置，不得复用为内部工作负载鉴权。

## Secret

生成 Secret values：

```bash
scripts/deploy/generate-secrets.sh
```

生成的文件不要提交到 Git。

Secret 必须分别包含 App/Agent 证书私钥、角色 CA trust bundle、Grant 当前/前一把公钥、仅 App
可见的 Grant 私钥，以及互不相同的 `aegisops_app` / `aegisops_runner` 数据库凭据。生成文件
必须通过安全通道传递，不得提交到 Git 或打进镜像。

## 网络隔离

生产 values 应启用 `networkPolicy.enabled=true`，并显式把
`networkPolicy.egress.allowedCidrs` 配置为实际外部数据服务网段；空列表、
`0.0.0.0/0` 与 `::/0` 会在 Helm 渲染阶段失败。`networkPolicy.egress.externalPorts`
按 app、runner、agent 分别维护端口白名单；未部署或未使用的外部依赖端口必须删除。Agent
默认只允许访问 App 8443 和配置的 LLM endpoint；App 与 Agent 的集群内双向调用由独立 Pod
selector 规则放行。Runner 只访问 PostgreSQL 和执行目标，不允许访问 Agent。

NetworkPolicy 不支持按域名匹配。使用外部域名时，仍需维护对应出口 CIDR；仅配置
`dnsNamespaceSelector` 只代表允许 DNS 查询，不代表允许访问任意解析结果。

## 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
kubectl logs -n aegisops deploy/aegisops-aegisops-app
```

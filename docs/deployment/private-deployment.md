---
title: AegisOps Private Deployment
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-31
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
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

生产环境建议使用客户已有中间件：

- PostgreSQL
- Redis
- ClickHouse
- MinIO
- VictoriaMetrics
- 支持 OAuth2 Client Credentials 的企业 IdP 或 Keycloak

IdP 必须创建 `aiops-server`、`aiops-worker`、`aiops-agent` 三个独立 client，并按
`docs/api/internal-service-authentication.md` 配置 audience 与 endpoint scope。Helm chart 不会
部署生产 IdP；`deploy/docker-compose.idp.yml` 仅供发布烟测使用。

## Secret

生成 Secret values：

```bash
scripts/deploy/generate-secrets.sh
```

生成的文件不要提交到 Git。

新建 Keycloak client 时，把生成文件中的三个 client secret 通过安全通道配置到 IdP。若使用
企业 IdP 已分配的 secret，则覆盖生成文件中的相应值，保证 Helm Secret 与 IdP 完全一致。

## 网络隔离

生产 values 应启用 `networkPolicy.enabled=true`，并显式把
`networkPolicy.egress.allowedCidrs` 配置为实际 IdP 和外部数据服务网段；空列表、
`0.0.0.0/0` 与 `::/0` 会在 Helm 渲染阶段失败。`networkPolicy.egress.externalPorts`
是按 server、worker、runner、agent
分别维护的端口白名单；未部署或未使用的外部依赖端口必须删除。Agent 默认只保留 IdP HTTPS
端口，Java 与 Agent 的集群内双向调用由独立 Pod selector 规则放行。

NetworkPolicy 不支持按域名匹配。使用外部域名时，仍需维护对应出口 CIDR；仅配置
`dnsNamespaceSelector` 只代表允许 DNS 查询，不代表允许访问任意解析结果。

## 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
kubectl logs -n aegisops deploy/aegisops-aegisops-server
```

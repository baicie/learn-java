---
title: AegisOps Private Deployment
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# AegisOps Private Deployment

## 部署方式

推荐使用 Helm 部署：

```bash
helm upgrade --install aegisops deploy/helm/aegisops \
  -n aegisops --create-namespace \
  -f deploy/helm/aegisops/values-private.yaml \
  -f deploy/helm/aegisops/generated-secrets.values.yaml
```

## 外部依赖

生产环境建议使用客户已有中间件：

- PostgreSQL
- Redis
- ClickHouse
- MinIO
- VictoriaMetrics

## Secret

生成 Secret values：

```bash
scripts/deploy/generate-secrets.sh
```

生成的文件不要提交到 Git。

## 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
kubectl logs -n aegisops deploy/aegisops-aegisops-server
```

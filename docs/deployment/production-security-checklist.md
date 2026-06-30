---
title: Production Security Checklist
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Production Security Checklist

## 必须修改

- [ ] security.internalAgentToken
- [ ] security.jwtSecret
- [ ] external.postgres.password
- [ ] external.redis.password
- [ ] external.clickhouse.password
- [ ] external.minio.accessKey
- [ ] external.minio.secretKey

## Kubernetes

- [ ] 开启 NetworkPolicy
- [ ] 开启 Ingress TLS
- [ ] 使用私有镜像仓库
- [ ] 禁止使用 latest tag
- [ ] 限制 Pod resource requests / limits
- [ ] 使用独立 namespace
- [ ] 使用最小权限 ServiceAccount

## AegisOps

- [ ] internal agent token required
- [ ] tenant required
- [ ] public API rate limit enabled
- [ ] internal agent rate limit enabled
- [ ] plugin tool policy 默认关闭或显式 allow
- [ ] Runner live 执行默认关闭
- [ ] Ansible / SSH adapter 默认关闭

## 备份

- [ ] PostgreSQL backup
- [ ] ClickHouse backup
- [ ] MinIO backup
- [ ] Helm values secret backup

---
title: 生产安全检查清单
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-30
related:
  - docs/adr/0009-service-to-service-authentication.md
  - docs/api/internal-service-authentication.md
---

# 生产安全检查清单

## 必须修改

- [ ] security.jwtSecret
- [ ] security.diagnosisGrantSecret（至少 32 字节）
- [ ] security.serviceAuth.issuerUri / jwkSetUri / tokenUri
- [ ] server / worker / agent 使用不同 OAuth2 client secret
- [ ] external.postgres.password
- [ ] external.redis.password
- [ ] external.clickhouse.password
- [ ] external.minio.accessKey
- [ ] external.minio.secretKey

## Kubernetes

- [ ] 开启 NetworkPolicy
- [ ] server / worker / runner / agent 使用独立 ServiceAccount
- [ ] server / worker / agent 使用独立认证 Secret
- [ ] Agent 不接收数据库 Secret 与 Diagnosis Grant 签名密钥
- [ ] Runner 不接收 Agent 服务凭据
- [ ] 使用 Service Mesh 时开启 STRICT mTLS 与 AuthorizationPolicy
- [ ] 开启 Ingress TLS
- [ ] 使用私有镜像仓库
- [ ] 禁止使用 latest tag
- [ ] 限制 Pod resource requests / limits
- [ ] 使用独立 namespace
- [ ] 使用最小权限 ServiceAccount

## AegisOps

- [ ] 生产模式使用 OAuth2 Client Credentials，不使用静态 token
- [ ] 服务 JWT 校验 issuer、audience、subject、expiry 与 endpoint scope
- [ ] Diagnosis Grant required，TTL 不超过 300 秒
- [ ] 内部租户上下文只从有效 Diagnosis Grant 恢复
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

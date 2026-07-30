---
title: AegisOps Production Runbook
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

- `AIOPS_AGENT_INBOUND_AUTH_MODE`
- `AIOPS_AGENT_INBOUND_OAUTH2_ISSUER`
- `AIOPS_AGENT_INBOUND_OAUTH2_JWKS_URL`
- `AIOPS_AGENT_INBOUND_OAUTH2_AUDIENCE`
- `AIOPS_AGENT_DIAGNOSIS_GRANT_REQUIRED`
- `AIOPS_AGENT_EVIDENCE_API_BASE_URL`
- `AIOPS_AGENT_MEMORY_API_BASE_URL`

## 4. Internal Agent 401

依次检查：

- 服务 JWT 的 issuer、audience、expiry 和目标端点 scope。
- IdP token endpoint 与 JWKS endpoint 是否可达。
- `X-AegisOps-Diagnosis-Grant` 是否存在、过期或 issuer 不允许。
- server 与 worker 的 `AIOPS_DIAGNOSIS_GRANT_SECRET` 是否一致。
- 静态兼容模式下，Java→Agent 与 Agent→Java token 必须不同且分别匹配。

## 5. Tenant Missing

内部 API 的租户授权来自有效 Diagnosis Grant。`X-Tenant-Id` 可以继续发送用于兼容和
可观测，但不得作为授权来源。若出现 `TENANT_REQUIRED` 或 `DIAGNOSIS_GRANT_INVALID`，检查：

- Grant 是否包含非空 `tenantId`、`incidentId`、`traceId`。
- Grant audience 是否为 `aegisops-internal-api`。
- 公共 API 是否能从用户 JWT 恢复租户。

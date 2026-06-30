---
title: AegisOps Production Runbook
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
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
- internal agent token
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

- `AIOPS_AGENT_INTERNAL_AGENT_TOKEN`
- `AIOPS_AGENT_EVIDENCE_API_BASE_URL`
- `AIOPS_AGENT_MEMORY_API_BASE_URL`

## 4. Internal Agent 401

Check Java token and Python token are equal:

- `AIOPS_INTERNAL_AGENT_TOKEN`
- `AIOPS_AGENT_INTERNAL_AGENT_TOKEN`

## 5. Tenant Missing

All internal API requests must send:

- `X-Tenant-Id`

Public API may resolve tenant from JWT.

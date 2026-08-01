---
title: Phase8.0 SaaS Multi-tenant Hardening
type: design
status: deprecated
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-30
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
---

# Phase8.0 SaaS Multi-tenant Hardening

> 历史设计（已废弃）：本文记录最初的静态 Token 方案，其中配置和 Header 示例不得继续
> 执行。当前服务鉴权只支持 OAuth2 Client Credentials，并强制使用 Diagnosis Grant；以
> `docs/adr/0010-service-authentication-oauth2-only.md` 为准。

## 目标

Phase8.0 为 AegisOps 增加 SaaS 多租户安全基线：

- tenant context required
- internal agent token
- tenant rate limit
- quota policy baseline
- security event audit
- Python Agent internal headers

## 不做

- 不做完整计费
- 不做 SSO/OIDC/SAML
- 不做 Redis 分布式限流
- 不做 WAF
- 不新增执行能力

## Java

新增：

- TenantRequiredFilter
- InternalAgentAuthFilter
- TenantRateLimitFilter
- InMemoryTenantRateLimiter
- TenantSecurityAuditService

## Python Agent

所有 internal Java API 调用必须携带：

- X-Tenant-Id
- X-AIOPS-INTERNAL-TOKEN

## 安全边界

- /api/\*\* 必须有 tenant context
- /internal/agent/\*\* 必须有 tenant context + internal token
- rate limit by tenant
- security event audit for auth failure / tenant missing / rate limited

## 环境变量

Java:

```env
AIOPS_INTERNAL_AGENT_TOKEN=change-me
AIOPS_PUBLIC_API_RPM=600
AIOPS_INTERNAL_AGENT_RPM=1200
```

Python:

```env
AIOPS_AGENT_INTERNAL_AGENT_TOKEN=change-me
```

## 验收标准

1. /api/\*\* 无 tenant 被拒绝。
2. /internal/agent/\*\* 无 token 被拒绝。
3. /internal/agent/\*\* token 错误被拒绝。
4. /internal/agent/\*\* token 正确可继续。
5. Python EvidenceClient 带 X-AIOPS-INTERNAL-TOKEN。
6. Python KnowledgeClient 带 X-AIOPS-INTERNAL-TOKEN。
7. Python CheckpointClient 带 X-AIOPS-INTERNAL-TOKEN。
8. Python MemoryClient 带 X-AIOPS-INTERNAL-TOKEN。
9. tenant rate limit 生效。
10. security event 可记录。

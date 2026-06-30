---
title: Phase8.0：SaaS Multi-tenant Hardening
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase8.0：SaaS Multi-tenant Hardening

> Phase8.0 目标：把 AegisOps 从“功能型 MVP”推进到“可 SaaS 化 / 可多租户隔离 / 可私有化安全部署”的基础安全阶段。
> 这一阶段重点不是新业务功能，而是 **租户隔离、内部 API 认证、Agent Tool 调用安全、限流、审计、Quota 基线**。

---

# 1. Phase8.0 定位

前置阶段已经有：

```txt id="a5nr4t"
Phase7.0 Agent Graph Modularization
Phase7.1 Human-in-the-loop Checkpoint
Phase7.2 Multi-Agent Collaboration
Phase7.3 Agent Memory
```

Phase8.0 主要处理这些风险：

```txt id="q5lb2s"
1. /internal/agent/** 不能裸奔
2. Python Agent 调 Java internal API 必须带内部 token
3. public /api/** 必须有 tenant context
4. internal API 也必须有 tenant context
5. tenant 级别 API 限流
6. internal agent tool 限流
7. quota policy 基础表
8. security event 审计
```

---

# 2. Phase8.0 不做什么

```txt id="fj4jom"
1. 不做完整计费系统
2. 不做企业级 IAM
3. 不做 SSO/SAML/OIDC
4. 不做 Redis 分布式限流
5. 不做全链路 WAF
6. 不做真正的 billing quota enforcement
7. 不改变 Agent 诊断逻辑
8. 不新增 Runner 执行能力
```

分布式限流、SSO、Billing 放后续 Phase8.1+。

---

# 3. 总体设计

## 3.1 Java 侧新增

```txt id="he41o6"
modules/aiops-security
  InternalAgentAuthFilter
  TenantRequiredFilter
  TenantRateLimitFilter
  InMemoryTenantRateLimiter
  TenantSecurityAuditService

modules/aiops-tenant
  tenant_quota_policy
  tenant_security_event
```

如果你现在没有单独 `aiops-security` 模块，也可以先放在：

```txt id="v1oeaa"
modules/aiops-web
或
modules/aiops-common
```

但推荐放 `modules/aiops-security`。

---

## 3.2 Python Agent 侧新增

实际路径按你现在项目来：

```txt id="zu0ae9"
apps/aiops-agent/src/aiops_agent/
  workflow/
    tools/
      internal_auth.py
      evidence_client.py
      knowledge_client.py
      checkpoint_client.py
      memory_client.py
```

所有 Python Agent 调 Java 的 internal API，都统一带：

```txt id="1k60h2"
X-Tenant-Id: <tenant_id>
X-AIOPS-INTERNAL-TOKEN: <configured token>
```

---

# 4. 请求安全边界

## 4.1 Public API

```txt id="ilfns4"
/api/**
```

必须满足：

```txt id="gf40dj"
1. 已登录 / 已认证
2. TenantContext 必须存在
3. 每分钟 tenant 限流
4. 记录必要 security event
```

---

## 4.2 Internal Agent API

```txt id="flx25h"
/internal/agent/**
```

必须满足：

```txt id="upnyz9"
1. X-Tenant-Id 必须存在
2. X-AIOPS-INTERNAL-TOKEN 必须正确
3. tenant 级 internal 限流
4. 不允许跨租户
5. 失败审计
```

---

# 5. 配置项

## 5.1 Java `application.yml`

路径：

```txt id="3ac89a"
apps/aiops-server/src/main/resources/application.yml
```

新增：

```yaml id="5fq6rn"
aiops:
  security:
    internal-agent-token: ${AIOPS_INTERNAL_AGENT_TOKEN:dev-internal-agent-token}
    internal-agent-token-required: true
    tenant-required: true
  quota:
    public-api-requests-per-minute: ${AIOPS_PUBLIC_API_RPM:600}
    internal-agent-requests-per-minute: ${AIOPS_INTERNAL_AGENT_RPM:1200}
    rate-limit-enabled: true
```

> 生产环境必须通过环境变量覆盖 `AIOPS_INTERNAL_AGENT_TOKEN`，不要使用默认值。

---

## 5.2 Python Agent `.env`

路径：

```txt id="efwq0q"
apps/aiops-agent/.env.example
```

新增：

```env id="grihap"
AIOPS_AGENT_INTERNAL_AGENT_TOKEN=dev-internal-agent-token
AIOPS_AGENT_EVIDENCE_API_BASE_URL=http://localhost:8080
AIOPS_AGENT_KNOWLEDGE_API_BASE_URL=http://localhost:8080
AIOPS_AGENT_CHECKPOINT_API_BASE_URL=http://localhost:8080
AIOPS_AGENT_MEMORY_API_BASE_URL=http://localhost:8080
```

---

# 6. Migration

路径：

```txt id="8r764e"
apps/aiops-server/src/main/resources/db/migration/V26__phase8_0_saas_tenant_hardening.sql
```

```sql id="n54ugb"
-- Phase 8.0: SaaS Multi-tenant Hardening.
-- Adds baseline tenant quota policy and security event audit.
-- This phase does not add runner execution capability.

create table if not exists tenant_quota_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  status varchar(32) not null default 'active',

  public_api_requests_per_minute int not null default 600,
  internal_agent_requests_per_minute int not null default 1200,

  max_active_incidents int not null default 1000,
  max_agent_memories int not null default 10000,
  max_kb_documents int not null default 10000,
  max_monthly_ai_diagnoses int not null default 100000,

  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_quota_policy unique (tenant_id),
  constraint ck_tenant_quota_policy_status
    check (status in ('active', 'disabled')),
  constraint ck_tenant_quota_public_api_rpm
    check (public_api_requests_per_minute > 0),
  constraint ck_tenant_quota_internal_agent_rpm
    check (internal_agent_requests_per_minute > 0)
);

create index if not exists idx_tenant_quota_policy_status
  on tenant_quota_policy(tenant_id, status);

create table if not exists tenant_security_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  event_type varchar(64) not null,
  severity varchar(32) not null default 'medium',
  actor varchar(128) not null default 'unknown',
  request_path varchar(512),
  remote_addr varchar(128),
  summary text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_tenant_security_event_type
    check (event_type in (
      'tenant_missing',
      'internal_auth_failed',
      'rate_limited',
      'quota_exceeded',
      'cross_tenant_denied',
      'internal_auth_succeeded'
    )),
  constraint ck_tenant_security_event_severity
    check (severity in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_tenant_security_event_tenant
  on tenant_security_event(tenant_id, created_at desc);

create index if not exists idx_tenant_security_event_type
  on tenant_security_event(event_type, created_at desc);
```

---

# 7. Java 代码

## 7.1 `AiopsSecurityProperties.java`

路径：

```txt id="xpctk6"
modules/aiops-security/src/main/java/io/aegisops/security/AiopsSecurityProperties.java
```

```java id="7zw3mx"
package io.aegisops.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String internalAgentToken = "dev-internal-agent-token";
  private boolean internalAgentTokenRequired = true;
  private boolean tenantRequired = true;

  public String getInternalAgentToken() {
    return internalAgentToken;
  }

  public void setInternalAgentToken(String internalAgentToken) {
    this.internalAgentToken = internalAgentToken;
  }

  public boolean isInternalAgentTokenRequired() {
    return internalAgentTokenRequired;
  }

  public void setInternalAgentTokenRequired(boolean internalAgentTokenRequired) {
    this.internalAgentTokenRequired = internalAgentTokenRequired;
  }

  public boolean isTenantRequired() {
    return tenantRequired;
  }

  public void setTenantRequired(boolean tenantRequired) {
    this.tenantRequired = tenantRequired;
  }
}
```

---

## 7.2 `AiopsQuotaProperties.java`

路径：

```txt id="widqfm"
modules/aiops-security/src/main/java/io/aegisops/security/AiopsQuotaProperties.java
```

```java id="b3jsqh"
package io.aegisops.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.quota")
public class AiopsQuotaProperties {
  private int publicApiRequestsPerMinute = 600;
  private int internalAgentRequestsPerMinute = 1200;
  private boolean rateLimitEnabled = true;

  public int getPublicApiRequestsPerMinute() {
    return publicApiRequestsPerMinute;
  }

  public void setPublicApiRequestsPerMinute(int publicApiRequestsPerMinute) {
    this.publicApiRequestsPerMinute = publicApiRequestsPerMinute;
  }

  public int getInternalAgentRequestsPerMinute() {
    return internalAgentRequestsPerMinute;
  }

  public void setInternalAgentRequestsPerMinute(int internalAgentRequestsPerMinute) {
    this.internalAgentRequestsPerMinute = internalAgentRequestsPerMinute;
  }

  public boolean isRateLimitEnabled() {
    return rateLimitEnabled;
  }

  public void setRateLimitEnabled(boolean rateLimitEnabled) {
    this.rateLimitEnabled = rateLimitEnabled;
  }
}
```

---

## 7.3 `SecurityConstants.java`

路径：

```txt id="dzr9gb"
modules/aiops-security/src/main/java/io/aegisops/security/SecurityConstants.java
```

```java id="zvl0q7"
package io.aegisops.security;

public final class SecurityConstants {
  public static final String HEADER_TENANT_ID = "X-Tenant-Id";
  public static final String HEADER_INTERNAL_AGENT_TOKEN = "X-AIOPS-INTERNAL-TOKEN";

  private SecurityConstants() {}
}
```

---

## 7.4 `ConstantTimeTokenMatcher.java`

路径：

```txt id="jkbq2l"
modules/aiops-security/src/main/java/io/aegisops/security/ConstantTimeTokenMatcher.java
```

```java id="t3ix3v"
package io.aegisops.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ConstantTimeTokenMatcher {
  private ConstantTimeTokenMatcher() {}

  public static boolean matches(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }

    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);

    return MessageDigest.isEqual(expectedBytes, actualBytes);
  }
}
```

---

## 7.5 `SecurityErrorResponseWriter.java`

路径：

```txt id="obz973"
modules/aiops-security/src/main/java/io/aegisops/security/SecurityErrorResponseWriter.java
```

```java id="oyz1ii"
package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;

public class SecurityErrorResponseWriter {
  private final ObjectMapper objectMapper;

  public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(
      HttpServletResponse response,
      int status,
      String code,
      String message) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        Map.of(
            "success", false,
            "code", code,
            "message", message));
  }
}
```

---

# 8. Security Audit

## 8.1 DTO

路径：

```txt id="gln5zm"
modules/aiops-security/src/main/java/io/aegisops/security/TenantSecurityEventCreateCommand.java
```

```java id="c0il5f"
package io.aegisops.security;

public record TenantSecurityEventCreateCommand(
    String id,
    String tenantId,
    String eventType,
    String severity,
    String actor,
    String requestPath,
    String remoteAddr,
    String summary,
    String metadataJson) {}
```

---

## 8.2 Repository

路径：

```txt id="gq1lbf"
modules/aiops-security/src/main/java/io/aegisops/security/TenantSecurityEventRepository.java
```

```java id="ctbid4"
package io.aegisops.security;

public interface TenantSecurityEventRepository {
  void create(TenantSecurityEventCreateCommand command);
}
```

---

## 8.3 jOOQ 实现

路径：

```txt id="1q9y9u"
modules/aiops-security/src/main/java/io/aegisops/security/JooqTenantSecurityEventRepository.java
```

```java id="bb2m66"
package io.aegisops.security;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.TENANT_SECURITY_EVENT;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqTenantSecurityEventRepository implements TenantSecurityEventRepository {
  private final DSLContext dsl;

  public JooqTenantSecurityEventRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void create(TenantSecurityEventCreateCommand command) {
    dsl.insertInto(TENANT_SECURITY_EVENT)
        .set(TENANT_SECURITY_EVENT.ID, command.id())
        .set(TENANT_SECURITY_EVENT.TENANT_ID, command.tenantId())
        .set(TENANT_SECURITY_EVENT.EVENT_TYPE, command.eventType())
        .set(TENANT_SECURITY_EVENT.SEVERITY, command.severity())
        .set(TENANT_SECURITY_EVENT.ACTOR, command.actor())
        .set(TENANT_SECURITY_EVENT.REQUEST_PATH, command.requestPath())
        .set(TENANT_SECURITY_EVENT.REMOTE_ADDR, command.remoteAddr())
        .set(TENANT_SECURITY_EVENT.SUMMARY, command.summary())
        .set(TENANT_SECURITY_EVENT.METADATA, jsonbValue(command.metadataJson()))
        .set(TENANT_SECURITY_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }
}
```

---

## 8.4 Service

路径：

```txt id="36qlv3"
modules/aiops-security/src/main/java/io/aegisops/security/TenantSecurityAuditService.java
```

```java id="ec47xf"
package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantSecurityAuditService {
  private final TenantSecurityEventRepository repository;
  private final ObjectMapper objectMapper;

  public TenantSecurityAuditService(
      TenantSecurityEventRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void record(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request) {
    record(tenantId, eventType, severity, summary, request, Map.of());
  }

  public void record(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request,
      Map<String, Object> metadata) {
    try {
      repository.create(
          new TenantSecurityEventCreateCommand(
              newId("tse"),
              tenantId,
              eventType,
              severity,
              "system",
              request == null ? null : request.getRequestURI(),
              request == null ? null : remoteAddr(request),
              summary,
              objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata)));
    } catch (Exception ignored) {
      // Security audit failure must not break request handling.
    }
  }

  private String remoteAddr(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 9. Tenant Required Filter

## 9.1 `TenantRequiredFilter.java`

路径：

```txt id="y44gcw"
modules/aiops-security/src/main/java/io/aegisops/security/TenantRequiredFilter.java
```

```java id="651dwx"
package io.aegisops.security;

import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantRequiredFilter extends OncePerRequestFilter {
  private final AiopsSecurityProperties properties;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public TenantRequiredFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.properties = properties;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !properties.isTenantRequired()
        || path.equals("/health")
        || path.startsWith("/actuator")
        || path.startsWith("/swagger")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/error");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String path = request.getRequestURI();

    if (!path.startsWith("/api/") && !path.startsWith("/internal/agent/")) {
      filterChain.doFilter(request, response);
      return;
    }

    String tenantId = resolveTenantId(request);
    if (tenantId == null || tenantId.isBlank()) {
      auditService.record(
          null,
          "tenant_missing",
          "high",
          "Tenant id is required",
          request);
      responseWriter.write(
          response,
          HttpStatus.BAD_REQUEST.value(),
          "TENANT_REQUIRED",
          "Tenant id is required");
      return;
    }

    TenantContext.setTenantId(tenantId.trim());

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private String resolveTenantId(HttpServletRequest request) {
    String header = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    if (header != null && !header.isBlank()) {
      return header;
    }

    String parameter = request.getParameter("tenantId");
    if (parameter != null && !parameter.isBlank()) {
      return parameter;
    }

    return TenantContext.getTenantId();
  }
}
```

> 如果你当前 `TenantContext` 没有 `setTenantId/getTenantId/clear` 这些方法，而是叫 `setCurrentTenantId/currentTenantId`，把方法名替换成你现有实现即可。

---

# 10. Internal Agent Auth Filter

## 10.1 `InternalAgentAuthFilter.java`

路径：

```txt id="ol2e74"
modules/aiops-security/src/main/java/io/aegisops/security/InternalAgentAuthFilter.java
```

```java id="zgn9ad"
package io.aegisops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class InternalAgentAuthFilter extends OncePerRequestFilter {
  private final AiopsSecurityProperties properties;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public InternalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.properties = properties;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/agent/")
        || !properties.isInternalAgentTokenRequired();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    String actualToken = request.getHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN);
    String expectedToken = properties.getInternalAgentToken();

    if (!ConstantTimeTokenMatcher.matches(expectedToken, actualToken)) {
      auditService.record(
          tenantId,
          "internal_auth_failed",
          "critical",
          "Invalid internal agent token",
          request);
      responseWriter.write(
          response,
          HttpStatus.UNAUTHORIZED.value(),
          "INTERNAL_AGENT_AUTH_FAILED",
          "Invalid internal agent token");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
```

---

# 11. Rate Limit

## 11.1 `RateLimitBucket.java`

路径：

```txt id="8egd6i"
modules/aiops-security/src/main/java/io/aegisops/security/RateLimitBucket.java
```

```java id="ospilw"
package io.aegisops.security;

import java.time.Clock;

final class RateLimitBucket {
  private final int limit;
  private final Clock clock;
  private long windowStartMillis;
  private int count;

  RateLimitBucket(int limit, Clock clock) {
    this.limit = Math.max(1, limit);
    this.clock = clock;
    this.windowStartMillis = now();
    this.count = 0;
  }

  synchronized boolean tryAcquire() {
    long current = now();
    if (current - windowStartMillis >= 60_000L) {
      windowStartMillis = current;
      count = 0;
    }

    if (count >= limit) {
      return false;
    }

    count++;
    return true;
  }

  private long now() {
    return clock.millis();
  }
}
```

---

## 11.2 `InMemoryTenantRateLimiter.java`

路径：

```txt id="kljtzv"
modules/aiops-security/src/main/java/io/aegisops/security/InMemoryTenantRateLimiter.java
```

```java id="0cn4et"
package io.aegisops.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTenantRateLimiter {
  private final Clock clock;
  private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

  public InMemoryTenantRateLimiter() {
    this(Clock.systemUTC());
  }

  InMemoryTenantRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public boolean tryAcquire(String key, int limitPerMinute) {
    RateLimitBucket bucket =
        buckets.computeIfAbsent(key, ignored -> new RateLimitBucket(limitPerMinute, clock));
    return bucket.tryAcquire();
  }

  public int bucketCount() {
    return buckets.size();
  }
}
```

---

## 11.3 `TenantRateLimitFilter.java`

路径：

```txt id="w12c95"
modules/aiops-security/src/main/java/io/aegisops/security/TenantRateLimitFilter.java
```

```java id="ogpyn5"
package io.aegisops.security;

import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class TenantRateLimitFilter extends OncePerRequestFilter {
  private final AiopsQuotaProperties quotaProperties;
  private final InMemoryTenantRateLimiter rateLimiter;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public TenantRateLimitFilter(
      AiopsQuotaProperties quotaProperties,
      InMemoryTenantRateLimiter rateLimiter,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.quotaProperties = quotaProperties;
    this.rateLimiter = rateLimiter;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!quotaProperties.isRateLimitEnabled()) {
      return true;
    }

    String path = request.getRequestURI();
    return !(path.startsWith("/api/") || path.startsWith("/internal/agent/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    }

    if (tenantId == null || tenantId.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    String path = request.getRequestURI();
    boolean internal = path.startsWith("/internal/agent/");
    int limit =
        internal
            ? quotaProperties.getInternalAgentRequestsPerMinute()
            : quotaProperties.getPublicApiRequestsPerMinute();

    String bucketKey = tenantId + ":" + (internal ? "internal-agent" : "public-api");
    boolean allowed = rateLimiter.tryAcquire(bucketKey, limit);

    if (!allowed) {
      auditService.record(
          tenantId,
          "rate_limited",
          "high",
          "Tenant request rate limited",
          request);
      responseWriter.write(
          response,
          HttpStatus.TOO_MANY_REQUESTS.value(),
          "TENANT_RATE_LIMITED",
          "Tenant request rate limited");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
```

---

# 12. Spring 配置

## 12.1 `AiopsSecurityConfiguration.java`

路径：

```txt id="13b2ap"
modules/aiops-security/src/main/java/io/aegisops/security/AiopsSecurityConfiguration.java
```

```java id="vm96dm"
package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AiopsSecurityProperties.class,
    AiopsQuotaProperties.class
})
public class AiopsSecurityConfiguration {
  @Bean
  SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
    return new SecurityErrorResponseWriter(objectMapper);
  }

  @Bean
  TenantRequiredFilter tenantRequiredFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new TenantRequiredFilter(properties, responseWriter, auditService);
  }

  @Bean
  InternalAgentAuthFilter internalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new InternalAgentAuthFilter(properties, responseWriter, auditService);
  }

  @Bean
  TenantRateLimitFilter tenantRateLimitFilter(
      AiopsQuotaProperties quotaProperties,
      InMemoryTenantRateLimiter rateLimiter,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new TenantRateLimitFilter(
        quotaProperties,
        rateLimiter,
        responseWriter,
        auditService);
  }
}
```

---

# 13. Python Agent 修改

## 13.1 `settings.py`

路径：

```txt id="ua3zd7"
apps/aiops-agent/src/aiops_agent/settings.py
```

追加配置：

```python id="gqequ7"
internal_agent_token: str = "dev-internal-agent-token"
```

如果当前是 Pydantic Settings，完整示例：

```python id="mb1q2s"
from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", extra="ignore")

    provider: str = "deterministic"
    model: str = "deterministic-v1"
    agent_name: str = "aegisops-agent"
    contract_version: str = "agent-diagnosis.v1"

    evidence_api_base_url: str = "http://localhost:8080"
    knowledge_api_base_url: str = "http://localhost:8080"
    checkpoint_api_base_url: str = "http://localhost:8080"
    memory_api_base_url: str = "http://localhost:8080"

    internal_agent_token: str = "dev-internal-agent-token"

    request_timeout_seconds: float = 5.0
    workflow_graph_version: str = "phase8.0-saas-tenant-hardening"

    workflow_case_retrieval_enabled: bool = False
    workflow_human_checkpoint_enabled: bool = False
    workflow_multi_agent_enabled: bool = False
    workflow_memory_enabled: bool = False
    workflow_memory_write_enabled: bool = False

    max_evidence_items: int = 8
    max_similar_cases: int = 5
    max_memories: int = 5
    checkpoint_ttl_seconds: int = 86400
    max_agent_messages: int = 20

    def normalized_generation_mode(self) -> str:
        return "deterministic" if self.provider == "deterministic" else "llm"


settings = Settings()
```

如果你现有 `Settings` 已经有字段，别全替换，追加 `internal_agent_token` 即可。

---

## 13.2 新增 `workflow/tools/internal_auth.py`

路径：

```txt id="tml6f2"
apps/aiops-agent/src/aiops_agent/workflow/tools/internal_auth.py
```

```python id="auamj6"
"""Shared headers for internal Java tool calls."""

from __future__ import annotations

from aiops_agent.settings import Settings, settings


HEADER_TENANT_ID = "X-Tenant-Id"
HEADER_INTERNAL_AGENT_TOKEN = "X-AIOPS-INTERNAL-TOKEN"


def internal_tool_headers(
    tenant_id: str,
    current_settings: Settings | None = None,
) -> dict[str, str]:
    cfg = current_settings or settings

    if not tenant_id or not tenant_id.strip():
        raise ValueError("tenant_id is required for internal tool call")

    return {
        HEADER_TENANT_ID: tenant_id.strip(),
        HEADER_INTERNAL_AGENT_TOKEN: cfg.internal_agent_token,
    }
```

---

## 13.3 修改所有 Python Tool Client

下面只给关键替换方式。你的现有文件路径应是：

```txt id="l8yykk"
apps/aiops-agent/src/aiops_agent/workflow/tools/evidence_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/knowledge_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/checkpoint_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/memory_client.py
```

在每个文件顶部加入：

```python id="oq3wni"
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers
```

然后把所有：

```python id="5i6b3c"
headers={"X-Tenant-Id": tenant_id}
```

替换为：

```python id="o0m4la"
headers=internal_tool_headers(tenant_id)
```

### 示例：`memory_client.py` 关键片段

```python id="c69yl6"
async with httpx.AsyncClient(timeout=self.timeout) as client:
    response = await client.post(
        url,
        json=body,
        headers=internal_tool_headers(tenant_id),
    )
    response.raise_for_status()
    payload = response.json()
```

### 示例：`checkpoint_client.py` 关键片段

```python id="2m1yup"
async with httpx.AsyncClient(timeout=self.timeout) as client:
    response = await client.post(
        url,
        json=body,
        headers=internal_tool_headers(tenant_id),
    )
    response.raise_for_status()
    payload = response.json()
```

### 示例：`evidence_client.py`

```python id="o6m7wc"
response = await client.get(
    url,
    headers=internal_tool_headers(tenant_id),
)
```

---

# 14. Java 单元测试

## 14.1 `ConstantTimeTokenMatcherTest.java`

路径：

```txt id="6mly9m"
modules/aiops-security/src/test/java/io/aegisops/security/ConstantTimeTokenMatcherTest.java
```

```java id="zv0xtk"
package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConstantTimeTokenMatcherTest {
  @Test
  void matchesSameToken() {
    assertTrue(ConstantTimeTokenMatcher.matches("abc", "abc"));
  }

  @Test
  void rejectsDifferentToken() {
    assertFalse(ConstantTimeTokenMatcher.matches("abc", "def"));
  }

  @Test
  void rejectsNull() {
    assertFalse(ConstantTimeTokenMatcher.matches("abc", null));
    assertFalse(ConstantTimeTokenMatcher.matches(null, "abc"));
  }
}
```

---

## 14.2 `InMemoryTenantRateLimiterTest.java`

路径：

```txt id="mbfzes"
modules/aiops-security/src/test/java/io/aegisops/security/InMemoryTenantRateLimiterTest.java
```

```java id="kfpwxb"
package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryTenantRateLimiterTest {
  @Test
  void allowsWithinLimitAndRejectsAfterLimit() {
    InMemoryTenantRateLimiter limiter =
        new InMemoryTenantRateLimiter(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertTrue(limiter.tryAcquire("tenant_1:api", 2));
    assertTrue(limiter.tryAcquire("tenant_1:api", 2));
    assertFalse(limiter.tryAcquire("tenant_1:api", 2));
  }

  @Test
  void separatesTenantBuckets() {
    InMemoryTenantRateLimiter limiter =
        new InMemoryTenantRateLimiter(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertTrue(limiter.tryAcquire("tenant_1:api", 1));
    assertFalse(limiter.tryAcquire("tenant_1:api", 1));

    assertTrue(limiter.tryAcquire("tenant_2:api", 1));
  }
}
```

---

## 14.3 `InternalAgentAuthFilterTest.java`

路径：

```txt id="7t89xi"
modules/aiops-security/src/test/java/io/aegisops/security/InternalAgentAuthFilterTest.java
```

```java id="48rq4r"
package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAgentAuthFilterTest {
  @Test
  void rejectsMissingTokenForInternalAgentApi() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");

    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            audit);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FAILED"));
    assertEquals(1, audit.events.size());
  }

  @Test
  void allowsValidTokenForInternalAgentApi() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");

    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            new FakeAuditService());

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    request.addHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN, "secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
  }

  private static class NoopChain implements FilterChain {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {}
  }

  private static class RecordingChain implements FilterChain {
    boolean called;

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      called = true;
    }
  }

  private static class FakeAuditService extends TenantSecurityAuditService {
    final List<String> events = new ArrayList<>();

    FakeAuditService() {
      super(command -> {}, new ObjectMapper());
    }

    @Override
    public void record(
        String tenantId,
        String eventType,
        String severity,
        String summary,
        jakarta.servlet.http.HttpServletRequest request) {
      events.add(eventType);
    }
  }
}
```

---

## 14.4 `TenantRequiredFilterTest.java`

路径：

```txt id="cj0cwl"
modules/aiops-security/src/test/java/io/aegisops/security/TenantRequiredFilterTest.java
```

```java id="puxz4e"
package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantRequiredFilterTest {
  @Test
  void rejectsApiRequestWithoutTenant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            new TenantSecurityAuditService(command -> {}, new ObjectMapper()));

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains("TENANT_REQUIRED"));
  }

  @Test
  void allowsApiRequestWithTenantHeader() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            new TenantSecurityAuditService(command -> {}, new ObjectMapper()));

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    var response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
  }

  private static class RecordingChain implements FilterChain {
    boolean called;

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      called = true;
    }
  }
}
```

---

## 14.5 `JooqTenantSecurityEventRepositoryGeneratedSqlTest.java`

路径：

```txt id="gxc3tf"
modules/aiops-security/src/test/java/io/aegisops/security/JooqTenantSecurityEventRepositoryGeneratedSqlTest.java
```

```java id="4y1hyx"
package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.persistence.JooqTestSupport;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqTenantSecurityEventRepositoryGeneratedSqlTest {
  @Test
  void createUsesTenantSecurityEventTable() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqTenantSecurityEventRepository(dsl);

    repository.create(
        new TenantSecurityEventCreateCommand(
            "tse_1",
            "tenant_1",
            "internal_auth_failed",
            "critical",
            "system",
            "/internal/agent/memories",
            "127.0.0.1",
            "Invalid token",
            "{}"));

    String sql =
        dsl.queries().stream()
            .map(Query::getSQL)
            .reduce("", (a, b) -> a + "\n" + b)
            .toLowerCase();

    assertTrue(sql.contains("tenant_security_event"));
    assertTrue(sql.contains("internal_auth_failed"));
  }
}
```

---

# 15. Python 单元测试

## 15.1 `test_internal_auth.py`

路径：

```txt id="07o4sn"
apps/aiops-agent/tests/test_internal_auth.py
```

```python id="p2en5n"
from __future__ import annotations

import pytest

from aiops_agent.settings import Settings
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_INTERNAL_AGENT_TOKEN,
    HEADER_TENANT_ID,
    internal_tool_headers,
)


def test_internal_tool_headers_contains_tenant_and_token():
    settings = Settings(internal_agent_token="secret-token")

    headers = internal_tool_headers("tenant_1", settings)

    assert headers[HEADER_TENANT_ID] == "tenant_1"
    assert headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"


def test_internal_tool_headers_strips_tenant():
    settings = Settings(internal_agent_token="secret-token")

    headers = internal_tool_headers(" tenant_1 ", settings)

    assert headers[HEADER_TENANT_ID] == "tenant_1"


def test_internal_tool_headers_rejects_blank_tenant():
    settings = Settings(internal_agent_token="secret-token")

    with pytest.raises(ValueError):
        internal_tool_headers(" ", settings)
```

---

## 15.2 `test_memory_client_internal_headers.py`

路径：

```txt id="fx05z3"
apps/aiops-agent/tests/test_memory_client_internal_headers.py
```

```python id="vhuj4j"
from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.internal_auth import HEADER_INTERNAL_AGENT_TOKEN, HEADER_TENANT_ID
from aiops_agent.workflow.tools.memory_client import MemoryClient


@pytest.mark.asyncio
@respx.mock
async def test_memory_client_sends_internal_auth_headers(monkeypatch):
    monkeypatch.setattr(settings, "internal_agent_token", "secret-token")

    route = respx.post("http://java/internal/agent/memories/search").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "query": "redis timeout",
                    "topK": 5,
                    "results": [],
                }
            },
        )
    )

    client = MemoryClient(base_url="http://java")

    await client.search_memories(
        tenant_id="tenant_1",
        query="redis timeout",
        tags=["redis"],
    )

    assert route.called
    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_1"
    assert route.calls[0].request.headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"
```

---

## 15.3 `test_checkpoint_client_internal_headers.py`

路径：

```txt id="m3u1rh"
apps/aiops-agent/tests/test_checkpoint_client_internal_headers.py
```

```python id="x63x35"
from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.checkpoint_client import CheckpointClient
from aiops_agent.workflow.tools.internal_auth import HEADER_INTERNAL_AGENT_TOKEN, HEADER_TENANT_ID


@pytest.mark.asyncio
@respx.mock
async def test_checkpoint_client_sends_internal_auth_headers(monkeypatch):
    monkeypatch.setattr(settings, "internal_agent_token", "secret-token")

    route = respx.post("http://java/internal/agent/checkpoints").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "id": "agcp_1",
                    "status": "pending",
                    "resumeToken": "agrt_1",
                    "stateSnapshotJson": "{}",
                }
            },
        )
    )

    client = CheckpointClient(base_url="http://java")

    await client.create_checkpoint(
        tenant_id="tenant_1",
        incident_id="inc_1",
        title="Review RCA",
        reason="Need review",
        review_prompt="Approve?",
        root_cause="redis timeout",
        confidence=0.8,
        risk_level="high",
        state_snapshot={},
    )

    assert route.called
    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_1"
    assert route.calls[0].request.headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"
```

---

# 16. 文档

路径：

```txt id="75jrmp"
docs/mvp/design/phase8.0-saas-multi-tenant-hardening.md
```

````md id="6yl6gw"
# Phase8.0 SaaS Multi-tenant Hardening

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
````

Python:

```env
AIOPS_AGENT_INTERNAL_AGENT_TOKEN=change-me
```

## 验收标准

1. /api/\*\* 无 tenant 被拒绝。
2. /internal/agent/\*\* 无 token 被拒绝。
3. /internal/agent/\*\* token 错误被拒绝。
4. /internal/agent/\*\* token 正确可继续。
5. Python Agent tool calls 带 X-Tenant-Id。
6. Python Agent tool calls 带 X-AIOPS-INTERNAL-TOKEN。
7. tenant rate limit 生效。
8. security event 可记录。

````

---

# 17. 验证命令

## Java

```powershell id="1imcym"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-security -am test
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-server -am test
````

全量：

```powershell id="byfq7h"
mvn test
```

---

## Python

```bash id="jcc5dq"
cd apps/aiops-agent
pip install -e ".[test]"
pytest -q
ruff check .
```

---

# 18. 验收标准

```txt id="78akdz"
1. tenant_quota_policy 表存在。
2. tenant_security_event 表存在。
3. /api/** 缺少 tenant 被拒绝。
4. /internal/agent/** 缺少 tenant 被拒绝。
5. /internal/agent/** 缺少 token 被拒绝。
6. /internal/agent/** token 错误被拒绝。
7. /internal/agent/** token 正确可继续。
8. public API tenant 级限流生效。
9. internal agent API tenant 级限流生效。
10. internal auth failed 写 security event。
11. tenant missing 写 security event。
12. rate limited 写 security event。
13. Python EvidenceClient 带 internal token。
14. Python KnowledgeClient 带 internal token。
15. Python CheckpointClient 带 internal token。
16. Python MemoryClient 带 internal token。
17. 不新增 execution。
18. 不调 runner。
19. 不新增 webhook / ansible / ssh 执行能力。
```

---

# 19. 建议提交信息

```txt id="pp3ztq"
feat(security): add saas tenant hardening baseline
```

---

# 20. 下一步 Phase8.1

Phase8.0 完成后，建议进入：

```txt id="osox1b"
Phase8.1 Plugin System
```

Phase8.1 做：

```txt id="rln1dh"
1. Plugin descriptor
2. Extension point registry
3. Backend plugin loading
4. Frontend plugin manifest
5. Tool plugin allowlist
6. Tenant-level plugin enablement
```

不要把 Plugin 混进 Phase8.0。

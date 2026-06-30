---
title: Phase5.4：Webhook Adapter
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase5.4：Webhook Adapter

> 前提：Phase5.3 已完成并修复 `retry` 只能重试最新 failed/timeout execution 的边界问题。
> Phase5.4 目标：在 runner 中接入第一类真实执行 Adapter：**Webhook Adapter**。
> 原则：默认 dry-run；live webhook 必须同时满足 `execution.live-enabled=true`、connector/policy 启用、host allowlist、私网/localhost/metadata 拦截、method 校验。

---

# 1. Phase5.4 定位

Phase5.3 已完成：

```txt id="7swsrz"
execution_run / execution_step
runner claim
lease / heartbeat
timeout sweep
retry
execution_artifact
manual executor
shell dry-run executor
unsupported executor
```

Phase5.4 新增：

```txt id="z84f8z"
webhook_connector
webhook_execution_policy
WebhookStepExecutor
WebhookSecurityValidator
WebhookHttpClient
请求/响应 artifact
host allowlist
private ip / localhost / metadata ip 拦截
sensitive header mask
```

---

# 2. 安全边界

```txt id="b95hma"
默认：
  dry-run only

live webhook 必须满足：
  1. execution_run.mode = live
  2. aiops.execution.live-enabled = true
  3. webhook_connector.enabled = true
  4. webhook_execution_policy.allow_live = true
  5. method 在 allowed_methods 中
  6. URL scheme 是 http/https
  7. host 在 allowed_hosts 中
  8. host 不是 localhost
  9. host 不是 127.0.0.0/8
  10. host 不是 169.254.169.254
  11. host 不是 private IP
  12. body size <= max_body_bytes
```

Phase5.4 仍然禁止：

```txt id="eb0tua"
ProcessBuilder
Runtime.exec
SSH
Ansible
自由 shell
```

---

# 3. API 设计

## 3.1 Webhook Connector 管理 API

```txt id="1bgtgr"
GET  /api/webhook-connectors
POST /api/webhook-connectors
GET  /api/webhook-connectors/{connectorId}
POST /api/webhook-connectors/{connectorId}/enable
POST /api/webhook-connectors/{connectorId}/disable
```

## 3.2 Runner 执行

Runbook step 的 `actionType`：

```txt id="hi1bg1"
webhook
```

`automation_plan_step.action_payload` 示例：

```json id="2s5fu8"
{
  "connectorId": "whc_restart_service",
  "method": "POST",
  "path": "/internal/restart-service",
  "headers": {
    "X-Source": "aegisops"
  },
  "body": {
    "serviceName": "order-service",
    "incidentId": "{{incidentId}}"
  }
}
```

Phase5.4 不做复杂模板渲染，`body` 直接作为 JSON 字符串发送。模板渲染可以放 Phase5.5/5.6 做。

---

# 4. 数据库设计

## 4.1 新增表

```txt id="5vk0jj"
webhook_connector
webhook_execution_policy
```

## 4.2 为什么不新增 webhook_execution_log

因为 Phase5.3 已经有：

```txt id="0mqqmr"
execution_artifact
```

Webhook 请求摘要、响应摘要、错误信息都写到 artifact 即可，避免执行日志分裂。

---

# 5. Migration

路径：

```txt id="ee5lzx"
apps/aiops-server/src/main/resources/db/migration/V14__phase5_4_webhook_adapter.sql
```

```sql id="n3mx1u"
-- Phase 5.4: Webhook Adapter.
-- Webhook execution is performed only by aiops-runner.
-- Live webhook is disabled by default and guarded by policy.

create table if not exists webhook_connector (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(160) not null,
  description text,
  base_url varchar(512) not null,
  default_method varchar(16) not null default 'POST',
  default_headers jsonb not null default '{}'::jsonb,
  sensitive_headers jsonb not null default '["authorization", "x-api-key", "x-token", "cookie"]'::jsonb,
  enabled boolean not null default true,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_webhook_connector_method
    check (default_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

create index if not exists idx_webhook_connector_tenant_enabled
  on webhook_connector(tenant_id, enabled);

create unique index if not exists uq_webhook_connector_tenant_name
  on webhook_connector(tenant_id, name);

create table if not exists webhook_execution_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  connector_id varchar(64) not null references webhook_connector(id) on delete cascade,
  allow_live boolean not null default false,
  allowed_hosts jsonb not null default '[]'::jsonb,
  allowed_methods jsonb not null default '["POST"]'::jsonb,
  block_private_ip boolean not null default true,
  block_localhost boolean not null default true,
  block_metadata_ip boolean not null default true,
  max_body_bytes int not null default 32768,
  timeout_millis int not null default 5000,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_webhook_policy_max_body_bytes
    check (max_body_bytes >= 0 and max_body_bytes <= 1048576),
  constraint ck_webhook_policy_timeout_millis
    check (timeout_millis >= 100 and timeout_millis <= 60000)
);

create unique index if not exists uq_webhook_policy_connector
  on webhook_execution_policy(connector_id);

create index if not exists idx_webhook_policy_tenant_enabled
  on webhook_execution_policy(tenant_id, enabled);
```

---

# 6. jOOQ Codegen

路径：

```txt id="3jl3z1"
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

`<includes>` 增加：

```txt id="wyq1zh"
webhook_connector | webhook_execution_policy
```

建议完整 includes：

```xml id="mdrih9"
<includes>
  tenant | sys_user | sys_role | sys_permission | sys_user_role | sys_role_permission |
  datasource | datasource_sync_run | asset | asset_relation | alert_event | incident |
  incident_event | incident_timeline | audit_log | rca_analysis | ai_diagnosis |
  agent_run | agent_run_step | agent_eval_result | log_event | change_event |
  runbook | runbook_step_template | automation_plan | automation_plan_step |
  approval_policy | automation_approval | approval_decision |
  execution_run | execution_step | execution_artifact |
  webhook_connector | webhook_execution_policy
</includes>
```

---

# 7. DTO 完整代码

## 7.1 `WebhookConnectorCreateRequest.java`

路径：

```txt id="g0kca7"
modules/aiops-execution/src/main/java/io/aegisops/execution/dto/WebhookConnectorCreateRequest.java
```

```java id="4f44n9"
package io.aegisops.execution.dto;

import java.util.List;
import java.util.Map;

public record WebhookConnectorCreateRequest(
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    Map<String, String> defaultHeaders,
    List<String> sensitiveHeaders,
    List<String> allowedHosts,
    List<String> allowedMethods,
    Boolean allowLive,
    Integer maxBodyBytes,
    Integer timeoutMillis,
    String createdBy) {}
```

---

## 7.2 `WebhookConnectorCreateCommand.java`

```java id="ps18nw"
package io.aegisops.execution.dto;

public record WebhookConnectorCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    String defaultHeadersJson,
    String sensitiveHeadersJson,
    boolean enabled,
    String createdBy) {}
```

---

## 7.3 `WebhookPolicyCreateCommand.java`

```java id="31zbwh"
package io.aegisops.execution.dto;

public record WebhookPolicyCreateCommand(
    String id,
    String tenantId,
    String connectorId,
    boolean allowLive,
    String allowedHostsJson,
    String allowedMethodsJson,
    boolean blockPrivateIp,
    boolean blockLocalhost,
    boolean blockMetadataIp,
    int maxBodyBytes,
    int timeoutMillis,
    boolean enabled) {}
```

---

## 7.4 `WebhookConnectorRecord.java`

```java id="s2rcbr"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record WebhookConnectorRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    String defaultHeadersJson,
    String sensitiveHeadersJson,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.5 `WebhookPolicyRecord.java`

```java id="9m7222"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record WebhookPolicyRecord(
    String id,
    String tenantId,
    String connectorId,
    boolean allowLive,
    String allowedHostsJson,
    String allowedMethodsJson,
    boolean blockPrivateIp,
    boolean blockLocalhost,
    boolean blockMetadataIp,
    int maxBodyBytes,
    int timeoutMillis,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 7.6 `WebhookConnectorResponse.java`

```java id="5wpiue"
package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WebhookConnectorResponse(
    String id,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    Map<String, String> defaultHeaders,
    List<String> sensitiveHeaders,
    boolean enabled,
    boolean allowLive,
    List<String> allowedHosts,
    List<String> allowedMethods,
    boolean blockPrivateIp,
    boolean blockLocalhost,
    boolean blockMetadataIp,
    int maxBodyBytes,
    int timeoutMillis,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

# 8. WebhookRepository

路径：

```txt id="7ei81e"
modules/aiops-execution/src/main/java/io/aegisops/execution/WebhookRepository.java
```

```java id="chsoyz"
package io.aegisops.execution;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.util.List;
import java.util.Optional;

public interface WebhookRepository {
  void createConnector(WebhookConnectorCreateCommand command);

  void createPolicy(WebhookPolicyCreateCommand command);

  List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled);

  Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId);

  Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId);

  boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled);
}
```

---

# 9. JooqWebhookRepository

路径：

```txt id="rklljh"
modules/aiops-execution/src/main/java/io/aegisops/execution/JooqWebhookRepository.java
```

```java id="4nmg6v"
package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.WEBHOOK_CONNECTOR;
import static io.aegisops.persistence.jooq.Tables.WEBHOOK_EXECUTION_POLICY;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqWebhookRepository implements WebhookRepository {
  private final DSLContext dsl;

  public JooqWebhookRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createConnector(WebhookConnectorCreateCommand command) {
    dsl.insertInto(WEBHOOK_CONNECTOR)
        .set(WEBHOOK_CONNECTOR.ID, command.id())
        .set(WEBHOOK_CONNECTOR.TENANT_ID, command.tenantId())
        .set(WEBHOOK_CONNECTOR.NAME, command.name())
        .set(WEBHOOK_CONNECTOR.DESCRIPTION, command.description())
        .set(WEBHOOK_CONNECTOR.BASE_URL, command.baseUrl())
        .set(WEBHOOK_CONNECTOR.DEFAULT_METHOD, command.defaultMethod())
        .set(WEBHOOK_CONNECTOR.DEFAULT_HEADERS, jsonbValue(command.defaultHeadersJson()))
        .set(WEBHOOK_CONNECTOR.SENSITIVE_HEADERS, jsonbValue(command.sensitiveHeadersJson()))
        .set(WEBHOOK_CONNECTOR.ENABLED, command.enabled())
        .set(WEBHOOK_CONNECTOR.CREATED_BY, command.createdBy())
        .set(WEBHOOK_CONNECTOR.CREATED_AT, DSL.currentOffsetDateTime())
        .set(WEBHOOK_CONNECTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createPolicy(WebhookPolicyCreateCommand command) {
    dsl.insertInto(WEBHOOK_EXECUTION_POLICY)
        .set(WEBHOOK_EXECUTION_POLICY.ID, command.id())
        .set(WEBHOOK_EXECUTION_POLICY.TENANT_ID, command.tenantId())
        .set(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID, command.connectorId())
        .set(WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE, command.allowLive())
        .set(WEBHOOK_EXECUTION_POLICY.ALLOWED_HOSTS, jsonbValue(command.allowedHostsJson()))
        .set(WEBHOOK_EXECUTION_POLICY.ALLOWED_METHODS, jsonbValue(command.allowedMethodsJson()))
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP, command.blockPrivateIp())
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST, command.blockLocalhost())
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP, command.blockMetadataIp())
        .set(WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES, command.maxBodyBytes())
        .set(WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS, command.timeoutMillis())
        .set(WEBHOOK_EXECUTION_POLICY.ENABLED, command.enabled())
        .set(WEBHOOK_EXECUTION_POLICY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(WEBHOOK_EXECUTION_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
    var condition = WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId);
    if (!includeDisabled) {
      condition = condition.and(WEBHOOK_CONNECTOR.ENABLED.isTrue());
    }

    return dsl.select(
            WEBHOOK_CONNECTOR.ID,
            WEBHOOK_CONNECTOR.TENANT_ID,
            WEBHOOK_CONNECTOR.NAME,
            WEBHOOK_CONNECTOR.DESCRIPTION,
            WEBHOOK_CONNECTOR.BASE_URL,
            WEBHOOK_CONNECTOR.DEFAULT_METHOD,
            WEBHOOK_CONNECTOR.DEFAULT_HEADERS.cast(String.class).as("default_headers_json"),
            WEBHOOK_CONNECTOR.SENSITIVE_HEADERS.cast(String.class).as("sensitive_headers_json"),
            WEBHOOK_CONNECTOR.ENABLED,
            WEBHOOK_CONNECTOR.CREATED_BY,
            WEBHOOK_CONNECTOR.CREATED_AT,
            WEBHOOK_CONNECTOR.UPDATED_AT)
        .from(WEBHOOK_CONNECTOR)
        .where(condition)
        .orderBy(WEBHOOK_CONNECTOR.CREATED_AT.desc())
        .fetch(this::toConnectorRecord);
  }

  @Override
  public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
    return dsl.select(
            WEBHOOK_CONNECTOR.ID,
            WEBHOOK_CONNECTOR.TENANT_ID,
            WEBHOOK_CONNECTOR.NAME,
            WEBHOOK_CONNECTOR.DESCRIPTION,
            WEBHOOK_CONNECTOR.BASE_URL,
            WEBHOOK_CONNECTOR.DEFAULT_METHOD,
            WEBHOOK_CONNECTOR.DEFAULT_HEADERS.cast(String.class).as("default_headers_json"),
            WEBHOOK_CONNECTOR.SENSITIVE_HEADERS.cast(String.class).as("sensitive_headers_json"),
            WEBHOOK_CONNECTOR.ENABLED,
            WEBHOOK_CONNECTOR.CREATED_BY,
            WEBHOOK_CONNECTOR.CREATED_AT,
            WEBHOOK_CONNECTOR.UPDATED_AT)
        .from(WEBHOOK_CONNECTOR)
        .where(WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId))
        .and(WEBHOOK_CONNECTOR.ID.eq(connectorId))
        .fetchOptional(this::toConnectorRecord);
  }

  @Override
  public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
    return dsl.select(
            WEBHOOK_EXECUTION_POLICY.ID,
            WEBHOOK_EXECUTION_POLICY.TENANT_ID,
            WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID,
            WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE,
            WEBHOOK_EXECUTION_POLICY.ALLOWED_HOSTS.cast(String.class).as("allowed_hosts_json"),
            WEBHOOK_EXECUTION_POLICY.ALLOWED_METHODS.cast(String.class).as("allowed_methods_json"),
            WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP,
            WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST,
            WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP,
            WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES,
            WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS,
            WEBHOOK_EXECUTION_POLICY.ENABLED,
            WEBHOOK_EXECUTION_POLICY.CREATED_AT,
            WEBHOOK_EXECUTION_POLICY.UPDATED_AT)
        .from(WEBHOOK_EXECUTION_POLICY)
        .where(WEBHOOK_EXECUTION_POLICY.TENANT_ID.eq(tenantId))
        .and(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID.eq(connectorId))
        .fetchOptional(this::toPolicyRecord);
  }

  @Override
  public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
    return dsl.update(WEBHOOK_CONNECTOR)
            .set(WEBHOOK_CONNECTOR.ENABLED, enabled)
            .set(WEBHOOK_CONNECTOR.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId))
            .and(WEBHOOK_CONNECTOR.ID.eq(connectorId))
            .execute()
        > 0;
  }

  private WebhookConnectorRecord toConnectorRecord(org.jooq.Record record) {
    return new WebhookConnectorRecord(
        record.get(WEBHOOK_CONNECTOR.ID),
        record.get(WEBHOOK_CONNECTOR.TENANT_ID),
        record.get(WEBHOOK_CONNECTOR.NAME),
        record.get(WEBHOOK_CONNECTOR.DESCRIPTION),
        record.get(WEBHOOK_CONNECTOR.BASE_URL),
        record.get(WEBHOOK_CONNECTOR.DEFAULT_METHOD),
        record.get("default_headers_json", String.class),
        record.get("sensitive_headers_json", String.class),
        Boolean.TRUE.equals(record.get(WEBHOOK_CONNECTOR.ENABLED)),
        record.get(WEBHOOK_CONNECTOR.CREATED_BY),
        record.get(WEBHOOK_CONNECTOR.CREATED_AT),
        record.get(WEBHOOK_CONNECTOR.UPDATED_AT));
  }

  private WebhookPolicyRecord toPolicyRecord(org.jooq.Record record) {
    return new WebhookPolicyRecord(
        record.get(WEBHOOK_EXECUTION_POLICY.ID),
        record.get(WEBHOOK_EXECUTION_POLICY.TENANT_ID),
        record.get(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE)),
        record.get("allowed_hosts_json", String.class),
        record.get("allowed_methods_json", String.class),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP)),
        value(record.get(WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES)),
        value(record.get(WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.ENABLED)),
        record.get(WEBHOOK_EXECUTION_POLICY.CREATED_AT),
        record.get(WEBHOOK_EXECUTION_POLICY.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 10. WebhookJson

路径：

```txt id="6zab0p"
modules/aiops-execution/src/main/java/io/aegisops/execution/WebhookJson.java
```

```java id="t3fiu2"
package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

public class WebhookJson {
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public WebhookJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_JSON_WRITE_FAILED", "Failed to serialize webhook json");
    }
  }

  public Map<String, String> readStringMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, STRING_MAP);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  public List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, STRING_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }
}
```

---

# 11. WebhookConnectorService

路径：

```txt id="mknpb5"
modules/aiops-execution/src/main/java/io/aegisops/execution/WebhookConnectorService.java
```

```java id="q52au6"
package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookConnectorResponse;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookConnectorService {
  private final WebhookRepository repository;
  private final WebhookJson json;

  public WebhookConnectorService(WebhookRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.json = new WebhookJson(objectMapper);
  }

  public List<WebhookConnectorResponse> list(String tenantId, boolean includeDisabled) {
    return repository.listConnectors(tenantId, includeDisabled).stream()
        .map(record -> toResponse(record, repository.findPolicy(tenantId, record.id()).orElse(null)))
        .toList();
  }

  public WebhookConnectorResponse get(String tenantId, String connectorId) {
    WebhookConnectorRecord connector =
        repository
            .findConnector(tenantId, connectorId)
            .orElseThrow(() -> new AppException("WEBHOOK_CONNECTOR_NOT_FOUND", "Webhook connector not found"));

    return toResponse(connector, repository.findPolicy(tenantId, connector.id()).orElse(null));
  }

  @Transactional
  public WebhookConnectorResponse create(String tenantId, WebhookConnectorCreateRequest request) {
    validateCreateRequest(request);

    String connectorId = newId("whc");
    String policyId = newId("whp");

    String method = normalizeMethod(request.defaultMethod(), "POST");
    List<String> allowedMethods =
        normalizeMethods(request.allowedMethods() == null ? List.of(method) : request.allowedMethods());

    String host = hostOf(request.baseUrl());
    List<String> allowedHosts =
        request.allowedHosts() == null || request.allowedHosts().isEmpty()
            ? List.of(host)
            : request.allowedHosts();

    repository.createConnector(
        new WebhookConnectorCreateCommand(
            connectorId,
            tenantId,
            request.name().trim(),
            request.description(),
            request.baseUrl().trim(),
            method,
            json.write(request.defaultHeaders() == null ? Map.of() : request.defaultHeaders()),
            json.write(
                request.sensitiveHeaders() == null
                    ? List.of("authorization", "x-api-key", "x-token", "cookie")
                    : request.sensitiveHeaders()),
            true,
            blankToDefault(request.createdBy(), "system")));

    repository.createPolicy(
        new WebhookPolicyCreateCommand(
            policyId,
            tenantId,
            connectorId,
            Boolean.TRUE.equals(request.allowLive()),
            json.write(allowedHosts),
            json.write(allowedMethods),
            true,
            true,
            true,
            normalizeMaxBodyBytes(request.maxBodyBytes()),
            normalizeTimeoutMillis(request.timeoutMillis()),
            true));

    return get(tenantId, connectorId);
  }

  @Transactional
  public WebhookConnectorResponse setEnabled(String tenantId, String connectorId, boolean enabled) {
    boolean updated = repository.setConnectorEnabled(tenantId, connectorId, enabled);
    if (!updated) {
      throw new AppException("WEBHOOK_CONNECTOR_UPDATE_FAILED", "Webhook connector was not updated");
    }
    return get(tenantId, connectorId);
  }

  private void validateCreateRequest(WebhookConnectorCreateRequest request) {
    if (request == null) {
      throw new AppException("WEBHOOK_CONNECTOR_REQUEST_REQUIRED", "Webhook connector request is required");
    }

    if (request.name() == null || request.name().isBlank()) {
      throw new AppException("WEBHOOK_CONNECTOR_NAME_REQUIRED", "Webhook connector name is required");
    }

    if (request.baseUrl() == null || request.baseUrl().isBlank()) {
      throw new AppException("WEBHOOK_CONNECTOR_BASE_URL_REQUIRED", "Webhook connector baseUrl is required");
    }

    URI uri = URI.create(request.baseUrl().trim());
    if (!List.of("http", "https").contains(uri.getScheme())) {
      throw new AppException("WEBHOOK_CONNECTOR_SCHEME_INVALID", "Webhook connector scheme must be http or https");
    }

    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new AppException("WEBHOOK_CONNECTOR_HOST_REQUIRED", "Webhook connector host is required");
    }
  }

  private WebhookConnectorResponse toResponse(WebhookConnectorRecord connector, WebhookPolicyRecord policy) {
    return new WebhookConnectorResponse(
        connector.id(),
        connector.name(),
        connector.description(),
        connector.baseUrl(),
        connector.defaultMethod(),
        json.readStringMap(connector.defaultHeadersJson()),
        json.readStringList(connector.sensitiveHeadersJson()),
        connector.enabled(),
        policy != null && policy.allowLive(),
        policy == null ? List.of() : json.readStringList(policy.allowedHostsJson()),
        policy == null ? List.of(connector.defaultMethod()) : json.readStringList(policy.allowedMethodsJson()),
        policy == null || policy.blockPrivateIp(),
        policy == null || policy.blockLocalhost(),
        policy == null || policy.blockMetadataIp(),
        policy == null ? 32768 : policy.maxBodyBytes(),
        policy == null ? 5000 : policy.timeoutMillis(),
        connector.createdBy(),
        connector.createdAt(),
        connector.updatedAt());
  }

  private String hostOf(String url) {
    return URI.create(url.trim()).getHost().toLowerCase();
  }

  private String normalizeMethod(String method, String fallback) {
    String value = method == null || method.isBlank() ? fallback : method.trim().toUpperCase();
    if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(value)) {
      throw new AppException("WEBHOOK_METHOD_INVALID", "Unsupported webhook method: " + value);
    }
    return value;
  }

  private List<String> normalizeMethods(List<String> methods) {
    return methods.stream().map(item -> normalizeMethod(item, "POST")).distinct().toList();
  }

  private int normalizeMaxBodyBytes(Integer value) {
    if (value == null) {
      return 32768;
    }
    return Math.max(0, Math.min(value, 1048576));
  }

  private int normalizeTimeoutMillis(Integer value) {
    if (value == null) {
      return 5000;
    }
    return Math.max(100, Math.min(value, 60000));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 12. WebhookConnectorController

路径：

```txt id="7fmq5i"
modules/aiops-execution/src/main/java/io/aegisops/execution/WebhookConnectorController.java
```

```java id="phij1o"
package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookConnectorController {
  private final WebhookConnectorService service;

  public WebhookConnectorController(WebhookConnectorService service) {
    this.service = service;
  }

  @GetMapping("/api/webhook-connectors")
  public ApiResponse<List<WebhookConnectorResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/webhook-connectors")
  public ApiResponse<WebhookConnectorResponse> create(
      @RequestBody WebhookConnectorCreateRequest request) {
    return ApiResponse.ok(service.create(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/webhook-connectors/{connectorId}")
  public ApiResponse<WebhookConnectorResponse> get(@PathVariable String connectorId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), connectorId));
  }

  @PostMapping("/api/webhook-connectors/{connectorId}/enable")
  public ApiResponse<WebhookConnectorResponse> enable(@PathVariable String connectorId) {
    return ApiResponse.ok(service.setEnabled(TenantContext.requireTenantId(), connectorId, true));
  }

  @PostMapping("/api/webhook-connectors/{connectorId}/disable")
  public ApiResponse<WebhookConnectorResponse> disable(@PathVariable String connectorId) {
    return ApiResponse.ok(service.setEnabled(TenantContext.requireTenantId(), connectorId, false));
  }
}
```

---

# 13. Runner Webhook Adapter

## 13.1 `WebhookActionPayload.java`

路径：

```txt id="w4txkx"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/webhook/WebhookActionPayload.java
```

```java id="c14fkx"
package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.Map;

public record WebhookActionPayload(
    String connectorId,
    String method,
    String path,
    Map<String, String> headers,
    String body) {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @SuppressWarnings("unchecked")
  public static WebhookActionPayload parse(ObjectMapper objectMapper, String json) {
    try {
      Map<String, Object> map = objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);

      String connectorId = stringValue(map.get("connectorId"));
      if (connectorId.isBlank()) {
        throw new AppException("WEBHOOK_CONNECTOR_ID_REQUIRED", "Webhook connectorId is required");
      }

      Object headersValue = map.get("headers");
      Map<String, String> headers =
          headersValue instanceof Map<?, ?> raw
              ? raw.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          item -> String.valueOf(item.getKey()),
                          item -> item.getValue() == null ? "" : String.valueOf(item.getValue())))
              : Map.of();

      Object bodyValue = map.get("body");
      String body =
          bodyValue == null
              ? ""
              : bodyValue instanceof String text ? text : objectMapper.writeValueAsString(bodyValue);

      return new WebhookActionPayload(
          connectorId,
          stringValue(map.get("method")),
          stringValue(map.get("path")),
          headers,
          body);
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_ACTION_PAYLOAD_INVALID", "Invalid webhook action payload");
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
```

---

## 13.2 `WebhookHttpRequest.java`

路径：

```txt id="890ubp"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/webhook/WebhookHttpRequest.java
```

```java id="tz9ahz"
package io.aegisops.runner.executor.webhook;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record WebhookHttpRequest(
    String method,
    URI uri,
    Map<String, String> headers,
    String body,
    Duration timeout) {}
```

---

## 13.3 `WebhookHttpResponse.java`

```java id="v2gbb6"
package io.aegisops.runner.executor.webhook;

import java.util.Map;

public record WebhookHttpResponse(
    int statusCode,
    Map<String, String> headers,
    String body) {
  public boolean success() {
    return statusCode >= 200 && statusCode < 300;
  }
}
```

---

## 13.4 `WebhookHttpClient.java`

```java id="im8fwv"
package io.aegisops.runner.executor.webhook;

public interface WebhookHttpClient {
  WebhookHttpResponse send(WebhookHttpRequest request);
}
```

---

## 13.5 `JdkWebhookHttpClient.java`

```java id="tc33uy"
package io.aegisops.runner.executor.webhook;

import io.aegisops.common.exception.AppException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JdkWebhookHttpClient implements WebhookHttpClient {
  private final HttpClient client;

  public JdkWebhookHttpClient() {
    this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override
  public WebhookHttpResponse send(WebhookHttpRequest request) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(request.uri())
              .timeout(request.timeout());

      for (Map.Entry<String, String> entry : request.headers().entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }

      String method = request.method().toUpperCase();
      if ("GET".equals(method)) {
        builder.GET();
      } else {
        builder.method(method, HttpRequest.BodyPublishers.ofString(request.body() == null ? "" : request.body()));
      }

      HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

      Map<String, String> headers =
          response.headers().map().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, item -> String.join(",", item.getValue())));

      return new WebhookHttpResponse(response.statusCode(), headers, response.body());
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_HTTP_SEND_FAILED", "Webhook HTTP request failed");
    }
  }
}
```

---

# 14. WebhookSecurityValidator

路径：

```txt id="e2lrn4"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/webhook/WebhookSecurityValidator.java
```

```java id="tl0m42"
package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WebhookSecurityValidator {
  private final WebhookJson json;

  public WebhookSecurityValidator(ObjectMapper objectMapper) {
    this.json = new WebhookJson(objectMapper);
  }

  public void validateLive(
      WebhookConnectorRecord connector,
      WebhookPolicyRecord policy,
      String method,
      URI uri,
      String body) {
    if (!connector.enabled()) {
      throw new AppException("WEBHOOK_CONNECTOR_DISABLED", "Webhook connector is disabled");
    }

    if (policy == null || !policy.enabled()) {
      throw new AppException("WEBHOOK_POLICY_DISABLED", "Webhook policy is disabled");
    }

    if (!policy.allowLive()) {
      throw new AppException("WEBHOOK_LIVE_NOT_ALLOWED", "Webhook live execution is not allowed by policy");
    }

    validateScheme(uri);
    validateMethod(policy, method);
    validateBody(policy, body);
    validateHost(policy, uri);
  }

  public void validateDryRun(WebhookConnectorRecord connector, WebhookPolicyRecord policy, String method, URI uri, String body) {
    if (!connector.enabled()) {
      throw new AppException("WEBHOOK_CONNECTOR_DISABLED", "Webhook connector is disabled");
    }

    validateScheme(uri);

    if (policy != null && policy.enabled()) {
      validateMethod(policy, method);
      validateBody(policy, body);
      validateHost(policy, uri);
    }
  }

  private void validateScheme(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!List.of("http", "https").contains(scheme)) {
      throw new AppException("WEBHOOK_SCHEME_INVALID", "Webhook scheme must be http or https");
    }

    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new AppException("WEBHOOK_HOST_REQUIRED", "Webhook host is required");
    }
  }

  private void validateMethod(WebhookPolicyRecord policy, String method) {
    List<String> allowed =
        json.readStringList(policy.allowedMethodsJson()).stream()
            .map(item -> item.toUpperCase(Locale.ROOT))
            .toList();

    if (!allowed.contains(method.toUpperCase(Locale.ROOT))) {
      throw new AppException("WEBHOOK_METHOD_NOT_ALLOWED", "Webhook method is not allowed");
    }
  }

  private void validateBody(WebhookPolicyRecord policy, String body) {
    int size = body == null ? 0 : body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (size > policy.maxBodyBytes()) {
      throw new AppException("WEBHOOK_BODY_TOO_LARGE", "Webhook body exceeds policy limit");
    }
  }

  private void validateHost(WebhookPolicyRecord policy, URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);

    List<String> allowedHosts =
        json.readStringList(policy.allowedHostsJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .toList();

    if (!allowedHosts.contains(host)) {
      throw new AppException("WEBHOOK_HOST_NOT_ALLOWED", "Webhook host is not allowed");
    }

    if (policy.blockLocalhost() && isLocalhost(host)) {
      throw new AppException("WEBHOOK_LOCALHOST_BLOCKED", "Webhook localhost target is blocked");
    }

    if (policy.blockMetadataIp() && isMetadataIp(host)) {
      throw new AppException("WEBHOOK_METADATA_IP_BLOCKED", "Webhook metadata IP target is blocked");
    }

    if (policy.blockPrivateIp() && isPrivateOrLoopback(host)) {
      throw new AppException("WEBHOOK_PRIVATE_IP_BLOCKED", "Webhook private IP target is blocked");
    }
  }

  private boolean isLocalhost(String host) {
    return "localhost".equals(host) || host.endsWith(".localhost");
  }

  private boolean isMetadataIp(String host) {
    return "169.254.169.254".equals(host);
  }

  private boolean isPrivateOrLoopback(String host) {
    try {
      InetAddress address = InetAddress.getByName(host);
      return address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isSiteLocalAddress()
          || address.isLinkLocalAddress();
    } catch (Exception ex) {
      return false;
    }
  }
}
```

---

# 15. WebhookHeaderMasker

路径：

```txt id="9d4d46"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/webhook/WebhookHeaderMasker.java
```

```java id="44jbc7"
package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WebhookHeaderMasker {
  private final WebhookJson json;

  public WebhookHeaderMasker(ObjectMapper objectMapper) {
    this.json = new WebhookJson(objectMapper);
  }

  public Map<String, String> mask(WebhookConnectorRecord connector, Map<String, String> headers) {
    Set<String> sensitive =
        json.readStringList(connector.sensitiveHeadersJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (sensitive.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
        result.put(entry.getKey(), "***");
      } else {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }
}
```

---

# 16. WebhookStepExecutor

路径：

```txt id="pkkctt"
apps/aiops-runner/src/main/java/io/aegisops/runner/executor/webhook/WebhookStepExecutor.java
```

```java id="8x0k28"
package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.WebhookRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WebhookStepExecutor implements StepExecutor {
  private final WebhookRepository repository;
  private final WebhookHttpClient httpClient;
  private final WebhookSecurityValidator validator;
  private final WebhookHeaderMasker headerMasker;
  private final WebhookJson json;
  private final ObjectMapper objectMapper;

  public WebhookStepExecutor(
      WebhookRepository repository,
      WebhookHttpClient httpClient,
      WebhookSecurityValidator validator,
      WebhookHeaderMasker headerMasker,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.httpClient = httpClient;
    this.validator = validator;
    this.headerMasker = headerMasker;
    this.objectMapper = objectMapper;
    this.json = new WebhookJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "webhook".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    WebhookActionPayload payload = WebhookActionPayload.parse(objectMapper, step.actionPayloadJson());

    WebhookConnectorRecord connector =
        repository
            .findConnector(step.tenantId(), payload.connectorId())
            .orElseThrow(() -> new io.aegisops.common.exception.AppException(
                "WEBHOOK_CONNECTOR_NOT_FOUND", "Webhook connector not found"));

    WebhookPolicyRecord policy =
        repository.findPolicy(step.tenantId(), connector.id()).orElse(null);

    String method = normalizeMethod(payload.method(), connector.defaultMethod());
    URI uri = buildUri(connector.baseUrl(), payload.path());

    Map<String, String> headers = mergedHeaders(connector, payload);
    String body = payload.body() == null ? "" : payload.body();

    if (context.dryRun()) {
      validator.validateDryRun(connector, policy, method, uri, body);
      return StepExecutionResult.success(
          "DRY-RUN webhook request: " + method + " " + uri,
          List.of(
              artifact(
                  step,
                  "webhook-dry-run-request.json",
                  json.write(
                      Map.of(
                          "method", method,
                          "url", uri.toString(),
                          "headers", headerMasker.mask(connector, headers),
                          "bodyBytes", body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                          "live", false)))));
    }

    if (!context.liveEnabled()) {
      return StepExecutionResult.failure(
          "Live webhook execution is disabled.",
          List.of(
              artifact(
                  step,
                  "webhook-live-disabled.json",
                  json.write(Map.of("method", method, "url", uri.toString())))));
    }

    validator.validateLive(connector, policy, method, uri, body);

    WebhookHttpResponse response =
        httpClient.send(
            new WebhookHttpRequest(
                method,
                uri,
                headers,
                body,
                Duration.ofMillis(policy == null ? 5000 : policy.timeoutMillis())));

    Map<String, Object> artifactPayload =
        Map.of(
            "request",
                Map.of(
                    "method", method,
                    "url", uri.toString(),
                    "headers", headerMasker.mask(connector, headers),
                    "bodyBytes", body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length),
            "response",
                Map.of(
                    "statusCode", response.statusCode(),
                    "headers", headerMasker.mask(connector, response.headers()),
                    "bodyPreview", preview(response.body(), 4096)));

    ExecutionArtifactCreateCommand artifact =
        artifact(step, "webhook-response.json", json.write(artifactPayload));

    if (response.success()) {
      return StepExecutionResult.success(
          "Webhook request succeeded with status " + response.statusCode(),
          List.of(artifact));
    }

    return StepExecutionResult.failure(
        "Webhook request failed with status " + response.statusCode(),
        List.of(artifact));
  }

  private Map<String, String> mergedHeaders(WebhookConnectorRecord connector, WebhookActionPayload payload) {
    Map<String, String> headers = new HashMap<>(json.readStringMap(connector.defaultHeadersJson()));
    headers.putAll(payload.headers() == null ? Map.of() : payload.headers());
    return headers;
  }

  private String normalizeMethod(String method, String fallback) {
    String value = method == null || method.isBlank() ? fallback : method;
    return value.toUpperCase(java.util.Locale.ROOT);
  }

  private URI buildUri(String baseUrl, String path) {
    URI base = URI.create(baseUrl);
    if (path == null || path.isBlank()) {
      return base;
    }

    String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return URI.create(normalizedBase + normalizedPath);
  }

  private String preview(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private ExecutionArtifactCreateCommand artifact(ExecutionStepRecord step, String name, String content) {
    return new ExecutionArtifactCreateCommand(
        newId("artifact"),
        step.tenantId(),
        step.executionId(),
        step.id(),
        "json",
        name,
        content,
        "{}");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
```

---

# 17. application.yml

路径：

```txt id="c5pata"
apps/aiops-runner/src/main/resources/application.yml
```

保留：

```yaml id="fmkjjf"
aiops:
  execution:
    api-enabled: false
    live-enabled: false
```

Phase5.4 live webhook 要测试时，手动改：

```yaml id="wcakx2"
aiops:
  execution:
    live-enabled: true
```

默认必须是 false。

---

# 18. 单元测试

## 18.1 WebhookSecurityValidatorTest

路径：

```txt id="6t1lrm"
apps/aiops-runner/src/test/java/io/aegisops/runner/executor/webhook/WebhookSecurityValidatorTest.java
```

```java id="9ome4q"
package io.aegisops.runner.executor.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookSecurityValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookJson json = new WebhookJson(objectMapper);
  private final WebhookSecurityValidator validator = new WebhookSecurityValidator(objectMapper);

  @Test
  void allowAllowedPublicHost() {
    assertDoesNotThrow(
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://ops.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockNonAllowlistedHost() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://evil.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockLocalhost() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("http://localhost:8080"),
                policy(true, List.of("localhost"), List.of("POST")),
                "POST",
                URI.create("http://localhost:8080/hook"),
                "{}"));
  }

  @Test
  void blockMetadataIp() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("http://169.254.169.254"),
                policy(true, List.of("169.254.169.254"), List.of("GET")),
                "GET",
                URI.create("http://169.254.169.254/latest/meta-data"),
                ""));
  }

  @Test
  void blockUnsupportedMethod() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "DELETE",
                URI.create("https://ops.example.com/internal/restart"),
                "{}"));
  }

  private WebhookConnectorRecord connector(String baseUrl) {
    return new WebhookConnectorRecord(
        "whc_1",
        "tenant_1",
        "ops",
        "desc",
        baseUrl,
        "POST",
        "{}",
        json.write(List.of("authorization", "x-api-key")),
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WebhookPolicyRecord policy(boolean allowLive, List<String> hosts, List<String> methods) {
    return new WebhookPolicyRecord(
        "whp_1",
        "tenant_1",
        "whc_1",
        allowLive,
        json.write(hosts),
        json.write(methods),
        true,
        true,
        true,
        32768,
        5000,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
```

---

## 18.2 WebhookStepExecutorTest

路径：

```txt id="3b7kqy"
apps/aiops-runner/src/test/java/io/aegisops/runner/executor/webhook/WebhookStepExecutorTest.java
```

```java id="ber63l"
package io.aegisops.runner.executor.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.WebhookRepository;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.execution.dto.ExecutionRunRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookStepExecutorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookJson json = new WebhookJson(objectMapper);

  @Test
  void dryRunDoesNotSendHttpRequest() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("dry_run", false), step(payload("whc_1")));

    assertTrue(result.success());
    assertFalse(httpClient.called);
    assertEquals(1, result.artifacts().size());
    assertEquals("webhook-dry-run-request.json", result.artifacts().get(0).name());
  }

  @Test
  void liveDisabledFailsWithoutHttpRequest() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("live", false), step(payload("whc_1")));

    assertFalse(result.success());
    assertFalse(httpClient.called);
    assertEquals("Live webhook execution is disabled.", result.errorMessage());
  }

  @Test
  void liveAllowedSendsRequestAndWritesResponseArtifact() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();
    httpClient.response = new WebhookHttpResponse(200, Map.of("x-ok", "true"), "{\"ok\":true}");

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("live", true), step(payload("whc_1")));

    assertTrue(result.success());
    assertTrue(httpClient.called);
    assertEquals("POST", httpClient.request.method());
    assertEquals("https://ops.example.com/internal/restart", httpClient.request.uri().toString());
    assertEquals(1, result.artifacts().size());
    assertEquals("webhook-response.json", result.artifacts().get(0).name());
  }

  private WebhookStepExecutor executor(FakeWebhookRepository repository, FakeWebhookHttpClient httpClient) {
    return new WebhookStepExecutor(
        repository,
        httpClient,
        new WebhookSecurityValidator(objectMapper),
        new WebhookHeaderMasker(objectMapper),
        objectMapper);
  }

  private StepExecutionContext context(String mode, boolean liveEnabled) {
    return new StepExecutionContext(
        new ExecutionRunRecord(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            mode,
            "alice",
            "runner_1",
            OffsetDateTime.now(),
            null,
            null,
            null,
            1,
            1,
            null,
            OffsetDateTime.now().plusSeconds(60),
            OffsetDateTime.now(),
            1800,
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        liveEnabled);
  }

  private ExecutionStepRecord step(String payload) {
    return new ExecutionStepRecord(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Restart service",
        "webhook",
        "service",
        "queued",
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private String payload(String connectorId) {
    return json.write(
        Map.of(
            "connectorId", connectorId,
            "method", "POST",
            "path", "/internal/restart",
            "headers", Map.of("X-Source", "aegisops"),
            "body", Map.of("serviceName", "order-service")));
  }

  private class FakeWebhookRepository implements WebhookRepository {
    @Override
    public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookConnectorRecord(
              "whc_1",
              tenantId,
              "ops",
              "desc",
              "https://ops.example.com",
              "POST",
              json.write(Map.of("Authorization", "secret")),
              json.write(List.of("authorization")),
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookPolicyRecord(
              "whp_1",
              tenantId,
              connectorId,
              true,
              json.write(List.of("ops.example.com")),
              json.write(List.of("POST")),
              true,
              true,
              true,
              32768,
              5000,
              true,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public void createConnector(WebhookConnectorCreateCommand command) {}

    @Override
    public void createPolicy(WebhookPolicyCreateCommand command) {}

    @Override
    public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
      return true;
    }
  }

  private static class FakeWebhookHttpClient implements WebhookHttpClient {
    boolean called;
    WebhookHttpRequest request;
    WebhookHttpResponse response = new WebhookHttpResponse(200, Map.of(), "ok");

    @Override
    public WebhookHttpResponse send(WebhookHttpRequest request) {
      this.called = true;
      this.request = request;
      return response;
    }
  }
}
```

---

## 18.3 WebhookConnectorServiceTest

路径：

```txt id="zj4slq"
modules/aiops-execution/src/test/java/io/aegisops/execution/WebhookConnectorServiceTest.java
```

```java id="2me40i"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookConnectorServiceTest {
  @Test
  void createConnectorCreatesPolicyWithDefaultHost() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    WebhookConnectorService service = new WebhookConnectorService(repository, new ObjectMapper());

    var response =
        service.create(
            "tenant_1",
            new WebhookConnectorCreateRequest(
                "ops",
                "desc",
                "https://ops.example.com",
                "POST",
                Map.of("X-Source", "aegisops"),
                List.of("authorization"),
                null,
                List.of("POST"),
                false,
                32768,
                5000,
                "alice"));

    assertEquals("ops", response.name());
    assertEquals(List.of("ops.example.com"), response.allowedHosts());
    assertEquals(List.of("POST"), response.allowedMethods());
  }

  @Test
  void createRejectsInvalidScheme() {
    WebhookConnectorService service =
        new WebhookConnectorService(new FakeWebhookRepository(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1",
                new WebhookConnectorCreateRequest(
                    "bad",
                    "desc",
                    "file:///etc/passwd",
                    "POST",
                    Map.of(),
                    List.of(),
                    null,
                    List.of("POST"),
                    false,
                    32768,
                    5000,
                    "alice")));
  }

  private static class FakeWebhookRepository implements WebhookRepository {
    private final WebhookJson json = new WebhookJson(new ObjectMapper());
    WebhookConnectorCreateCommand connector;
    WebhookPolicyCreateCommand policy;

    @Override
    public void createConnector(WebhookConnectorCreateCommand command) {
      connector = command;
    }

    @Override
    public void createPolicy(WebhookPolicyCreateCommand command) {
      policy = command;
    }

    @Override
    public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookConnectorRecord(
              connector.id(),
              connector.tenantId(),
              connector.name(),
              connector.description(),
              connector.baseUrl(),
              connector.defaultMethod(),
              connector.defaultHeadersJson(),
              connector.sensitiveHeadersJson(),
              connector.enabled(),
              connector.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookPolicyRecord(
              policy.id(),
              policy.tenantId(),
              policy.connectorId(),
              policy.allowLive(),
              policy.allowedHostsJson(),
              policy.allowedMethodsJson(),
              policy.blockPrivateIp(),
              policy.blockLocalhost(),
              policy.blockMetadataIp(),
              policy.maxBodyBytes(),
              policy.timeoutMillis(),
              policy.enabled(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
      return true;
    }
  }
}
```

---

## 18.4 JooqWebhookRepositoryGeneratedSqlTest

路径：

```txt id="7j4lji"
modules/aiops-execution/src/test/java/io/aegisops/execution/JooqWebhookRepositoryGeneratedSqlTest.java
```

```java id="8h0ifi"
package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqWebhookRepositoryGeneratedSqlTest {
  @Test
  void createConnectorUsesWebhookConnectorTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.dsl().renderInlined(context.query()));
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqWebhookRepository repository =
        new JooqWebhookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createConnector(
        new WebhookConnectorCreateCommand(
            "whc_1",
            "tenant_1",
            "ops",
            "desc",
            "https://ops.example.com",
            "POST",
            "{}",
            "[\"authorization\"]",
            true,
            "alice"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("webhook_connector"));
    assertTrue(sql.contains("ops.example.com"));
  }

  @Test
  void createPolicyUsesWebhookExecutionPolicyTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.dsl().renderInlined(context.query()));
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqWebhookRepository repository =
        new JooqWebhookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createPolicy(
        new WebhookPolicyCreateCommand(
            "whp_1",
            "tenant_1",
            "whc_1",
            false,
            "[\"ops.example.com\"]",
            "[\"POST\"]",
            true,
            true,
            true,
            32768,
            5000,
            true));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("webhook_execution_policy"));
    assertTrue(sql.contains("ops.example.com"));
  }
}
```

---

## 18.5 RunnerSafetyTest 增强

路径：

```txt id="z9bj5g"
apps/aiops-runner/src/test/java/io/aegisops/runner/RunnerSafetyTest.java
```

补充断言：

```java id="dtf9x4"
assertFalse(content.contains("ProcessBuilder"));
assertFalse(content.contains("Runtime.getRuntime"));
assertFalse(content.contains("JSch"));
assertFalse(content.contains("sshj"));
assertFalse(content.contains("Ansible"));
```

允许：

```txt id="tuasyb"
java.net.http.HttpClient
```

因为 Phase5.4 的 Webhook Adapter 需要 HTTP client。

---

# 19. 文档

路径：

```txt id="fftw0d"
docs/mvp/design/phase5.4-webhook-adapter.md
```

````md id="7v3wbf"
# Phase5.4 Webhook Adapter

## 目标

Phase5.4 为 aiops-runner 增加 Webhook Adapter。

## 原则

- server 不发 webhook
- runner 发 webhook
- 默认 dry-run
- live webhook 默认关闭
- live webhook 必须通过 allowlist 和安全校验
- 请求与响应摘要写 execution_artifact

## 安全策略

阻止：

- localhost
- 127.0.0.0/8
- private ip
- link local ip
- metadata ip 169.254.169.254
- non-http scheme
- non-allowlisted host
- disallowed method
- too large body

## action_payload

```json
{
  "connectorId": "whc_xxx",
  "method": "POST",
  "path": "/internal/restart",
  "headers": {
    "X-Source": "aegisops"
  },
  "body": {
    "serviceName": "order-service"
  }
}
```
````

## dry-run

dry-run 不发送 HTTP 请求，只写 artifact。

## live

live 需要：

- execution_run.mode = live
- aiops.execution.live-enabled = true
- connector.enabled = true
- policy.enabled = true
- policy.allow_live = true

## 后续

Phase5.5 做 Ansible Adapter。

````

---

# 20. 验证命令

```powershell id="co4cw4"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-execution -am test
mvn -pl apps/aiops-runner -am test
mvn -pl apps/aiops-server -am test
````

全量：

```powershell id="rpkkgy"
mvn test
```

---

# 21. 验收标准

```txt id="yz1awl"
1. webhook_connector 表存在。
2. webhook_execution_policy 表存在。
3. jOOQ generated Tables 包含 WEBHOOK_CONNECTOR。
4. jOOQ generated Tables 包含 WEBHOOK_EXECUTION_POLICY。
5. 可以创建 webhook connector。
6. 创建 connector 时自动创建 policy。
7. actionType=webhook 的 step 可以被 runner 识别。
8. dry-run webhook 不发送 HTTP 请求。
9. dry-run webhook 会写 execution_artifact。
10. live webhook 默认被拒绝。
11. live-enabled=true 且 policy allowLive=true 时才允许真实发送。
12. localhost 被拦截。
13. metadata IP 被拦截。
14. private IP 被拦截。
15. 非 allowlist host 被拦截。
16. disallowed method 被拦截。
17. response artifact 会脱敏敏感 headers。
18. runner 中仍不存在 ProcessBuilder / Runtime.exec / SSH / Ansible。
```

---

# 22. 建议提交信息

```txt id="xmhums"
feat(execution): add webhook adapter with dry-run and allowlist policy
```

---

# 23. Phase5.5 下一步

Phase5.4 完成后，Phase5.5 建议做：

```txt id="91qs2k"
Phase5.5 Ansible Adapter
```

目标：

```txt id="z5v71x"
1. ansible_inventory
2. ansible_playbook
3. ansible_execution_policy
4. ansible --check dry-run
5. playbook allowlist
6. variables schema validate
7. stdout/stderr artifact
8. live ansible 默认关闭
```

不要下一步直接做 SSH。SSH 最后做。

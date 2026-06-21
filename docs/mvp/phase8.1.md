# Phase8.1：Plugin System

> Phase8.1 目标：给 AegisOps 增加 **安全插件系统 MVP**。
> 这一阶段只做插件描述符、扩展点、租户启停、前端 manifest、Agent Tool allowlist。
> **不动态执行第三方代码，不加载远程 JS，不执行插件脚本，不调 Runner。**

---

# 1. Phase8.1 定位

Phase8.0 已完成 SaaS 多租户安全基线：

```txt
Tenant Required
Internal Agent Token
Tenant Rate Limit
Security Event Audit
Python Agent internal headers
```

Phase8.1 在这个基础上加：

```txt
Plugin Descriptor
Extension Point Registry
Tenant Plugin Enablement
Frontend Plugin Manifest
Agent Tool Plugin Allowlist
Plugin Event Audit
```

---

# 2. 不做什么

```txt
1. 不动态加载 jar
2. 不加载远程 JS
3. 不执行插件脚本
4. 不开放任意 Python tool
5. 不允许插件直接调 Runner
6. 不允许插件直接 Webhook / Ansible / SSH
7. 不做 marketplace
8. 不做 billing
```

第一版插件系统的安全边界是：

```txt
插件只能声明能力
插件只能贡献 UI manifest
插件只能申请使用内置 tool key
租户启用后，Agent 才能调用 allowlist 中的内置工具
```

---

# 3. 插件模型

## 3.1 Plugin Descriptor

一个插件描述自己：

```json
{
  "pluginKey": "builtin.incident-copilot",
  "name": "Incident Copilot",
  "version": "0.1.0",
  "description": "Incident assistant plugin",
  "frontend": {
    "contributions": [
      {
        "extensionPoint": "incident.detail.sidebar",
        "contributionId": "incident-copilot-panel",
        "title": "Incident Copilot",
        "componentKey": "builtin.incidentCopilotPanel",
        "order": 100,
        "props": {}
      }
    ]
  },
  "agentTools": [
    {
      "toolKey": "knowledge.search_cases",
      "riskLevel": "low",
      "description": "Search similar incident cases"
    }
  ]
}
```

---

## 3.2 Extension Points

内置扩展点第一版固定：

```txt
incident.detail.sidebar
incident.detail.action
dashboard.card
settings.page
agent.tool
```

---

## 3.3 Tool Keys

Phase8.1 允许插件申请的内置 Agent Tool Key：

```txt
evidence.fetch
knowledge.search_cases
checkpoint.create
checkpoint.get
memory.search
memory.create
```

注意：

```txt
这些 tool key 仍然只是 Agent 内部工具。
不代表允许执行 SSH / Ansible / Webhook / Runner。
```

---

# 4. API 设计

## 4.1 Public API

```txt
GET  /api/plugin-extension-points
GET  /api/plugins
GET  /api/plugins/{pluginId}
GET  /api/tenant/plugins
POST /api/plugins/{pluginId}/enable
POST /api/plugins/{pluginId}/disable
GET  /api/tenant/plugins/frontend-manifest
GET  /api/tenant/plugins/tools
POST /api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/allow
POST /api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/deny
```

---

## 4.2 Internal Agent API

```txt
POST /internal/agent/plugins/tools/authorize
```

请求：

```json
{
  "tenantId": "tenant_1",
  "toolKey": "memory.search"
}
```

返回：

```json
{
  "allowed": true,
  "toolKey": "memory.search",
  "reason": "allowed by tenant plugin policy"
}
```

---

# 5. Migration

路径：

```txt
apps/aiops-server/src/main/resources/db/migration/V27__phase8_1_plugin_system.sql
```

```sql
-- Phase 8.1: Plugin System.
-- Safe plugin descriptor, tenant enablement, frontend manifest and agent tool allowlist.
-- This phase does not dynamically load code and does not add execution capability.

create table if not exists plugin_descriptor (
  id varchar(64) primary key,
  plugin_key varchar(128) not null,
  name varchar(160) not null,
  version varchar(64) not null,
  description text,
  provider varchar(128) not null default 'builtin',
  status varchar(32) not null default 'active',
  manifest_json jsonb not null default '{}'::jsonb,
  capabilities_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_plugin_descriptor_key_version unique (plugin_key, version),
  constraint ck_plugin_descriptor_status check (status in ('active', 'disabled', 'deprecated'))
);

create index if not exists idx_plugin_descriptor_key
  on plugin_descriptor(plugin_key);

create index if not exists idx_plugin_descriptor_status
  on plugin_descriptor(status, created_at desc);

create table if not exists tenant_plugin (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  status varchar(32) not null default 'enabled',
  config_json jsonb not null default '{}'::jsonb,
  enabled_by varchar(64) not null default 'system',
  enabled_at timestamptz not null default now(),
  disabled_by varchar(64),
  disabled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_plugin unique (tenant_id, plugin_id),
  constraint ck_tenant_plugin_status check (status in ('enabled', 'disabled'))
);

create index if not exists idx_tenant_plugin_tenant_status
  on tenant_plugin(tenant_id, status, created_at desc);

create table if not exists tenant_plugin_tool_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  tenant_plugin_id varchar(64) not null references tenant_plugin(id) on delete cascade,
  plugin_id varchar(64) not null references plugin_descriptor(id) on delete cascade,
  tool_key varchar(128) not null,
  status varchar(32) not null default 'allowed',
  risk_level varchar(32) not null default 'low',
  created_by varchar(64) not null default 'system',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_tenant_plugin_tool_policy unique (tenant_id, tenant_plugin_id, tool_key),
  constraint ck_tenant_plugin_tool_policy_status check (status in ('allowed', 'denied')),
  constraint ck_tenant_plugin_tool_policy_risk check (risk_level in ('low', 'medium', 'high', 'critical'))
);

create index if not exists idx_tenant_plugin_tool_policy_lookup
  on tenant_plugin_tool_policy(tenant_id, tool_key, status);

create table if not exists plugin_event (
  id varchar(64) primary key,
  tenant_id varchar(64),
  plugin_id varchar(64),
  tenant_plugin_id varchar(64),
  event_type varchar(64) not null,
  summary text not null,
  actor varchar(64) not null default 'system',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),

  constraint ck_plugin_event_type check (event_type in (
    'plugin_registered',
    'plugin_enabled',
    'plugin_disabled',
    'tool_allowed',
    'tool_denied',
    'tool_authorized',
    'tool_denied_by_policy',
    'manifest_requested'
  ))
);

create index if not exists idx_plugin_event_tenant
  on plugin_event(tenant_id, created_at desc);

create index if not exists idx_plugin_event_plugin
  on plugin_event(plugin_id, created_at desc);
```

---

# 6. jOOQ Codegen

路径：

```txt
modules/aiops-persistence/src/main/resources/jooq-codegen.xml
```

追加：

```txt
plugin_descriptor | tenant_plugin | tenant_plugin_tool_policy | plugin_event
```

---

# 7. Java 模块

新增模块：

```txt
modules/aiops-plugin
```

---

## 7.1 `modules/aiops-plugin/pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.aegisops</groupId>
    <artifactId>ai-ops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>aiops-plugin</artifactId>

  <dependencies>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-common</artifactId>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-persistence</artifactId>
    </dependency>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-security</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jooq</groupId>
      <artifactId>jooq</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

同时在根 `pom.xml` 的 `<modules>` 里追加：

```xml
<module>modules/aiops-plugin</module>
```

在 `apps/aiops-server/pom.xml` 追加依赖：

```xml
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-plugin</artifactId>
</dependency>
```

---

# 8. Java DTO

## 8.1 `PluginDescriptorCreateCommand.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/dto/PluginDescriptorCreateCommand.java
```

```java
package io.aegisops.plugin.dto;

public record PluginDescriptorCreateCommand(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    String createdBy) {}
```

---

## 8.2 `PluginDescriptorRecord.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record PluginDescriptorRecord(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.3 `PluginDescriptorResponse.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record PluginDescriptorResponse(
    String id,
    String pluginKey,
    String name,
    String version,
    String description,
    String provider,
    String status,
    String manifestJson,
    String capabilitiesJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.4 `TenantPluginRecord.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginRecord(
    String id,
    String tenantId,
    String pluginId,
    String pluginKey,
    String name,
    String version,
    String status,
    String configJson,
    String manifestJson,
    String capabilitiesJson,
    String enabledBy,
    OffsetDateTime enabledAt,
    String disabledBy,
    OffsetDateTime disabledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.5 `TenantPluginResponse.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginResponse(
    String id,
    String pluginId,
    String pluginKey,
    String name,
    String version,
    String status,
    String configJson,
    OffsetDateTime enabledAt,
    OffsetDateTime disabledAt) {}
```

---

## 8.6 `PluginEnableRequest.java`

```java
package io.aegisops.plugin.dto;

public record PluginEnableRequest(
    String enabledBy,
    String configJson) {}
```

---

## 8.7 `PluginDisableRequest.java`

```java
package io.aegisops.plugin.dto;

public record PluginDisableRequest(
    String disabledBy) {}
```

---

## 8.8 `TenantPluginToolPolicyRecord.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginToolPolicyRecord(
    String id,
    String tenantId,
    String tenantPluginId,
    String pluginId,
    String pluginKey,
    String toolKey,
    String status,
    String riskLevel,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.9 `TenantPluginToolPolicyResponse.java`

```java
package io.aegisops.plugin.dto;

import java.time.OffsetDateTime;

public record TenantPluginToolPolicyResponse(
    String id,
    String tenantPluginId,
    String pluginId,
    String pluginKey,
    String toolKey,
    String status,
    String riskLevel,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

---

## 8.10 `AgentToolAuthorizeRequest.java`

```java
package io.aegisops.plugin.dto;

public record AgentToolAuthorizeRequest(
    String tenantId,
    String toolKey) {}
```

---

## 8.11 `AgentToolAuthorizeResponse.java`

```java
package io.aegisops.plugin.dto;

public record AgentToolAuthorizeResponse(
    boolean allowed,
    String toolKey,
    String reason) {}
```

---

## 8.12 `PluginExtensionPointResponse.java`

```java
package io.aegisops.plugin.dto;

public record PluginExtensionPointResponse(
    String extensionPoint,
    String description,
    boolean frontend,
    boolean agentTool) {}
```

---

## 8.13 `TenantFrontendManifestResponse.java`

```java
package io.aegisops.plugin.dto;

import java.util.List;

public record TenantFrontendManifestResponse(
    String tenantId,
    List<String> enabledPluginKeys,
    List<Object> contributions) {}
```

---

# 9. JSON 工具与校验器

## 9.1 `PluginJson.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginJson.java
```

```java
package io.aegisops.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PluginJson {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final TypeReference<List<Object>> LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public PluginJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_WRITE_FAILED", "Failed to serialize plugin json");
    }
  }

  public Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      Map<String, Object> value = objectMapper.readValue(json, MAP);
      return value == null ? Map.of() : value;
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_READ_FAILED", "Failed to parse plugin json object");
    }
  }

  public List<Object> readList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      List<Object> value = objectMapper.readValue(json, LIST);
      return value == null ? List.of() : value;
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_READ_FAILED", "Failed to parse plugin json array");
    }
  }
}
```

---

## 9.2 `PluginManifestValidator.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginManifestValidator.java
```

```java
package io.aegisops.plugin;

import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PluginManifestValidator {
  public static final Set<String> ALLOWED_EXTENSION_POINTS =
      Set.of(
          "incident.detail.sidebar",
          "incident.detail.action",
          "dashboard.card",
          "settings.page",
          "agent.tool");

  public static final Set<String> ALLOWED_TOOL_KEYS =
      Set.of(
          "evidence.fetch",
          "knowledge.search_cases",
          "checkpoint.create",
          "checkpoint.get",
          "memory.search",
          "memory.create");

  private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9_.-]{1,128}$");

  private final PluginJson json;

  public PluginManifestValidator(PluginJson json) {
    this.json = json;
  }

  public void validateManifest(String manifestJson) {
    Map<String, Object> manifest = json.readMap(manifestJson);

    rejectRemoteCode(manifest);

    Object frontend = manifest.get("frontend");
    if (frontend instanceof Map<?, ?> frontendMap) {
      validateFrontend(frontendMap);
    }

    Object tools = manifest.get("agentTools");
    if (tools instanceof List<?> toolList) {
      validateTools(toolList);
    }
  }

  public void requireAllowedToolKey(String toolKey) {
    if (toolKey == null || !ALLOWED_TOOL_KEYS.contains(toolKey)) {
      throw new AppException("PLUGIN_TOOL_NOT_ALLOWED", "Plugin tool key is not allowed");
    }
  }

  private void validateFrontend(Map<?, ?> frontendMap) {
    Object contributions = frontendMap.get("contributions");
    if (contributions == null) {
      return;
    }

    if (!(contributions instanceof List<?> list)) {
      throw new AppException("PLUGIN_MANIFEST_INVALID", "frontend.contributions must be array");
    }

    for (Object item : list) {
      if (!(item instanceof Map<?, ?> contribution)) {
        throw new AppException("PLUGIN_MANIFEST_INVALID", "frontend contribution must be object");
      }

      String extensionPoint = stringValue(contribution.get("extensionPoint"));
      String contributionId = stringValue(contribution.get("contributionId"));
      String componentKey = stringValue(contribution.get("componentKey"));

      if (!ALLOWED_EXTENSION_POINTS.contains(extensionPoint)) {
        throw new AppException("PLUGIN_EXTENSION_POINT_INVALID", "Invalid extension point");
      }

      if (!SAFE_KEY.matcher(contributionId).matches()) {
        throw new AppException("PLUGIN_CONTRIBUTION_ID_INVALID", "Invalid contribution id");
      }

      if (!SAFE_KEY.matcher(componentKey).matches()) {
        throw new AppException("PLUGIN_COMPONENT_KEY_INVALID", "Invalid component key");
      }

      if (contribution.containsKey("remoteUrl")
          || contribution.containsKey("script")
          || contribution.containsKey("html")) {
        throw new AppException("PLUGIN_REMOTE_CODE_FORBIDDEN", "Remote plugin code is forbidden");
      }
    }
  }

  private void validateTools(List<?> toolList) {
    for (Object item : toolList) {
      if (!(item instanceof Map<?, ?> tool)) {
        throw new AppException("PLUGIN_MANIFEST_INVALID", "agent tool must be object");
      }

      String toolKey = stringValue(tool.get("toolKey"));
      requireAllowedToolKey(toolKey);
    }
  }

  private void rejectRemoteCode(Map<String, Object> manifest) {
    if (manifest.containsKey("remoteEntry")
        || manifest.containsKey("scriptUrl")
        || manifest.containsKey("iframeUrl")) {
      throw new AppException("PLUGIN_REMOTE_CODE_FORBIDDEN", "Remote plugin code is forbidden");
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
```

---

# 10. Extension Registry

## 10.1 `PluginExtensionPointRegistry.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginExtensionPointRegistry.java
```

```java
package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PluginExtensionPointRegistry {
  public List<PluginExtensionPointResponse> list() {
    return List.of(
        new PluginExtensionPointResponse(
            "incident.detail.sidebar",
            "Incident detail sidebar panel",
            true,
            false),
        new PluginExtensionPointResponse(
            "incident.detail.action",
            "Incident detail action button",
            true,
            false),
        new PluginExtensionPointResponse(
            "dashboard.card",
            "Dashboard card contribution",
            true,
            false),
        new PluginExtensionPointResponse(
            "settings.page",
            "Settings page contribution",
            true,
            false),
        new PluginExtensionPointResponse(
            "agent.tool",
            "Agent tool allowlist contribution",
            false,
            true));
  }
}
```

---

# 11. Repository

## 11.1 `PluginRepository.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginRepository.java
```

```java
package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import java.util.List;
import java.util.Optional;

public interface PluginRepository {
  void upsertDescriptor(PluginDescriptorCreateCommand command);

  Optional<PluginDescriptorRecord> findPlugin(String pluginId);

  Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version);

  List<PluginDescriptorRecord> listPlugins();

  Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId);

  Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId);

  List<TenantPluginRecord> listTenantPlugins(String tenantId);

  String enablePlugin(String tenantId, String pluginId, String configJson, String enabledBy);

  boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy);

  List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId);

  Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(String tenantId, String toolKey);

  void upsertToolPolicy(
      String id,
      String tenantId,
      String tenantPluginId,
      String pluginId,
      String toolKey,
      String status,
      String riskLevel,
      String createdBy);

  void createEvent(
      String id,
      String tenantId,
      String pluginId,
      String tenantPluginId,
      String eventType,
      String summary,
      String actor,
      String metadataJson);
}
```

---

## 11.2 `JooqPluginRepository.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/JooqPluginRepository.java
```

```java
package io.aegisops.plugin;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.PLUGIN_DESCRIPTOR;
import static io.aegisops.persistence.jooq.Tables.PLUGIN_EVENT;
import static io.aegisops.persistence.jooq.Tables.TENANT_PLUGIN;
import static io.aegisops.persistence.jooq.Tables.TENANT_PLUGIN_TOOL_POLICY;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPluginRepository implements PluginRepository {
  private final DSLContext dsl;

  public JooqPluginRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void upsertDescriptor(PluginDescriptorCreateCommand command) {
    dsl.insertInto(PLUGIN_DESCRIPTOR)
        .set(PLUGIN_DESCRIPTOR.ID, command.id())
        .set(PLUGIN_DESCRIPTOR.PLUGIN_KEY, command.pluginKey())
        .set(PLUGIN_DESCRIPTOR.NAME, command.name())
        .set(PLUGIN_DESCRIPTOR.VERSION, command.version())
        .set(PLUGIN_DESCRIPTOR.DESCRIPTION, command.description())
        .set(PLUGIN_DESCRIPTOR.PROVIDER, command.provider())
        .set(PLUGIN_DESCRIPTOR.STATUS, command.status())
        .set(PLUGIN_DESCRIPTOR.MANIFEST_JSON, jsonbValue(command.manifestJson()))
        .set(PLUGIN_DESCRIPTOR.CAPABILITIES_JSON, jsonbValue(command.capabilitiesJson()))
        .set(PLUGIN_DESCRIPTOR.CREATED_BY, command.createdBy())
        .set(PLUGIN_DESCRIPTOR.CREATED_AT, DSL.currentOffsetDateTime())
        .set(PLUGIN_DESCRIPTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(PLUGIN_DESCRIPTOR.PLUGIN_KEY, PLUGIN_DESCRIPTOR.VERSION)
        .doUpdate()
        .set(PLUGIN_DESCRIPTOR.NAME, command.name())
        .set(PLUGIN_DESCRIPTOR.DESCRIPTION, command.description())
        .set(PLUGIN_DESCRIPTOR.PROVIDER, command.provider())
        .set(PLUGIN_DESCRIPTOR.STATUS, command.status())
        .set(PLUGIN_DESCRIPTOR.MANIFEST_JSON, jsonbValue(command.manifestJson()))
        .set(PLUGIN_DESCRIPTOR.CAPABILITIES_JSON, jsonbValue(command.capabilitiesJson()))
        .set(PLUGIN_DESCRIPTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<PluginDescriptorRecord> findPlugin(String pluginId) {
    return selectPlugin()
        .where(PLUGIN_DESCRIPTOR.ID.eq(pluginId))
        .fetchOptional(this::toPluginRecord);
  }

  @Override
  public Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version) {
    return selectPlugin()
        .where(PLUGIN_DESCRIPTOR.PLUGIN_KEY.eq(pluginKey))
        .and(PLUGIN_DESCRIPTOR.VERSION.eq(version))
        .fetchOptional(this::toPluginRecord);
  }

  @Override
  public List<PluginDescriptorRecord> listPlugins() {
    return selectPlugin()
        .orderBy(PLUGIN_DESCRIPTOR.PLUGIN_KEY.asc(), PLUGIN_DESCRIPTOR.VERSION.desc())
        .fetch(this::toPluginRecord);
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId) {
    return selectTenantPlugin()
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN.ID.eq(tenantPluginId))
        .fetchOptional(this::toTenantPluginRecord);
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId) {
    return selectTenantPlugin()
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN.PLUGIN_ID.eq(pluginId))
        .fetchOptional(this::toTenantPluginRecord);
  }

  @Override
  public List<TenantPluginRecord> listTenantPlugins(String tenantId) {
    return selectTenantPlugin()
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .orderBy(TENANT_PLUGIN.CREATED_AT.desc())
        .fetch(this::toTenantPluginRecord);
  }

  @Override
  public String enablePlugin(String tenantId, String pluginId, String configJson, String enabledBy) {
    Optional<TenantPluginRecord> existing = findTenantPluginByPluginId(tenantId, pluginId);
    if (existing.isPresent()) {
      String id = existing.get().id();
      dsl.update(TENANT_PLUGIN)
          .set(TENANT_PLUGIN.STATUS, "enabled")
          .set(TENANT_PLUGIN.CONFIG_JSON, jsonbValue(configJson))
          .set(TENANT_PLUGIN.ENABLED_BY, enabledBy)
          .set(TENANT_PLUGIN.ENABLED_AT, DSL.currentOffsetDateTime())
          .set(TENANT_PLUGIN.DISABLED_BY, (String) null)
          .set(TENANT_PLUGIN.DISABLED_AT, (java.time.OffsetDateTime) null)
          .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
          .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
          .and(TENANT_PLUGIN.ID.eq(id))
          .execute();
      return id;
    }

    String id = "tplg_" + java.util.UUID.randomUUID().toString().replace("-", "");
    dsl.insertInto(TENANT_PLUGIN)
        .set(TENANT_PLUGIN.ID, id)
        .set(TENANT_PLUGIN.TENANT_ID, tenantId)
        .set(TENANT_PLUGIN.PLUGIN_ID, pluginId)
        .set(TENANT_PLUGIN.STATUS, "enabled")
        .set(TENANT_PLUGIN.CONFIG_JSON, jsonbValue(configJson))
        .set(TENANT_PLUGIN.ENABLED_BY, enabledBy)
        .set(TENANT_PLUGIN.ENABLED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
    return id;
  }

  @Override
  public boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy) {
    return dsl.update(TENANT_PLUGIN)
            .set(TENANT_PLUGIN.STATUS, "disabled")
            .set(TENANT_PLUGIN.DISABLED_BY, disabledBy)
            .set(TENANT_PLUGIN.DISABLED_AT, DSL.currentOffsetDateTime())
            .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
            .and(TENANT_PLUGIN.ID.eq(tenantPluginId))
            .and(TENANT_PLUGIN.STATUS.eq("enabled"))
            .execute()
        > 0;
  }

  @Override
  public List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId) {
    return selectToolPolicy()
        .where(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID.eq(tenantId))
        .orderBy(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY.asc())
        .fetch(this::toToolPolicyRecord);
  }

  @Override
  public Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(String tenantId, String toolKey) {
    return selectToolPolicy()
        .where(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY.eq(toolKey))
        .and(TENANT_PLUGIN_TOOL_POLICY.STATUS.eq("allowed"))
        .and(TENANT_PLUGIN.STATUS.eq("enabled"))
        .and(PLUGIN_DESCRIPTOR.STATUS.eq("active"))
        .fetchOptional(this::toToolPolicyRecord);
  }

  @Override
  public void upsertToolPolicy(
      String id,
      String tenantId,
      String tenantPluginId,
      String pluginId,
      String toolKey,
      String status,
      String riskLevel,
      String createdBy) {
    dsl.insertInto(TENANT_PLUGIN_TOOL_POLICY)
        .set(TENANT_PLUGIN_TOOL_POLICY.ID, id)
        .set(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID, tenantId)
        .set(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID, tenantPluginId)
        .set(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID, pluginId)
        .set(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY, toolKey)
        .set(TENANT_PLUGIN_TOOL_POLICY.STATUS, status)
        .set(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL, riskLevel)
        .set(TENANT_PLUGIN_TOOL_POLICY.CREATED_BY, createdBy)
        .set(TENANT_PLUGIN_TOOL_POLICY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(
            TENANT_PLUGIN_TOOL_POLICY.TENANT_ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID,
            TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY)
        .doUpdate()
        .set(TENANT_PLUGIN_TOOL_POLICY.STATUS, status)
        .set(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL, riskLevel)
        .set(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createEvent(
      String id,
      String tenantId,
      String pluginId,
      String tenantPluginId,
      String eventType,
      String summary,
      String actor,
      String metadataJson) {
    dsl.insertInto(PLUGIN_EVENT)
        .set(PLUGIN_EVENT.ID, id)
        .set(PLUGIN_EVENT.TENANT_ID, tenantId)
        .set(PLUGIN_EVENT.PLUGIN_ID, pluginId)
        .set(PLUGIN_EVENT.TENANT_PLUGIN_ID, tenantPluginId)
        .set(PLUGIN_EVENT.EVENT_TYPE, eventType)
        .set(PLUGIN_EVENT.SUMMARY, summary)
        .set(PLUGIN_EVENT.ACTOR, actor)
        .set(PLUGIN_EVENT.METADATA, jsonbValue(metadataJson))
        .set(PLUGIN_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private org.jooq.SelectJoinStep<Record> selectPlugin() {
    return dsl.select(
            PLUGIN_DESCRIPTOR.ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            PLUGIN_DESCRIPTOR.DESCRIPTION,
            PLUGIN_DESCRIPTOR.PROVIDER,
            PLUGIN_DESCRIPTOR.STATUS,
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("capabilities_json_string"),
            PLUGIN_DESCRIPTOR.CREATED_BY,
            PLUGIN_DESCRIPTOR.CREATED_AT,
            PLUGIN_DESCRIPTOR.UPDATED_AT)
        .from(PLUGIN_DESCRIPTOR);
  }

  private org.jooq.SelectJoinStep<Record> selectTenantPlugin() {
    return dsl.select(
            TENANT_PLUGIN.ID,
            TENANT_PLUGIN.TENANT_ID,
            TENANT_PLUGIN.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            TENANT_PLUGIN.STATUS,
            TENANT_PLUGIN.CONFIG_JSON.cast(String.class).as("config_json_string"),
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("capabilities_json_string"),
            TENANT_PLUGIN.ENABLED_BY,
            TENANT_PLUGIN.ENABLED_AT,
            TENANT_PLUGIN.DISABLED_BY,
            TENANT_PLUGIN.DISABLED_AT,
            TENANT_PLUGIN.CREATED_AT,
            TENANT_PLUGIN.UPDATED_AT)
        .from(TENANT_PLUGIN)
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN.PLUGIN_ID));
  }

  private org.jooq.SelectJoinStep<Record> selectToolPolicy() {
    return dsl.select(
            TENANT_PLUGIN_TOOL_POLICY.ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID,
            TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY,
            TENANT_PLUGIN_TOOL_POLICY.STATUS,
            TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_BY,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_AT,
            TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT)
        .from(TENANT_PLUGIN_TOOL_POLICY)
        .join(TENANT_PLUGIN)
        .on(TENANT_PLUGIN.ID.eq(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID))
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID));
  }

  private PluginDescriptorRecord toPluginRecord(Record record) {
    return new PluginDescriptorRecord(
        record.get(PLUGIN_DESCRIPTOR.ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(PLUGIN_DESCRIPTOR.NAME),
        record.get(PLUGIN_DESCRIPTOR.VERSION),
        record.get(PLUGIN_DESCRIPTOR.DESCRIPTION),
        record.get(PLUGIN_DESCRIPTOR.PROVIDER),
        record.get(PLUGIN_DESCRIPTOR.STATUS),
        record.get("manifest_json_string", String.class),
        record.get("capabilities_json_string", String.class),
        record.get(PLUGIN_DESCRIPTOR.CREATED_BY),
        record.get(PLUGIN_DESCRIPTOR.CREATED_AT),
        record.get(PLUGIN_DESCRIPTOR.UPDATED_AT));
  }

  private TenantPluginRecord toTenantPluginRecord(Record record) {
    return new TenantPluginRecord(
        record.get(TENANT_PLUGIN.ID),
        record.get(TENANT_PLUGIN.TENANT_ID),
        record.get(TENANT_PLUGIN.PLUGIN_ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(PLUGIN_DESCRIPTOR.NAME),
        record.get(PLUGIN_DESCRIPTOR.VERSION),
        record.get(TENANT_PLUGIN.STATUS),
        record.get("config_json_string", String.class),
        record.get("manifest_json_string", String.class),
        record.get("capabilities_json_string", String.class),
        record.get(TENANT_PLUGIN.ENABLED_BY),
        record.get(TENANT_PLUGIN.ENABLED_AT),
        record.get(TENANT_PLUGIN.DISABLED_BY),
        record.get(TENANT_PLUGIN.DISABLED_AT),
        record.get(TENANT_PLUGIN.CREATED_AT),
        record.get(TENANT_PLUGIN.UPDATED_AT));
  }

  private TenantPluginToolPolicyRecord toToolPolicyRecord(Record record) {
    return new TenantPluginToolPolicyRecord(
        record.get(TENANT_PLUGIN_TOOL_POLICY.ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.STATUS),
        record.get(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL),
        record.get(TENANT_PLUGIN_TOOL_POLICY.CREATED_BY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.CREATED_AT),
        record.get(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT));
  }
}
```

---

# 12. Builtin Plugin Seeder

## 12.1 `BuiltinPluginSeeder.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/BuiltinPluginSeeder.java
```

```java
package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BuiltinPluginSeeder implements ApplicationRunner {
  private final PluginRepository repository;
  private final PluginJson json;
  private final PluginManifestValidator validator;

  public BuiltinPluginSeeder(
      PluginRepository repository,
      PluginJson json,
      PluginManifestValidator validator) {
    this.repository = repository;
    this.json = json;
    this.validator = validator;
  }

  @Override
  public void run(ApplicationArguments args) {
    registerIncidentCopilot();
  }

  private void registerIncidentCopilot() {
    String manifest =
        json.write(
            Map.of(
                "frontend",
                Map.of(
                    "contributions",
                    java.util.List.of(
                        Map.of(
                            "extensionPoint",
                            "incident.detail.sidebar",
                            "contributionId",
                            "incident-copilot-panel",
                            "title",
                            "Incident Copilot",
                            "componentKey",
                            "builtin.incidentCopilotPanel",
                            "order",
                            100,
                            "props",
                            Map.of()))),
                "agentTools",
                java.util.List.of(
                    Map.of(
                        "toolKey",
                        "knowledge.search_cases",
                        "riskLevel",
                        "low",
                        "description",
                        "Search similar incident cases"),
                    Map.of(
                        "toolKey",
                        "memory.search",
                        "riskLevel",
                        "low",
                        "description",
                        "Search tenant agent memory"))));

    validator.validateManifest(manifest);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            stableId("builtin.incident-copilot", "0.1.0"),
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "Built-in incident assistant plugin",
            "builtin",
            "active",
            manifest,
            json.write(
                Map.of(
                    "extensionPoints",
                    java.util.List.of("incident.detail.sidebar", "agent.tool"),
                    "agentTools",
                    java.util.List.of("knowledge.search_cases", "memory.search"))),
            "system"));
  }

  private String stableId(String pluginKey, String version) {
    return "plg_" + UUID.nameUUIDFromBytes((pluginKey + ":" + version).getBytes()).toString().replace("-", "");
  }
}
```

---

# 13. Service

## 13.1 `PluginService.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginService.java
```

```java
package io.aegisops.plugin;

import io.aegisops.common.exception.AppException;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.PluginDescriptorResponse;
import io.aegisops.plugin.dto.PluginDisableRequest;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import io.aegisops.plugin.dto.TenantFrontendManifestResponse;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginResponse;
import io.aegisops.plugin.dto.TenantPluginToolPolicyResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginService {
  private final PluginRepository repository;
  private final PluginExtensionPointRegistry extensionPointRegistry;
  private final PluginManifestValidator validator;
  private final PluginJson json;

  public PluginService(
      PluginRepository repository,
      PluginExtensionPointRegistry extensionPointRegistry,
      PluginManifestValidator validator,
      PluginJson json) {
    this.repository = repository;
    this.extensionPointRegistry = extensionPointRegistry;
    this.validator = validator;
    this.json = json;
  }

  public List<PluginExtensionPointResponse> listExtensionPoints() {
    return extensionPointRegistry.list();
  }

  public List<PluginDescriptorResponse> listPlugins() {
    return repository.listPlugins().stream().map(this::toPluginResponse).toList();
  }

  public PluginDescriptorResponse getPlugin(String pluginId) {
    return toPluginResponse(loadPlugin(pluginId));
  }

  public List<TenantPluginResponse> listTenantPlugins(String tenantId) {
    return repository.listTenantPlugins(tenantId).stream().map(this::toTenantPluginResponse).toList();
  }

  @Transactional
  public TenantPluginResponse enablePlugin(
      String tenantId,
      String pluginId,
      PluginEnableRequest request) {
    PluginDescriptorRecord plugin = loadPlugin(pluginId);
    ensurePluginActive(plugin);

    validator.validateManifest(plugin.manifestJson());

    String enabledBy = blankToDefault(request == null ? null : request.enabledBy(), "system");
    String configJson = normalizeConfigJson(request == null ? null : request.configJson());

    String tenantPluginId = repository.enablePlugin(tenantId, pluginId, configJson, enabledBy);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);

    allowDefaultTools(tenantId, tenantPlugin, plugin, enabledBy);

    repository.createEvent(
        newId("ple"),
        tenantId,
        plugin.id(),
        tenantPlugin.id(),
        "plugin_enabled",
        "Plugin enabled for tenant",
        enabledBy,
        "{}");

    return toTenantPluginResponse(tenantPlugin);
  }

  @Transactional
  public TenantPluginResponse disablePlugin(
      String tenantId,
      String tenantPluginId,
      PluginDisableRequest request) {
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);
    String disabledBy = blankToDefault(request == null ? null : request.disabledBy(), "system");

    boolean updated = repository.disablePlugin(tenantId, tenantPluginId, disabledBy);
    if (!updated) {
      throw new AppException("PLUGIN_DISABLE_FAILED", "Plugin was not disabled");
    }

    repository.createEvent(
        newId("ple"),
        tenantId,
        tenantPlugin.pluginId(),
        tenantPlugin.id(),
        "plugin_disabled",
        "Plugin disabled for tenant",
        disabledBy,
        "{}");

    return toTenantPluginResponse(loadTenantPlugin(tenantId, tenantPluginId));
  }

  public TenantFrontendManifestResponse frontendManifest(String tenantId) {
    List<TenantPluginRecord> enabled =
        repository.listTenantPlugins(tenantId).stream()
            .filter(item -> "enabled".equals(item.status()))
            .toList();

    List<String> keys = enabled.stream().map(TenantPluginRecord::pluginKey).toList();
    List<Object> contributions = new ArrayList<>();

    for (TenantPluginRecord plugin : enabled) {
      Map<String, Object> manifest = json.readMap(plugin.manifestJson());
      Object frontend = manifest.get("frontend");
      if (!(frontend instanceof Map<?, ?> frontendMap)) {
        continue;
      }

      Object rawContributions = frontendMap.get("contributions");
      if (rawContributions instanceof List<?> list) {
        contributions.addAll(list);
      }
    }

    repository.createEvent(
        newId("ple"),
        tenantId,
        null,
        null,
        "manifest_requested",
        "Tenant frontend plugin manifest requested",
        "system",
        json.write(Map.of("pluginCount", enabled.size(), "contributionCount", contributions.size())));

    return new TenantFrontendManifestResponse(tenantId, keys, contributions);
  }

  public List<TenantPluginToolPolicyResponse> listToolPolicies(String tenantId) {
    return repository.listTenantToolPolicies(tenantId).stream().map(this::toToolPolicyResponse).toList();
  }

  @Transactional
  public TenantPluginToolPolicyResponse allowTool(
      String tenantId,
      String tenantPluginId,
      String toolKey,
      String actor) {
    validator.requireAllowedToolKey(toolKey);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);
    ensureTenantPluginEnabled(tenantPlugin);

    repository.upsertToolPolicy(
        newId("tptp"),
        tenantId,
        tenantPlugin.id(),
        tenantPlugin.pluginId(),
        toolKey,
        "allowed",
        riskLevelForTool(tenantPlugin, toolKey),
        blankToDefault(actor, "system"));

    repository.createEvent(
        newId("ple"),
        tenantId,
        tenantPlugin.pluginId(),
        tenantPlugin.id(),
        "tool_allowed",
        "Plugin tool allowed",
        blankToDefault(actor, "system"),
        json.write(Map.of("toolKey", toolKey)));

    return repository
        .findAllowedToolPolicy(tenantId, toolKey)
        .map(this::toToolPolicyResponse)
        .orElseThrow(() -> new AppException("PLUGIN_TOOL_POLICY_NOT_FOUND", "Tool policy not found"));
  }

  @Transactional
  public TenantPluginToolPolicyResponse denyTool(
      String tenantId,
      String tenantPluginId,
      String toolKey,
      String actor) {
    validator.requireAllowedToolKey(toolKey);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);

    repository.upsertToolPolicy(
        newId("tptp"),
        tenantId,
        tenantPlugin.id(),
        tenantPlugin.pluginId(),
        toolKey,
        "denied",
        riskLevelForTool(tenantPlugin, toolKey),
        blankToDefault(actor, "system"));

    repository.createEvent(
        newId("ple"),
        tenantId,
        tenantPlugin.pluginId(),
        tenantPlugin.id(),
        "tool_denied",
        "Plugin tool denied",
        blankToDefault(actor, "system"),
        json.write(Map.of("toolKey", toolKey)));

    return repository.listTenantToolPolicies(tenantId).stream()
        .filter(item -> item.tenantPluginId().equals(tenantPluginId) && item.toolKey().equals(toolKey))
        .findFirst()
        .map(this::toToolPolicyResponse)
        .orElseThrow(() -> new AppException("PLUGIN_TOOL_POLICY_NOT_FOUND", "Tool policy not found"));
  }

  public AgentToolAuthorizeResponse authorizeTool(AgentToolAuthorizeRequest request) {
    if (request == null || request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("PLUGIN_TOOL_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.toolKey() == null || request.toolKey().isBlank()) {
      throw new AppException("PLUGIN_TOOL_KEY_REQUIRED", "Tool key is required");
    }

    validator.requireAllowedToolKey(request.toolKey());

    boolean allowed =
        repository.findAllowedToolPolicy(request.tenantId(), request.toolKey()).isPresent();

    if (allowed) {
      repository.createEvent(
          newId("ple"),
          request.tenantId(),
          null,
          null,
          "tool_authorized",
          "Agent tool authorized",
          "agent",
          json.write(Map.of("toolKey", request.toolKey())));
      return new AgentToolAuthorizeResponse(true, request.toolKey(), "allowed by tenant plugin policy");
    }

    repository.createEvent(
        newId("ple"),
        request.tenantId(),
        null,
        null,
        "tool_denied_by_policy",
        "Agent tool denied by plugin policy",
        "agent",
        json.write(Map.of("toolKey", request.toolKey())));

    return new AgentToolAuthorizeResponse(false, request.toolKey(), "not allowed by tenant plugin policy");
  }

  private void allowDefaultTools(
      String tenantId,
      TenantPluginRecord tenantPlugin,
      PluginDescriptorRecord plugin,
      String actor) {
    Map<String, Object> manifest = json.readMap(plugin.manifestJson());
    Object rawTools = manifest.get("agentTools");
    if (!(rawTools instanceof List<?> tools)) {
      return;
    }

    for (Object item : tools) {
      if (!(item instanceof Map<?, ?> tool)) {
        continue;
      }

      String toolKey = String.valueOf(tool.get("toolKey"));
      validator.requireAllowedToolKey(toolKey);

      repository.upsertToolPolicy(
          newId("tptp"),
          tenantId,
          tenantPlugin.id(),
          plugin.id(),
          toolKey,
          "allowed",
          String.valueOf(tool.getOrDefault("riskLevel", "low")),
          actor);
    }
  }

  private String riskLevelForTool(TenantPluginRecord tenantPlugin, String toolKey) {
    Map<String, Object> manifest = json.readMap(tenantPlugin.manifestJson());
    Object rawTools = manifest.get("agentTools");
    if (!(rawTools instanceof List<?> tools)) {
      return "low";
    }

    for (Object item : tools) {
      if (item instanceof Map<?, ?> tool && toolKey.equals(String.valueOf(tool.get("toolKey")))) {
        String risk = String.valueOf(tool.getOrDefault("riskLevel", "low"));
        return List.of("low", "medium", "high", "critical").contains(risk) ? risk : "low";
      }
    }

    return "low";
  }

  private PluginDescriptorRecord loadPlugin(String pluginId) {
    return repository
        .findPlugin(pluginId)
        .orElseThrow(() -> new AppException("PLUGIN_NOT_FOUND", "Plugin not found"));
  }

  private TenantPluginRecord loadTenantPlugin(String tenantId, String tenantPluginId) {
    return repository
        .findTenantPlugin(tenantId, tenantPluginId)
        .orElseThrow(() -> new AppException("TENANT_PLUGIN_NOT_FOUND", "Tenant plugin not found"));
  }

  private void ensurePluginActive(PluginDescriptorRecord plugin) {
    if (!"active".equals(plugin.status())) {
      throw new AppException("PLUGIN_NOT_ACTIVE", "Plugin is not active");
    }
  }

  private void ensureTenantPluginEnabled(TenantPluginRecord plugin) {
    if (!"enabled".equals(plugin.status())) {
      throw new AppException("TENANT_PLUGIN_DISABLED", "Tenant plugin is disabled");
    }
  }

  private PluginDescriptorResponse toPluginResponse(PluginDescriptorRecord record) {
    return new PluginDescriptorResponse(
        record.id(),
        record.pluginKey(),
        record.name(),
        record.version(),
        record.description(),
        record.provider(),
        record.status(),
        record.manifestJson(),
        record.capabilitiesJson(),
        record.createdAt(),
        record.updatedAt());
  }

  private TenantPluginResponse toTenantPluginResponse(TenantPluginRecord record) {
    return new TenantPluginResponse(
        record.id(),
        record.pluginId(),
        record.pluginKey(),
        record.name(),
        record.version(),
        record.status(),
        record.configJson(),
        record.enabledAt(),
        record.disabledAt());
  }

  private TenantPluginToolPolicyResponse toToolPolicyResponse(TenantPluginToolPolicyRecord record) {
    return new TenantPluginToolPolicyResponse(
        record.id(),
        record.tenantPluginId(),
        record.pluginId(),
        record.pluginKey(),
        record.toolKey(),
        record.status(),
        record.riskLevel(),
        record.createdAt(),
        record.updatedAt());
  }

  private String normalizeConfigJson(String value) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    json.readMap(value);
    return value;
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

# 14. Controller

## 14.1 `PluginController.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginController.java
```

```java
package io.aegisops.plugin;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.plugin.dto.PluginDescriptorResponse;
import io.aegisops.plugin.dto.PluginDisableRequest;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import io.aegisops.plugin.dto.TenantFrontendManifestResponse;
import io.aegisops.plugin.dto.TenantPluginResponse;
import io.aegisops.plugin.dto.TenantPluginToolPolicyResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class PluginController {
  private final PluginService service;

  public PluginController(PluginService service) {
    this.service = service;
  }

  @GetMapping("/api/plugin-extension-points")
  public ApiResponse<List<PluginExtensionPointResponse>> extensionPoints() {
    return ApiResponse.ok(service.listExtensionPoints());
  }

  @GetMapping("/api/plugins")
  public ApiResponse<List<PluginDescriptorResponse>> listPlugins() {
    return ApiResponse.ok(service.listPlugins());
  }

  @GetMapping("/api/plugins/{pluginId}")
  public ApiResponse<PluginDescriptorResponse> getPlugin(@PathVariable String pluginId) {
    return ApiResponse.ok(service.getPlugin(pluginId));
  }

  @GetMapping("/api/tenant/plugins")
  public ApiResponse<List<TenantPluginResponse>> listTenantPlugins() {
    return ApiResponse.ok(service.listTenantPlugins(TenantContext.requireTenantId()));
  }

  @PostMapping("/api/plugins/{pluginId}/enable")
  public ApiResponse<TenantPluginResponse> enable(
      @PathVariable String pluginId,
      @RequestBody(required = false) PluginEnableRequest request) {
    return ApiResponse.ok(service.enablePlugin(TenantContext.requireTenantId(), pluginId, request));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/disable")
  public ApiResponse<TenantPluginResponse> disable(
      @PathVariable String tenantPluginId,
      @RequestBody(required = false) PluginDisableRequest request) {
    return ApiResponse.ok(service.disablePlugin(TenantContext.requireTenantId(), tenantPluginId, request));
  }

  @GetMapping("/api/tenant/plugins/frontend-manifest")
  public ApiResponse<TenantFrontendManifestResponse> frontendManifest() {
    return ApiResponse.ok(service.frontendManifest(TenantContext.requireTenantId()));
  }

  @GetMapping("/api/tenant/plugins/tools")
  public ApiResponse<List<TenantPluginToolPolicyResponse>> toolPolicies() {
    return ApiResponse.ok(service.listToolPolicies(TenantContext.requireTenantId()));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/allow")
  public ApiResponse<TenantPluginToolPolicyResponse> allowTool(
      @PathVariable String tenantPluginId,
      @PathVariable String toolKey,
      @RequestParam(defaultValue = "system") String actor) {
    return ApiResponse.ok(
        service.allowTool(TenantContext.requireTenantId(), tenantPluginId, toolKey, actor));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/deny")
  public ApiResponse<TenantPluginToolPolicyResponse> denyTool(
      @PathVariable String tenantPluginId,
      @PathVariable String toolKey,
      @RequestParam(defaultValue = "system") String actor) {
    return ApiResponse.ok(
        service.denyTool(TenantContext.requireTenantId(), tenantPluginId, toolKey, actor));
  }
}
```

---

## 14.2 `InternalAgentPluginController.java`

路径：

```txt
modules/aiops-plugin/src/main/java/io/aegisops/plugin/InternalAgentPluginController.java
```

```java
package io.aegisops.plugin;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalAgentPluginController {
  private final PluginService service;

  public InternalAgentPluginController(PluginService service) {
    this.service = service;
  }

  @PostMapping("/internal/agent/plugins/tools/authorize")
  public ApiResponse<AgentToolAuthorizeResponse> authorize(
      @RequestBody AgentToolAuthorizeRequest request) {
    return ApiResponse.ok(service.authorizeTool(request));
  }
}
```

---

# 15. Python Agent Tool Policy

> 路径必须按当前实际项目：
> `apps/aiops-agent/src/aiops_agent/...`

---

## 15.1 `workflow/tools/plugin_policy_client.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/plugin_policy_client.py
```

```python
"""Plugin tool allowlist client for internal Java policy checks."""

from __future__ import annotations

from typing import Any

import httpx

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.internal_auth import internal_tool_headers


class PluginToolPolicyError(Exception):
    pass


class PluginToolDeniedError(PluginToolPolicyError):
    def __init__(self, tool_key: str, reason: str):
        super().__init__(reason)
        self.tool_key = tool_key
        self.reason = reason


class PluginPolicyClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None):
        self.base_url = (base_url or settings.memory_api_base_url).rstrip("/")
        self.timeout = timeout or settings.request_timeout_seconds

    async def authorize_tool(self, tenant_id: str, tool_key: str) -> bool:
        url = f"{self.base_url}/internal/agent/plugins/tools/authorize"
        body = {
            "tenantId": tenant_id,
            "toolKey": tool_key,
        }

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                url,
                json=body,
                headers=internal_tool_headers(tenant_id),
            )
            response.raise_for_status()
            payload = response.json()

        data = self._extract_data(payload)
        allowed = bool(data.get("allowed", False))
        reason = str(data.get("reason") or "")

        if not allowed:
            raise PluginToolDeniedError(tool_key, reason or "tool denied by plugin policy")

        return True

    def _extract_data(self, payload: Any) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise PluginToolPolicyError("plugin policy response must be object")
        data = payload.get("data", payload)
        if not isinstance(data, dict):
            raise PluginToolPolicyError("plugin policy response data must be object")
        return data
```

---

## 15.2 `workflow/tools/tool_keys.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/tool_keys.py
```

```python
"""Built-in agent tool keys used by the plugin allowlist."""

EVIDENCE_FETCH = "evidence.fetch"
KNOWLEDGE_SEARCH_CASES = "knowledge.search_cases"
CHECKPOINT_CREATE = "checkpoint.create"
CHECKPOINT_GET = "checkpoint.get"
MEMORY_SEARCH = "memory.search"
MEMORY_CREATE = "memory.create"
```

---

## 15.3 `workflow/tools/plugin_policy_guard.py`

路径：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/plugin_policy_guard.py
```

```python
"""Guard for optional plugin tool allowlist enforcement."""

from __future__ import annotations

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.plugin_policy_client import PluginPolicyClient


class PluginToolPolicyGuard:
    def __init__(self, client: PluginPolicyClient | None = None, enabled: bool | None = None):
        self.client = client or PluginPolicyClient()
        self.enabled = settings.workflow_plugin_tool_policy_enabled if enabled is None else enabled

    async def require_allowed(self, tenant_id: str, tool_key: str) -> None:
        if not self.enabled:
            return
        await self.client.authorize_tool(tenant_id, tool_key)
```

---

## 15.4 修改 Python Settings

路径：

```txt
apps/aiops-agent/src/aiops_agent/settings.py
```

追加：

```python
workflow_plugin_tool_policy_enabled: bool = False
```

建议默认 false，避免本地开发没有启用插件时，工具全被 deny。SaaS 环境再打开：

```env
AIOPS_AGENT_WORKFLOW_PLUGIN_TOOL_POLICY_ENABLED=true
```

---

## 15.5 Tool Client 接入点

在这些文件中加入 guard：

```txt
apps/aiops-agent/src/aiops_agent/workflow/tools/evidence_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/knowledge_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/checkpoint_client.py
apps/aiops-agent/src/aiops_agent/workflow/tools/memory_client.py
```

### EvidenceClient 关键修改

```python
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import EVIDENCE_FETCH

class EvidenceClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None, policy_guard: PluginToolPolicyGuard | None = None):
        ...
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def fetch_evidence(self, tenant_id: str, incident_id: str):
        await self.policy_guard.require_allowed(tenant_id, EVIDENCE_FETCH)
        ...
```

### KnowledgeClient 关键修改

```python
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import KNOWLEDGE_SEARCH_CASES

class KnowledgeClient:
    def __init__(self, base_url: str | None = None, timeout: float | None = None, policy_guard: PluginToolPolicyGuard | None = None):
        ...
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def search_cases(...):
        await self.policy_guard.require_allowed(tenant_id, KNOWLEDGE_SEARCH_CASES)
        ...
```

### CheckpointClient 关键修改

```python
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import CHECKPOINT_CREATE, CHECKPOINT_GET

class CheckpointClient:
    def __init__(..., policy_guard: PluginToolPolicyGuard | None = None):
        ...
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def create_checkpoint(...):
        await self.policy_guard.require_allowed(tenant_id, CHECKPOINT_CREATE)
        ...

    async def get_checkpoint(...):
        await self.policy_guard.require_allowed(tenant_id, CHECKPOINT_GET)
        ...
```

### MemoryClient 关键修改

```python
from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard
from aiops_agent.workflow.tools.tool_keys import MEMORY_SEARCH, MEMORY_CREATE

class MemoryClient:
    def __init__(..., policy_guard: PluginToolPolicyGuard | None = None):
        ...
        self.policy_guard = policy_guard or PluginToolPolicyGuard()

    async def search_memories(...):
        await self.policy_guard.require_allowed(tenant_id, MEMORY_SEARCH)
        ...

    async def create_memory(...):
        await self.policy_guard.require_allowed(tenant_id, MEMORY_CREATE)
        ...
```

---

# 16. Java 单元测试

## 16.1 `PluginManifestValidatorTest.java`

路径：

```txt
modules/aiops-plugin/src/test/java/io/aegisops/plugin/PluginManifestValidatorTest.java
```

```java
package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

class PluginManifestValidatorTest {
  private final PluginManifestValidator validator =
      new PluginManifestValidator(new PluginJson(new ObjectMapper()));

  @Test
  void allowSafeManifest() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "incident.detail.sidebar",
                "contributionId": "incident-copilot-panel",
                "title": "Incident Copilot",
                "componentKey": "builtin.incidentCopilotPanel",
                "order": 100,
                "props": {}
              }
            ]
          },
          "agentTools": [
            {
              "toolKey": "knowledge.search_cases",
              "riskLevel": "low"
            }
          ]
        }
        """;

    assertDoesNotThrow(() -> validator.validateManifest(manifest));
  }

  @Test
  void rejectRemoteEntry() {
    String manifest = """
        {"remoteEntry": "https://evil.example/plugin.js"}
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectInvalidExtensionPoint() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "unknown.point",
                "contributionId": "bad",
                "componentKey": "builtin.bad"
              }
            ]
          }
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectUnknownToolKey() {
    String manifest =
        """
        {
          "agentTools": [
            {"toolKey": "ssh.exec", "riskLevel": "critical"}
          ]
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }
}
```

---

## 16.2 `PluginServiceTest.java`

路径：

```txt
modules/aiops-plugin/src/test/java/io/aegisops/plugin/PluginServiceTest.java
```

```java
package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class PluginServiceTest {
  @Test
  void enablePluginCreatesToolPoliciesAndAuthorizeTool() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            """
            {
              "frontend": {
                "contributions": [
                  {
                    "extensionPoint": "incident.detail.sidebar",
                    "contributionId": "panel",
                    "componentKey": "builtin.panel"
                  }
                ]
              },
              "agentTools": [
                {"toolKey": "knowledge.search_cases", "riskLevel": "low"}
              ]
            }
            """,
            "{}",
            "system"));

    var enabled =
        service.enablePlugin(
            "tenant_1",
            "plg_1",
            new PluginEnableRequest("alice", "{}"));

    assertEquals("enabled", enabled.status());

    var auth =
        service.authorizeTool(
            new AgentToolAuthorizeRequest("tenant_1", "knowledge.search_cases"));

    assertTrue(auth.allowed());
  }

  @Test
  void authorizeDeniedWhenNoPolicy() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    var auth =
        service.authorizeTool(
            new AgentToolAuthorizeRequest("tenant_1", "memory.search"));

    assertFalse(auth.allowed());
  }

  private static class FakePluginRepository implements PluginRepository {
    final Map<String, PluginDescriptorRecord> plugins = new HashMap<>();
    final Map<String, TenantPluginRecord> tenantPlugins = new HashMap<>();
    final List<TenantPluginToolPolicyRecord> policies = new ArrayList<>();

    @Override
    public void upsertDescriptor(PluginDescriptorCreateCommand command) {
      plugins.put(
          command.id(),
          new PluginDescriptorRecord(
              command.id(),
              command.pluginKey(),
              command.name(),
              command.version(),
              command.description(),
              command.provider(),
              command.status(),
              command.manifestJson(),
              command.capabilitiesJson(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<PluginDescriptorRecord> findPlugin(String pluginId) {
      return Optional.ofNullable(plugins.get(pluginId));
    }

    @Override
    public Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version) {
      return plugins.values().stream()
          .filter(item -> item.pluginKey().equals(pluginKey) && item.version().equals(version))
          .findFirst();
    }

    @Override
    public List<PluginDescriptorRecord> listPlugins() {
      return new ArrayList<>(plugins.values());
    }

    @Override
    public Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId) {
      return Optional.ofNullable(tenantPlugins.get(tenantPluginId))
          .filter(item -> item.tenantId().equals(tenantId));
    }

    @Override
    public Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId) {
      return tenantPlugins.values().stream()
          .filter(item -> item.tenantId().equals(tenantId) && item.pluginId().equals(pluginId))
          .findFirst();
    }

    @Override
    public List<TenantPluginRecord> listTenantPlugins(String tenantId) {
      return tenantPlugins.values().stream()
          .filter(item -> item.tenantId().equals(tenantId))
          .toList();
    }

    @Override
    public String enablePlugin(String tenantId, String pluginId, String configJson, String enabledBy) {
      PluginDescriptorRecord plugin = plugins.get(pluginId);
      String id = "tplg_1";
      tenantPlugins.put(
          id,
          new TenantPluginRecord(
              id,
              tenantId,
              pluginId,
              plugin.pluginKey(),
              plugin.name(),
              plugin.version(),
              "enabled",
              configJson,
              plugin.manifestJson(),
              plugin.capabilitiesJson(),
              enabledBy,
              OffsetDateTime.now(),
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
      return id;
    }

    @Override
    public boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy) {
      return true;
    }

    @Override
    public List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId) {
      return policies.stream().filter(item -> item.tenantId().equals(tenantId)).toList();
    }

    @Override
    public Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(String tenantId, String toolKey) {
      return policies.stream()
          .filter(item -> item.tenantId().equals(tenantId))
          .filter(item -> item.toolKey().equals(toolKey))
          .filter(item -> item.status().equals("allowed"))
          .findFirst();
    }

    @Override
    public void upsertToolPolicy(
        String id,
        String tenantId,
        String tenantPluginId,
        String pluginId,
        String toolKey,
        String status,
        String riskLevel,
        String createdBy) {
      PluginDescriptorRecord plugin = plugins.get(pluginId);
      policies.add(
          new TenantPluginToolPolicyRecord(
              id,
              tenantId,
              tenantPluginId,
              pluginId,
              plugin.pluginKey(),
              toolKey,
              status,
              riskLevel,
              createdBy,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public void createEvent(
        String id,
        String tenantId,
        String pluginId,
        String tenantPluginId,
        String eventType,
        String summary,
        String actor,
        String metadataJson) {}
  }
}
```

---

## 16.3 `JooqPluginRepositoryGeneratedSqlTest.java`

路径：

```txt
modules/aiops-plugin/src/test/java/io/aegisops/plugin/JooqPluginRepositoryGeneratedSqlTest.java
```

```java
package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.persistence.JooqTestSupport;
import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import java.util.List;
import org.jooq.Query;
import org.junit.jupiter.api.Test;

class JooqPluginRepositoryGeneratedSqlTest {
  @Test
  void repositoryUsesPluginTables() {
    var dsl = JooqTestSupport.dsl();
    var repository = new JooqPluginRepository(dsl);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.test",
            "Test",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            "{}",
            "{}",
            "system"));

    repository.createEvent(
        "ple_1",
        "tenant_1",
        "plg_1",
        null,
        "plugin_registered",
        "registered",
        "system",
        "{}");

    String sql =
        dsl.queries().stream()
            .map(Query::getSQL)
            .reduce("", (a, b) -> a + "\n" + b)
            .toLowerCase();

    assertTrue(sql.contains("plugin_descriptor"));
    assertTrue(sql.contains("plugin_event"));
  }
}
```

---

# 17. Python 单元测试

## 17.1 `test_plugin_policy_client.py`

路径：

```txt
apps/aiops-agent/tests/test_plugin_policy_client.py
```

```python
from __future__ import annotations

import pytest
import respx
from httpx import Response

from aiops_agent.settings import settings
from aiops_agent.workflow.tools.plugin_policy_client import (
    PluginPolicyClient,
    PluginToolDeniedError,
)
from aiops_agent.workflow.tools.internal_auth import (
    HEADER_INTERNAL_AGENT_TOKEN,
    HEADER_TENANT_ID,
)


@pytest.mark.asyncio
@respx.mock
async def test_plugin_policy_client_authorizes_tool(monkeypatch):
    monkeypatch.setattr(settings, "internal_agent_token", "secret-token")

    route = respx.post("http://java/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "allowed": True,
                    "toolKey": "memory.search",
                    "reason": "allowed",
                }
            },
        )
    )

    client = PluginPolicyClient(base_url="http://java")

    allowed = await client.authorize_tool("tenant_1", "memory.search")

    assert allowed is True
    assert route.calls[0].request.headers[HEADER_TENANT_ID] == "tenant_1"
    assert route.calls[0].request.headers[HEADER_INTERNAL_AGENT_TOKEN] == "secret-token"


@pytest.mark.asyncio
@respx.mock
async def test_plugin_policy_client_denies_tool():
    respx.post("http://java/internal/agent/plugins/tools/authorize").mock(
        return_value=Response(
            200,
            json={
                "data": {
                    "allowed": False,
                    "toolKey": "memory.create",
                    "reason": "not allowed",
                }
            },
        )
    )

    client = PluginPolicyClient(base_url="http://java")

    with pytest.raises(PluginToolDeniedError):
        await client.authorize_tool("tenant_1", "memory.create")
```

---

## 17.2 `test_plugin_policy_guard.py`

路径：

```txt
apps/aiops-agent/tests/test_plugin_policy_guard.py
```

```python
from __future__ import annotations

import pytest

from aiops_agent.workflow.tools.plugin_policy_guard import PluginToolPolicyGuard


class FakePolicyClient:
    def __init__(self):
        self.called = False

    async def authorize_tool(self, tenant_id: str, tool_key: str) -> bool:
        self.called = True
        return True


@pytest.mark.asyncio
async def test_guard_skips_when_disabled():
    client = FakePolicyClient()
    guard = PluginToolPolicyGuard(client=client, enabled=False)

    await guard.require_allowed("tenant_1", "memory.search")

    assert client.called is False


@pytest.mark.asyncio
async def test_guard_calls_client_when_enabled():
    client = FakePolicyClient()
    guard = PluginToolPolicyGuard(client=client, enabled=True)

    await guard.require_allowed("tenant_1", "memory.search")

    assert client.called is True
```

---

# 18. 文档

路径：

```txt
docs/mvp/design/phase8.1-plugin-system.md
```

```md
# Phase8.1 Plugin System

## 目标

提供安全插件系统 MVP：

- Plugin Descriptor
- Extension Point Registry
- Tenant Plugin Enablement
- Frontend Manifest
- Agent Tool Allowlist

## 不做

- 不动态加载 jar
- 不加载远程 JS
- 不执行插件脚本
- 不直接调用 Runner
- 不直接执行 Webhook / Ansible / SSH
- 不做 Marketplace

## Extension Points

- incident.detail.sidebar
- incident.detail.action
- dashboard.card
- settings.page
- agent.tool

## Built-in Tool Keys

- evidence.fetch
- knowledge.search_cases
- checkpoint.create
- checkpoint.get
- memory.search
- memory.create

## API

Public:

- GET /api/plugin-extension-points
- GET /api/plugins
- GET /api/plugins/{pluginId}
- GET /api/tenant/plugins
- POST /api/plugins/{pluginId}/enable
- POST /api/tenant/plugins/{tenantPluginId}/disable
- GET /api/tenant/plugins/frontend-manifest
- GET /api/tenant/plugins/tools
- POST /api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/allow
- POST /api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/deny

Internal:

- POST /internal/agent/plugins/tools/authorize

## 安全边界

插件只声明能力。
插件不能加载远程代码。
插件不能执行脚本。
插件不能直接执行自动化。
Agent Tool 需要租户启用插件并允许 tool key 后才能调用。

## 验收标准

1. 插件描述符可注册。
2. 租户可启用插件。
3. 租户可禁用插件。
4. 前端可获取 tenant frontend manifest。
5. Agent 可查询 tool 是否允许。
6. 未 allow 的 tool 返回 denied。
7. manifest 禁止 remoteEntry/script/html。
8. 不新增 Runner 执行能力。
```

---

# 19. 验证命令

## Java

```powershell
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-plugin -am test
mvn -pl apps/aiops-server -am test
```

---

## Python

```bash
cd apps/aiops-agent
pip install -e ".[test]"
pytest -q
ruff check .
```

---

# 20. 验收标准

```txt
1. plugin_descriptor 表存在。
2. tenant_plugin 表存在。
3. tenant_plugin_tool_policy 表存在。
4. plugin_event 表存在。
5. BuiltinPluginSeeder 能注册 builtin.incident-copilot。
6. /api/plugin-extension-points 返回固定扩展点。
7. /api/plugins 返回插件列表。
8. /api/plugins/{pluginId}/enable 可启用租户插件。
9. /api/tenant/plugins/frontend-manifest 返回 contributions。
10. /internal/agent/plugins/tools/authorize 对 allow tool 返回 allowed=true。
11. 未 allow tool 返回 allowed=false。
12. Python PluginPolicyClient 会带 internal token。
13. Python PluginToolPolicyGuard disabled 时不影响本地开发。
14. Python PluginToolPolicyGuard enabled 时会调用 Java authorize。
15. manifest 拒绝 remoteEntry。
16. manifest 拒绝未知 extensionPoint。
17. manifest 拒绝未知 toolKey。
18. 不新增 execution。
19. 不调 runner。
20. 不执行 webhook / ansible / ssh。
```

---

# 21. 建议提交信息

```txt
feat(plugin): add safe tenant plugin system
```

---

# 22. 下一步 Phase8.2

Phase8.1 完成后进入：

```txt
Phase8.2 Private Deployment / Helm / Offline Package
```

Phase8.2 做：

```txt
1. Docker image hardening
2. Helm chart
3. values.yaml
4. private deployment profile
5. offline package
6. init secret scripts
7. production security checklist
```

---
title: Phase 20.6 模板市场与字段级权限
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.6：模板市场与字段级权限

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

### 4.5 V0032：模板市场与字段级权限

```sql
-- V0032__phase20_market_field_policy.sql

create table if not exists work_record.wr_market_package (
    id varchar(64) primary key,
    publisher_tenant_id varchar(64),
    source_template_id varchar(64),
    package_code varchar(96) not null,
    name varchar(160) not null,
    summary varchar(500),
    category varchar(64) not null,
    visibility varchar(24) not null default 'private',
    status varchar(24) not null default 'draft',
    latest_version_id varchar(64),
    install_count bigint not null default 0,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_wr_market_package_code
        unique (package_code),

    constraint ck_wr_market_visibility
        check (visibility in ('private', 'tenant', 'public')),

    constraint ck_wr_market_status
        check (status in ('draft', 'published', 'withdrawn'))
);

create table if not exists work_record.wr_market_package_version (
    id varchar(64) primary key,
    package_id varchar(64) not null
        references work_record.wr_market_package(id)
        on delete cascade,
    version_no integer not null,
    version_name varchar(128),
    package_json jsonb not null,
    checksum varchar(64) not null,
    published_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_market_package_version
        unique (package_id, version_no),

    constraint ck_wr_market_package_json
        check (jsonb_typeof(package_json) = 'object'),

    constraint ck_wr_market_checksum
        check (checksum ~ '^[0-9a-f]{64}$')
);

alter table work_record.wr_market_package
    add constraint fk_wr_market_latest_version
    foreign key (latest_version_id)
    references work_record.wr_market_package_version(id)
    deferrable initially deferred;

create table if not exists work_record.wr_market_install (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    package_id varchar(64) not null
        references work_record.wr_market_package(id),
    package_version_id varchar(64) not null
        references work_record.wr_market_package_version(id),
    installed_template_id varchar(64) not null
        references work_record.wr_template(id),
    installed_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_market_install
        unique (tenant_id, package_version_id, installed_template_id)
);

create table if not exists work_record.wr_field_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    template_version_id varchar(64) not null
        references work_record.wr_template_version(id)
        on delete cascade,
    field_code varchar(64) not null,
    read_roles_json jsonb not null default '[]'::jsonb,
    write_roles_json jsonb not null default '[]'::jsonb,
    mask_mode varchar(24) not null default 'none',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uk_wr_field_policy
        unique (tenant_id, template_version_id, field_code),

    constraint ck_wr_field_policy_read_roles
        check (jsonb_typeof(read_roles_json) = 'array'),

    constraint ck_wr_field_policy_write_roles
        check (jsonb_typeof(write_roles_json) = 'array'),

    constraint ck_wr_field_policy_mask_mode
        check (mask_mode in ('none', 'full', 'partial'))
);

create index if not exists
idx_wr_field_policy_version
on work_record.wr_field_policy(tenant_id, template_version_id);
```

## 13. Phase 20.6：模板市场与字段级权限

### 13.1 Schema 协议升级

历史版本必须继续按原版本展示，所以 Phase 20 不能简单把 `CURRENT_SCHEMA_VERSION` 改为 2 后拒绝 v1。正确规则：

```text
历史读取：支持 v1、v2
新草稿发布：统一规范化为 v2
v1 缺少字段权限：等价于 readRoles=[]、writeRoles=[]、maskMode=none
```

完整替换版本常量：

```java
package io.aegisops.workrecord.application.schema;

public final class WorkRecordSchemaContract {

  public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
  public static final int CURRENT_SCHEMA_VERSION = 2;

  public static final String ROOT_SCHEMA_VERSION_KEY =
      "x-work-record-schema-version";
  public static final String FIELD_EXTENSION_KEY = "x-work-record";

  public static boolean supportedVersion(int version) {
    return version >= MIN_SUPPORTED_SCHEMA_VERSION
        && version <= CURRENT_SCHEMA_VERSION;
  }

  private WorkRecordSchemaContract() {}
}
```

`WorkRecordSchemaValidator.validateRoot()` 的版本判断替换为：

```java
int version =
    root.has(ROOT_SCHEMA_VERSION_KEY)
        ? root.path(ROOT_SCHEMA_VERSION_KEY).asInt()
        : WorkRecordSchemaContract.MIN_SUPPORTED_SCHEMA_VERSION;

if (!WorkRecordSchemaContract.supportedVersion(version)) {
  throw new IllegalArgumentException("unsupported schema version: " + version);
}
```

### 13.2 字段协议

v2 字段扩展：

```json
{
  "x-work-record": {
    "fieldCode": "salary",
    "fieldType": "number",
    "readRoles": ["system_admin", "record_admin"],
    "writeRoles": ["system_admin"],
    "maskMode": "partial",
    "listVisible": false,
    "filterable": false,
    "exportable": false,
    "statistical": true
  }
}
```

字段级权限不是单独的 UI 配置，而是模板版本的一部分。发布时将权限同步到 `wr_field_policy`，历史模板版本的权限不可原地修改。

### 13.3 FieldPolicyDescriptor.java

```java
package io.aegisops.workrecord.domain.model;

import java.util.List;

public record FieldPolicyDescriptor(
    List<String> readRoles,
    List<String> writeRoles,
    String maskMode) {

  public FieldPolicyDescriptor {
    readRoles = readRoles == null ? List.of() : List.copyOf(readRoles);
    writeRoles = writeRoles == null ? List.of() : List.copyOf(writeRoles);
    maskMode = maskMode == null || maskMode.isBlank() ? "none" : maskMode;
  }

  public static FieldPolicyDescriptor unrestricted() {
    return new FieldPolicyDescriptor(List.of(), List.of(), "none");
  }
}
```

`FormFieldDescriptor` 增加最后一个属性：

```java
FieldPolicyDescriptor policy
```

Parser 增加：

```java
private FieldPolicyDescriptor policy(JsonNode ext) {
  return new FieldPolicyDescriptor(
      stringArray(ext.path("readRoles")),
      stringArray(ext.path("writeRoles")),
      textOrDefault(ext, "maskMode", "none"));
}

private List<String> stringArray(JsonNode node) {
  if (!node.isArray()) {
    return List.of();
  }
  List<String> result = new ArrayList<>();
  for (JsonNode item : node) {
    if (item.isTextual() && !item.asText().isBlank()) {
      result.add(item.asText());
    }
  }
  return List.copyOf(result);
}
```

Validator 增加：

```java
private static final Set<String> MASK_MODES =
    Set.of("none", "full", "partial");

public void validateFieldPolicy(JsonNode ext, String schemaPath) {
  validateRoleArray(ext.path("readRoles"), "readRoles", schemaPath);
  validateRoleArray(ext.path("writeRoles"), "writeRoles", schemaPath);

  String maskMode = optionalText(ext, "maskMode", "none");
  if (!MASK_MODES.contains(maskMode)) {
    throw new IllegalArgumentException(
        "unsupported maskMode at " + schemaPath + ": " + maskMode);
  }
}

private void validateRoleArray(JsonNode node, String name, String path) {
  if (node.isMissingNode()) {
    return;
  }
  if (!node.isArray() || node.size() > 20) {
    throw new IllegalArgumentException(name + " must be array with at most 20 roles at " + path);
  }
  for (JsonNode item : node) {
    if (!item.isTextual() || !item.asText().matches("^[a-z][a-z0-9_-]{1,63}$")) {
      throw new IllegalArgumentException("invalid role code in " + name + " at " + path);
    }
  }
}
```

### 13.4 FieldPolicy.java

```java
package io.aegisops.workrecord.extension.domain;

import java.util.List;

public record FieldPolicy(
    String templateVersionId,
    String fieldCode,
    List<String> readRoles,
    List<String> writeRoles,
    MaskMode maskMode) {

  public FieldPolicy {
    readRoles = readRoles == null ? List.of() : List.copyOf(readRoles);
    writeRoles = writeRoles == null ? List.of() : List.copyOf(writeRoles);
  }

  public enum MaskMode {
    NONE,
    FULL,
    PARTIAL;

    public static MaskMode from(String value) {
      return value == null ? NONE : valueOf(value.toUpperCase());
    }
  }
}
```

### 13.5 FieldPolicyRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.List;

public interface FieldPolicyRepository {

  void replaceForVersion(
      String tenantId,
      String templateVersionId,
      List<FieldPolicy> policies);

  List<FieldPolicy> listByVersion(
      String tenantId,
      String templateVersionId);
}
```

### 13.6 FieldPolicyService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class FieldPolicyService {

  private final FieldPolicyRepository repository;
  private final ObjectMapper objectMapper;

  public FieldPolicyService(
      FieldPolicyRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public String filterReadableJson(
      String tenantId,
      String templateVersionId,
      String customDataJson,
      UserPrincipal principal) {
    ObjectNode source = object(customDataJson);
    ObjectNode result = objectMapper.createObjectNode();
    Map<String, FieldPolicy> policies = policies(tenantId, templateVersionId);

    source.fields().forEachRemaining(entry -> {
      FieldReadDecision decision =
          readDecision(policies.get(entry.getKey()), principal);
      if (decision.visible()) {
        result.set(entry.getKey(), mask(entry.getValue(), decision.maskMode()));
      }
    });
    return write(result);
  }

  public void requireWritablePatch(
      String tenantId,
      String templateVersionId,
      String beforeJson,
      String afterJson,
      UserPrincipal principal) {
    ObjectNode before = object(beforeJson);
    ObjectNode after = object(afterJson);
    Map<String, FieldPolicy> policies = policies(tenantId, templateVersionId);

    Set<String> keys = new java.util.HashSet<>();
    before.fieldNames().forEachRemaining(keys::add);
    after.fieldNames().forEachRemaining(keys::add);

    for (String key : keys) {
      JsonNode oldValue = before.get(key);
      JsonNode newValue = after.get(key);
      if (java.util.Objects.equals(oldValue, newValue)) {
        continue;
      }
      if (!canWrite(policies.get(key), principal)) {
        throw new AccessDeniedException("not allowed to write field: " + key);
      }
    }
  }

  public boolean canReadField(
      String tenantId,
      String templateVersionId,
      String fieldCode,
      UserPrincipal principal) {
    return readDecision(
            policies(tenantId, templateVersionId).get(fieldCode),
            principal)
        .visible();
  }

  public boolean canWriteField(
      String tenantId,
      String templateVersionId,
      String fieldCode,
      UserPrincipal principal) {
    return canWrite(
        policies(tenantId, templateVersionId).get(fieldCode),
        principal);
  }

  private Map<String, FieldPolicy> policies(
      String tenantId,
      String versionId) {
    Map<String, FieldPolicy> result = new HashMap<>();
    for (FieldPolicy policy : repository.listByVersion(tenantId, versionId)) {
      result.put(policy.fieldCode(), policy);
    }
    return Map.copyOf(result);
  }

  private FieldReadDecision readDecision(
      FieldPolicy policy,
      UserPrincipal principal) {
    if (policy == null || policy.readRoles().isEmpty()) {
      return new FieldReadDecision(true, FieldPolicy.MaskMode.NONE);
    }
    boolean matched =
        principal != null
            && principal.roles().stream().anyMatch(policy.readRoles()::contains);
    return matched
        ? new FieldReadDecision(true, policy.maskMode())
        : new FieldReadDecision(false, FieldPolicy.MaskMode.FULL);
  }

  private boolean canWrite(FieldPolicy policy, UserPrincipal principal) {
    if (policy == null || policy.writeRoles().isEmpty()) {
      return true;
    }
    return principal != null
        && principal.roles().stream().anyMatch(policy.writeRoles()::contains);
  }

  private JsonNode mask(JsonNode value, FieldPolicy.MaskMode mode) {
    if (mode == FieldPolicy.MaskMode.NONE) {
      return value;
    }
    if (mode == FieldPolicy.MaskMode.FULL) {
      return objectMapper.getNodeFactory().textNode("******");
    }
    if (!value.isTextual()) {
      return objectMapper.getNodeFactory().textNode("***");
    }
    String text = value.asText();
    if (text.length() <= 4) {
      return objectMapper.getNodeFactory().textNode("****");
    }
    return objectMapper.getNodeFactory().textNode(
        text.substring(0, 2) + "****" + text.substring(text.length() - 2));
  }

  private ObjectNode object(String json) {
    try {
      JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("custom data must be object");
      }
      return (ObjectNode) node;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid custom data", ex);
    }
  }

  private String write(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize filtered data", ex);
    }
  }

  public record FieldReadDecision(
      boolean visible,
      FieldPolicy.MaskMode maskMode) {}
}
```

### 13.7 字段权限必须接入的所有路径

```text
GET record detail
record list dynamic columns
record update
record create
filter metadata
filter validator
CSV/XLSX export
Excel import
statistics/workload
AI input builder
record audit detail UI
```

核心改造示例：

```java
public WorkRecord getVisible(
    String tenantId,
    String recordId,
    UserPrincipal user) {
  WorkRecord record = queryService.get(tenantId, recordId, user);
  return record.withCustomDataJson(
      fieldPolicyService.filterReadableJson(
          tenantId,
          record.templateVersionId(),
          record.customDataJson(),
          user));
}
```

当前 `WorkRecord` 是 record，没有 `withCustomDataJson`，应新增静态 mapper：

```java
public final class WorkRecordViews {

  public static WorkRecord customData(
      WorkRecord source,
      String customDataJson) {
    return new WorkRecord(
        source.id(),
        source.tenantId(),
        source.templateId(),
        source.templateVersionId(),
        source.title(),
        source.status(),
        source.ownerId(),
        source.creatorId(),
        source.recordTime(),
        source.builtinDataJson(),
        customDataJson,
        source.rowVersion(),
        source.createdAt(),
        source.updatedAt(),
        source.deletedAt());
  }

  private WorkRecordViews() {}
}
```

更新记录时，在领域校验之前增加：

```java
fieldPolicyService.requireWritablePatch(
    tenantId,
    existing.templateVersionId(),
    existing.customDataJson(),
    effectiveCustomJson,
    user);
```

动态筛选时：

```java
if (!fieldPolicyService.canReadField(
    tenantId,
    templateVersionId,
    filter.fieldCode(),
    user)) {
  throw new AccessDeniedException(
      "not allowed to filter field: " + filter.fieldCode());
}
```

### 13.8 MarketPackageDocument.java

模板市场发布的是不可变包，不是对源模板的实时引用。

```java
package io.aegisops.workrecord.extension.application.model;

import java.util.List;
import java.util.Map;

public record MarketPackageDocument(
    int contractVersion,
    String packageCode,
    String name,
    String description,
    String templateCode,
    String templateName,
    String schemaJson,
    String designerJson,
    List<DictionaryDependency> dictionaries,
    Map<String, Object> metadata) {

  public MarketPackageDocument {
    dictionaries = dictionaries == null ? List.of() : List.copyOf(dictionaries);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public record DictionaryDependency(
      String dictCode,
      String dictName,
      List<DictionaryItem> items) {

    public DictionaryDependency {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record DictionaryItem(
      String label,
      String value,
      String color,
      int sortOrder) {}
}
```

### 13.9 TemplatePackagePort.java

该 Port 放入 `aiops-work-record`，避免市场模块直接操作模板仓库。

```java
package io.aegisops.workrecord.application.port;

public interface TemplatePackagePort {

  TemplateSnapshot exportPublished(
      String tenantId,
      String templateId);

  InstalledTemplate install(
      String tenantId,
      InstallTemplateCommand command,
      String actorId);

  record TemplateSnapshot(
      String templateCode,
      String templateName,
      String description,
      String schemaJson,
      String designerJson) {}

  record InstallTemplateCommand(
      String templateCode,
      String templateName,
      String description,
      String schemaJson,
      String designerJson) {}

  record InstalledTemplate(
      String templateId,
      String templateVersionId) {}
}
```

### 13.10 TemplateMarketService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.TemplatePackagePort;
import io.aegisops.workrecord.extension.application.model.MarketPackageDocument;
import io.aegisops.workrecord.extension.application.port.TemplateMarketRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateMarketService {

  private final TemplateMarketRepository repository;
  private final TemplatePackagePort templates;
  private final MarketDictionaryService dictionaries;
  private final ObjectMapper objectMapper;

  public TemplateMarketService(
      TemplateMarketRepository repository,
      TemplatePackagePort templates,
      MarketDictionaryService dictionaries,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.templates = templates;
    this.dictionaries = dictionaries;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public String publish(
      String tenantId,
      PublishPackage command,
      UserPrincipal user) {
    require(user, "work-record:market:publish");
    TemplatePackagePort.TemplateSnapshot snapshot =
        templates.exportPublished(tenantId, command.templateId());
    MarketPackageDocument document =
        new MarketPackageDocument(
            1,
            command.packageCode(),
            command.name(),
            snapshot.description(),
            snapshot.templateCode(),
            snapshot.templateName(),
            snapshot.schemaJson(),
            snapshot.designerJson(),
            dictionaries.dependencies(tenantId, snapshot.schemaJson()),
            java.util.Map.of("sourceTemplateId", command.templateId()));
    String json = write(document);
    String checksum = sha256(json);
    return repository.publish(
        tenantId,
        command.packageCode(),
        command.name(),
        command.category(),
        command.visibility(),
        json,
        checksum,
        user.id());
  }

  @Transactional
  public TemplatePackagePort.InstalledTemplate install(
      String tenantId,
      String packageVersionId,
      String targetTemplateCode,
      UserPrincipal user) {
    require(user, "work-record:market:install");
    var version = repository.requireVisibleVersion(tenantId, packageVersionId);
    verifyChecksum(version.packageJson(), version.checksum());
    MarketPackageDocument document = read(version.packageJson());
    dictionaries.installDependencies(tenantId, document.dictionaries(), user.id());
    TemplatePackagePort.InstalledTemplate installed =
        templates.install(
            tenantId,
            new TemplatePackagePort.InstallTemplateCommand(
                targetTemplateCode,
                document.templateName(),
                document.description(),
                document.schemaJson(),
                document.designerJson()),
            user.id());
    repository.recordInstall(
        Ids.newId(),
        tenantId,
        version.packageId(),
        version.id(),
        installed.templateId(),
        user.id());
    return installed;
  }

  private void require(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new AccessDeniedException("template market permission denied");
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize market package", ex);
    }
  }

  private MarketPackageDocument read(String value) {
    try {
      return objectMapper.readValue(value, MarketPackageDocument.class);
    } catch (Exception ex) {
      throw new IllegalStateException("invalid market package", ex);
    }
  }

  private void verifyChecksum(String json, String checksum) {
    if (!sha256(json).equals(checksum)) {
      throw new IllegalStateException("market package checksum mismatch");
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public record PublishPackage(
      String templateId,
      String packageCode,
      String name,
      String category,
      String visibility) {}
}
```

### 13.11 FieldPolicyServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.extension.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.extension.domain.FieldPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FieldPolicyServiceTest {

  @Test
  void hiddenFieldIsRemovedFromReadableJson() {
    FieldPolicyRepository repository = Mockito.mock(FieldPolicyRepository.class);
    when(repository.listByVersion("t1", "v1"))
        .thenReturn(
            List.of(
                new FieldPolicy(
                    "v1",
                    "salary",
                    List.of("record_admin"),
                    List.of("record_admin"),
                    FieldPolicy.MaskMode.FULL)));
    FieldPolicyService service =
        new FieldPolicyService(repository, new ObjectMapper());

    String visible =
        service.filterReadableJson(
            "t1",
            "v1",
            "{\"salary\":10000,\"summary\":\"done\"}",
            TestPrincipals.normalUser());

    assertThat(visible).doesNotContain("salary").contains("summary");
  }

  @Test
  void unauthorizedRoleCannotModifyField() {
    FieldPolicyRepository repository = Mockito.mock(FieldPolicyRepository.class);
    when(repository.listByVersion("t1", "v1"))
        .thenReturn(
            List.of(
                new FieldPolicy(
                    "v1",
                    "salary",
                    List.of(),
                    List.of("system_admin"),
                    FieldPolicy.MaskMode.NONE)));
    FieldPolicyService service =
        new FieldPolicyService(repository, new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.requireWritablePatch(
                    "t1",
                    "v1",
                    "{\"salary\":10000}",
                    "{\"salary\":20000}",
                    TestPrincipals.normalUser()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
```

### 13.12 TemplateMarketServiceTest.java

```java
@Test
void installRejectsTamperedPackageBeforeCreatingTemplate() {
  when(repository.requireVisibleVersion("t1", "pv1"))
      .thenReturn(TestMarketPackages.version("{\"name\":\"tampered\"}", "0".repeat(64)));

  assertThatThrownBy(
          () ->
              service.install(
                  "t1",
                  "pv1",
                  "installed_template",
                  TestPrincipals.marketInstaller()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("checksum");

  verifyNoInteractions(templates);
}
```

---

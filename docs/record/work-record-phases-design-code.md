---
title: 工作记录模块 Phase 设计与代码包
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-06
updated: 2026-07-06
related:
  - docs/record/index.md
---

# 工作记录模块 Phase 设计与代码包

本文基于当前仓库现状与 `docs/record/index.md` 的终版设想，给出“可配置工作记录模块”的逐 Phase 详细设计、实施文件、核心完整代码与单元测试。

当前现状：

- 后端是 Maven 多模块 + Spring Boot 3.5 + Java 21。
- 业务模块使用 `modules/aiops-*`，由 `apps/aiops-server` 聚合启动。
- 数据库 migration 已到 `V0011__init_inspection.sql`。
- 持久层当前主要使用 `JdbcTemplate`，模块内 `record` DTO + `Repository` + `Service` + `Controller` 风格较轻。
- 前端是 `web/console` 单应用，React + Vite + TanStack Query + shadcn/ui 风格组件，路由集中在 `App.tsx`。

设计边界：

- 不做微前端。
- 不做流程引擎、审批流、SLA、附件、评论时间线、Excel 导入、AI 总结。
- 字典属于 `aiops-platform`。
- 工作记录属于新增 `aiops-work-record`。
- 所有查询必须带 `tenantId`。
- 管理类变更后续接入 `aiops-audit`，本包先给出审计动作点。

## Phase WR-0：工程接入与数据库骨架

### 目标

建立工作记录模块的工程边界、菜单、权限、数据库表结构。此阶段不暴露复杂业务，只保证模块可编译、migration 可执行、菜单可见。

### 交付物

- 根 `pom.xml` 新增 `modules/aiops-work-record`。
- `apps/aiops-server/pom.xml` 引入 `aiops-work-record`。
- 新增 `modules/aiops-work-record/pom.xml`。
- 新增 Flyway migration：`V0012__init_work_record.sql`。
- 新增菜单：工作记录、记录列表、表单设计、字典管理。

### 验收标准

- `mvn -pl modules/aiops-work-record -am test` 通过。
- `mvn -pl apps/aiops-server -am test` 至少能执行 migration 唯一性测试。
- `/api/platform/navigation/menus` 能返回工作记录菜单。

### `pom.xml` 增量

```xml
<module>modules/aiops-work-record</module>
```

### `apps/aiops-server/pom.xml` 增量

```xml
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record</artifactId>
  <version>${project.version}</version>
</dependency>
```

### `modules/aiops-work-record/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.aegisops</groupId>
    <artifactId>aegisops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>aiops-work-record</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-common</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
  </dependencies>
</project>
```

### `apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql`

```sql
create table if not exists platform_dict_type (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dict_code varchar(128) not null,
  dict_name varchar(128) not null,
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_code)
);

create table if not exists platform_dict_item (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  dict_type_id varchar(64) not null references platform_dict_type(id) on delete cascade,
  item_label varchar(128) not null,
  item_value varchar(128) not null,
  color varchar(32),
  icon varchar(64),
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  extra_json jsonb not null default '{}'::jsonb,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_type_id, item_value)
);

create schema if not exists work_record;

create table if not exists work_record.wr_template (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  name varchar(128) not null,
  code varchar(64) not null,
  description text,
  enabled boolean not null default true,
  schema_json jsonb not null default '{}'::jsonb,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table if not exists work_record.wr_template_field (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  template_id varchar(64) not null references work_record.wr_template(id) on delete cascade,
  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,
  required boolean not null default false,
  default_value text,
  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,
  list_visible boolean not null default false,
  filterable boolean not null default false,
  statistical boolean not null default false,
  sort_order integer not null default 0,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, template_id, field_code)
);

create table if not exists work_record.wr_record (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  template_id varchar(64) not null references work_record.wr_template(id),
  title varchar(255) not null,
  status varchar(32) not null default 'draft',
  owner_id varchar(64),
  creator_id varchar(64) not null,
  record_time timestamptz not null,
  builtin_data_json jsonb not null default '{}'::jsonb,
  custom_data_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_dict_type_tenant_code on platform_dict_type(tenant_id, dict_code);
create index if not exists idx_dict_item_type_sort on platform_dict_item(dict_type_id, enabled, sort_order);
create index if not exists idx_wr_template_tenant_enabled on work_record.wr_template(tenant_id, enabled);
create index if not exists idx_wr_field_template_sort on work_record.wr_template_field(template_id, enabled, sort_order);
create index if not exists idx_wr_record_tenant_time on work_record.wr_record(tenant_id, record_time desc);
create index if not exists idx_wr_record_tenant_owner on work_record.wr_record(tenant_id, owner_id);

insert into platform_menu_item(id, module_id, parent_id, path, title, icon, permission_code, sort_order, enabled)
values
  ('menu-work-record', 'work-record', null, '/app/work-records', '工作记录', 'clipboard-list', 'work-record:read', 300, true),
  ('menu-work-record-list', 'work-record', 'menu-work-record', '/app/work-records', '记录列表', 'list', 'work-record:read', 310, true),
  ('menu-work-record-designer', 'work-record', 'menu-work-record', '/app/work-records/designer', '表单设计', 'settings-2', 'work-record:template:write', 320, true),
  ('menu-platform-dictionaries', 'platform', null, '/app/platform/dictionaries', '字典管理', 'book-open', 'platform:dict:write', 430, true)
on conflict (module_id, path) do update
set title = excluded.title,
    icon = excluded.icon,
    permission_code = excluded.permission_code,
    sort_order = excluded.sort_order,
    enabled = excluded.enabled;
```

## Phase WR-1：平台字典能力

### 目标

在 `aiops-platform` 中补齐字典类型与字典项的 CRUD。字典服务给工作记录的记录类型、状态、优先级、环境等通用枚举提供统一来源。

### API

- `GET /api/platform/dictionaries`
- `POST /api/platform/dictionaries`
- `GET /api/platform/dictionaries/{dictCode}/items`
- `POST /api/platform/dictionaries/{dictCode}/items`

### `modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/DictTypeRecord.java`

```java
package io.aegisops.platform.dictionary;

import java.time.OffsetDateTime;

public record DictTypeRecord(
    String id,
    String tenantId,
    String dictCode,
    String dictName,
    String description,
    boolean systemBuiltin,
    boolean enabled,
    int sortOrder,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

### `DictItemRecord.java`

```java
package io.aegisops.platform.dictionary;

import java.time.OffsetDateTime;

public record DictItemRecord(
    String id,
    String tenantId,
    String dictTypeId,
    String itemLabel,
    String itemValue,
    String color,
    String icon,
    String description,
    boolean systemBuiltin,
    boolean enabled,
    int sortOrder,
    String extraJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

### `CreateDictTypeRequest.java`

```java
package io.aegisops.platform.dictionary;

public record CreateDictTypeRequest(
    String dictCode, String dictName, String description, Integer sortOrder, Boolean enabled) {}
```

### `CreateDictItemRequest.java`

```java
package io.aegisops.platform.dictionary;

public record CreateDictItemRequest(
    String itemLabel,
    String itemValue,
    String color,
    String icon,
    String description,
    Integer sortOrder,
    Boolean enabled,
    String extraJson) {}
```

### `DictionaryRepository.java`

```java
package io.aegisops.platform.dictionary;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DictionaryRepository {
  private final JdbcTemplate jdbc;

  public DictionaryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DictTypeRecord> listTypes(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, dict_code, dict_name, description, system_builtin,
                   enabled, sort_order, created_by, created_at, updated_at
            from platform_dict_type
            where tenant_id = ?
            order by sort_order asc, created_at asc
            """,
        (rs, rowNum) -> mapType(rs),
        tenantId);
  }

  public Optional<DictTypeRecord> findType(String tenantId, String dictCode) {
    List<DictTypeRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, dict_code, dict_name, description, system_builtin,
                       enabled, sort_order, created_by, created_at, updated_at
                from platform_dict_type
                where tenant_id = ? and dict_code = ?
                """,
            (rs, rowNum) -> mapType(rs),
            tenantId,
            dictCode);
    return rows.stream().findFirst();
  }

  public DictTypeRecord createType(String tenantId, CreateDictTypeRequest request, String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into platform_dict_type(
              id, tenant_id, dict_code, dict_name, description, enabled, sort_order, created_by)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        id,
        tenantId,
        request.dictCode(),
        request.dictName(),
        request.description(),
        request.enabled() == null || request.enabled(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        createdBy);
    return findType(tenantId, request.dictCode()).orElseThrow();
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode) {
    return jdbc.query(
        """
            select i.id, i.tenant_id, i.dict_type_id, i.item_label, i.item_value, i.color,
                   i.icon, i.description, i.system_builtin, i.enabled, i.sort_order,
                   i.extra_json::text, i.created_by, i.created_at, i.updated_at
            from platform_dict_item i
            join platform_dict_type t on t.id = i.dict_type_id and t.tenant_id = i.tenant_id
            where i.tenant_id = ? and t.dict_code = ?
            order by i.sort_order asc, i.created_at asc
            """,
        (rs, rowNum) -> mapItem(rs),
        tenantId,
        dictCode);
  }

  public DictItemRecord createItem(
      String tenantId, String dictCode, CreateDictItemRequest request, String createdBy) {
    DictTypeRecord type =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));
    String id = Ids.newId();
    jdbc.update(
        """
            insert into platform_dict_item(
              id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
              enabled, sort_order, extra_json, created_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
        id,
        tenantId,
        type.id(),
        request.itemLabel(),
        request.itemValue(),
        request.color(),
        request.icon(),
        request.description(),
        request.enabled() == null || request.enabled(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        blankJson(request.extraJson()),
        createdBy);
    return listItems(tenantId, dictCode).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private DictTypeRecord mapType(ResultSet rs) throws SQLException {
    return new DictTypeRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("dict_code"),
        rs.getString("dict_name"),
        rs.getString("description"),
        rs.getBoolean("system_builtin"),
        rs.getBoolean("enabled"),
        rs.getInt("sort_order"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private DictItemRecord mapItem(ResultSet rs) throws SQLException {
    return new DictItemRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("dict_type_id"),
        rs.getString("item_label"),
        rs.getString("item_value"),
        rs.getString("color"),
        rs.getString("icon"),
        rs.getString("description"),
        rs.getBoolean("system_builtin"),
        rs.getBoolean("enabled"),
        rs.getInt("sort_order"),
        rs.getString("extra_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}
```

### `DictionaryService.java`

```java
package io.aegisops.platform.dictionary;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService {
  private final DictionaryRepository repository;

  public DictionaryService(DictionaryRepository repository) {
    this.repository = repository;
  }

  public List<DictTypeRecord> listTypes(String tenantId) {
    return repository.listTypes(tenantId);
  }

  public DictTypeRecord createType(String tenantId, CreateDictTypeRequest request, String createdBy) {
    requireText(request == null ? null : request.dictCode(), "dictCode");
    requireText(request.dictName(), "dictName");
    return repository.createType(tenantId, request, defaultActor(createdBy));
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode) {
    requireText(dictCode, "dictCode");
    return repository.listItems(tenantId, dictCode);
  }

  public DictItemRecord createItem(
      String tenantId, String dictCode, CreateDictItemRequest request, String createdBy) {
    requireText(dictCode, "dictCode");
    requireText(request == null ? null : request.itemLabel(), "itemLabel");
    requireText(request.itemValue(), "itemValue");
    return repository.createItem(tenantId, dictCode, request, defaultActor(createdBy));
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String createdBy) {
    return createdBy == null || createdBy.isBlank() ? "system" : createdBy;
  }
}
```

### `DictionaryController.java`

```java
package io.aegisops.platform.dictionary;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/dictionaries")
public class DictionaryController {
  private final DictionaryService service;

  public DictionaryController(DictionaryService service) {
    this.service = service;
  }

  @GetMapping
  public List<DictTypeRecord> listTypes(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
    return service.listTypes(tenantId);
  }

  @PostMapping
  public DictTypeRecord createType(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
      @RequestBody CreateDictTypeRequest request) {
    return service.createType(tenantId, request, userId);
  }

  @GetMapping("/{dictCode}/items")
  public List<DictItemRecord> listItems(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @PathVariable String dictCode) {
    return service.listItems(tenantId, dictCode);
  }

  @PostMapping("/{dictCode}/items")
  public DictItemRecord createItem(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
      @PathVariable String dictCode,
      @RequestBody CreateDictItemRequest request) {
    return service.createItem(tenantId, dictCode, request, userId);
  }
}
```

### `DictionaryServiceTest.java`

```java
package io.aegisops.platform.dictionary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DictionaryServiceTest {
  @Test
  void createType_shouldValidateDictCode() {
    DictionaryService service = new DictionaryService(null);
    assertThatThrownBy(() -> service.createType("t1", new CreateDictTypeRequest("", "状态", null, 0, true), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode");
  }

  @Test
  void createType_shouldValidateDictName() {
    DictionaryService service = new DictionaryService(null);
    assertThatThrownBy(() -> service.createType("t1", new CreateDictTypeRequest("record_status", "", null, 0, true), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictName");
  }

  @Test
  void createItem_shouldValidateItemValue() {
    DictionaryService service = new DictionaryService(null);
    assertThatThrownBy(() -> service.createItem("t1", "record_status", new CreateDictItemRequest("草稿", "", null, null, null, 0, true, "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("itemValue");
  }

  @Test
  void createType_shouldDelegateToRepository() {
    DictionaryRepository repository = Mockito.mock(DictionaryRepository.class);
    DictionaryService service = new DictionaryService(repository);
    CreateDictTypeRequest request = new CreateDictTypeRequest("record_status", "工作记录状态", null, 10, true);

    service.createType("t1", request, null);

    verify(repository).createType("t1", request, "system");
  }
}
```

## Phase WR-2：表单模板与字段设计

### 目标

新增 `aiops-work-record` 的模板和字段能力。管理员可以创建模板，给模板配置字段，字段支持静态选项或平台字典选项。

### API

- `GET /api/work-record/templates`
- `POST /api/work-record/templates`
- `GET /api/work-record/templates/{templateId}/fields`
- `POST /api/work-record/templates/{templateId}/fields`

### `WorkRecordTemplate.java`

```java
package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record WorkRecordTemplate(
    String id,
    String tenantId,
    String name,
    String code,
    String description,
    boolean enabled,
    String schemaJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

### `WorkRecordField.java`

```java
package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record WorkRecordField(
    String id,
    String tenantId,
    String templateId,
    String fieldName,
    String fieldCode,
    String fieldType,
    boolean required,
    String defaultValue,
    String optionSource,
    String dictCode,
    String optionsJson,
    boolean listVisible,
    boolean filterable,
    boolean statistical,
    int sortOrder,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

### `CreateTemplateRequest.java`

```java
package io.aegisops.workrecord;

public record CreateTemplateRequest(
    String name, String code, String description, Boolean enabled, String schemaJson) {}
```

### `CreateFieldRequest.java`

```java
package io.aegisops.workrecord;

public record CreateFieldRequest(
    String fieldName,
    String fieldCode,
    String fieldType,
    Boolean required,
    String defaultValue,
    String optionSource,
    String dictCode,
    String optionsJson,
    Boolean listVisible,
    Boolean filterable,
    Boolean statistical,
    Integer sortOrder,
    Boolean enabled) {}
```

### `WorkRecordTemplateRepository.java`

```java
package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordTemplateRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordTemplateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecordTemplate> list(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, name, code, description, enabled, schema_json::text,
                   created_by, created_at, updated_at
            from work_record.wr_template
            where tenant_id = ?
            order by created_at desc
            """,
        (rs, rowNum) -> mapTemplate(rs),
        tenantId);
  }

  public Optional<WorkRecordTemplate> find(String tenantId, String id) {
    List<WorkRecordTemplate> rows =
        jdbc.query(
            """
                select id, tenant_id, name, code, description, enabled, schema_json::text,
                       created_by, created_at, updated_at
                from work_record.wr_template
                where tenant_id = ? and id = ?
                """,
            (rs, rowNum) -> mapTemplate(rs),
            tenantId,
            id);
    return rows.stream().findFirst();
  }

  public WorkRecordTemplate create(String tenantId, CreateTemplateRequest request, String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into work_record.wr_template(
              id, tenant_id, name, code, description, enabled, schema_json, created_by)
            values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
        id,
        tenantId,
        request.name(),
        request.code(),
        request.description(),
        request.enabled() == null || request.enabled(),
        blankJson(request.schemaJson()),
        createdBy);
    return find(tenantId, id).orElseThrow();
  }

  private WorkRecordTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new WorkRecordTemplate(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("name"),
        rs.getString("code"),
        rs.getString("description"),
        rs.getBoolean("enabled"),
        rs.getString("schema_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}
```

### `WorkRecordFieldRepository.java`

```java
package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordFieldRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordFieldRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecordField> list(String tenantId, String templateId) {
    return jdbc.query(
        """
            select id, tenant_id, template_id, field_name, field_code, field_type,
                   required, default_value, option_source, dict_code, options_json::text,
                   list_visible, filterable, statistical, sort_order, enabled, created_at, updated_at
            from work_record.wr_template_field
            where tenant_id = ? and template_id = ?
            order by sort_order asc, created_at asc
            """,
        (rs, rowNum) -> mapField(rs),
        tenantId,
        templateId);
  }

  public WorkRecordField create(String tenantId, String templateId, CreateFieldRequest request) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into work_record.wr_template_field(
              id, tenant_id, template_id, field_name, field_code, field_type, required,
              default_value, option_source, dict_code, options_json, list_visible,
              filterable, statistical, sort_order, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """,
        id,
        tenantId,
        templateId,
        request.fieldName(),
        request.fieldCode(),
        request.fieldType(),
        request.required() != null && request.required(),
        request.defaultValue(),
        request.optionSource() == null ? "static" : request.optionSource(),
        request.dictCode(),
        blankArray(request.optionsJson()),
        request.listVisible() != null && request.listVisible(),
        request.filterable() != null && request.filterable(),
        request.statistical() != null && request.statistical(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        request.enabled() == null || request.enabled());
    return list(tenantId, templateId).stream().filter(field -> field.id().equals(id)).findFirst().orElseThrow();
  }

  private WorkRecordField mapField(ResultSet rs) throws SQLException {
    return new WorkRecordField(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("field_name"),
        rs.getString("field_code"),
        rs.getString("field_type"),
        rs.getBoolean("required"),
        rs.getString("default_value"),
        rs.getString("option_source"),
        rs.getString("dict_code"),
        rs.getString("options_json"),
        rs.getBoolean("list_visible"),
        rs.getBoolean("filterable"),
        rs.getBoolean("statistical"),
        rs.getInt("sort_order"),
        rs.getBoolean("enabled"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankArray(String value) {
    return value == null || value.isBlank() ? "[]" : value;
  }
}
```

### `WorkRecordTemplateService.java`

```java
package io.aegisops.workrecord;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordTemplateService {
  private static final Set<String> FIELD_TYPES =
      Set.of("text", "textarea", "number", "date", "datetime", "select", "multi_select", "user", "switch");

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldRepository fieldRepository;

  public WorkRecordTemplateService(
      WorkRecordTemplateRepository templateRepository, WorkRecordFieldRepository fieldRepository) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
  }

  public List<WorkRecordTemplate> listTemplates(String tenantId) {
    return templateRepository.list(tenantId);
  }

  public WorkRecordTemplate createTemplate(
      String tenantId, CreateTemplateRequest request, String createdBy) {
    requireText(request == null ? null : request.name(), "name");
    requireText(request.code(), "code");
    return templateRepository.create(tenantId, request, defaultActor(createdBy));
  }

  public List<WorkRecordField> listFields(String tenantId, String templateId) {
    requireText(templateId, "templateId");
    return fieldRepository.list(tenantId, templateId);
  }

  public WorkRecordField createField(String tenantId, String templateId, CreateFieldRequest request) {
    requireText(templateId, "templateId");
    requireText(request == null ? null : request.fieldName(), "fieldName");
    requireText(request.fieldCode(), "fieldCode");
    requireText(request.fieldType(), "fieldType");
    if (!FIELD_TYPES.contains(request.fieldType())) {
      throw new IllegalArgumentException("unsupported fieldType");
    }
    if ("dict".equals(request.optionSource())) {
      requireText(request.dictCode(), "dictCode");
    }
    return fieldRepository.create(tenantId, templateId, request);
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String userId) {
    return userId == null || userId.isBlank() ? "system" : userId;
  }
}
```

### `WorkRecordTemplateController.java`

```java
package io.aegisops.workrecord;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/templates")
public class WorkRecordTemplateController {
  private final WorkRecordTemplateService service;

  public WorkRecordTemplateController(WorkRecordTemplateService service) {
    this.service = service;
  }

  @GetMapping
  public List<WorkRecordTemplate> list(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
    return service.listTemplates(tenantId);
  }

  @PostMapping
  public WorkRecordTemplate create(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
      @RequestBody CreateTemplateRequest request) {
    return service.createTemplate(tenantId, request, userId);
  }

  @GetMapping("/{templateId}/fields")
  public List<WorkRecordField> listFields(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @PathVariable String templateId) {
    return service.listFields(tenantId, templateId);
  }

  @PostMapping("/{templateId}/fields")
  public WorkRecordField createField(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @PathVariable String templateId,
      @RequestBody CreateFieldRequest request) {
    return service.createField(tenantId, templateId, request);
  }
}
```

### `WorkRecordTemplateServiceTest.java`

```java
package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordTemplateServiceTest {
  @Test
  void createTemplate_shouldValidateName() {
    WorkRecordTemplateService service = new WorkRecordTemplateService(null, null);
    assertThatThrownBy(() -> service.createTemplate("t1", new CreateTemplateRequest("", "daily", null, true, "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void createField_shouldRejectUnsupportedFieldType() {
    WorkRecordTemplateService service = new WorkRecordTemplateService(null, null);
    CreateFieldRequest request =
        new CreateFieldRequest("字段", "field", "unknown", false, null, "static", null, "[]", false, false, false, 0, true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void createField_whenDictOption_shouldRequireDictCode() {
    WorkRecordTemplateService service = new WorkRecordTemplateService(null, null);
    CreateFieldRequest request =
        new CreateFieldRequest("优先级", "priority", "select", false, null, "dict", "", "[]", true, true, false, 0, true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode");
  }

  @Test
  void createTemplate_shouldDelegateToRepository() {
    WorkRecordTemplateRepository templates = Mockito.mock(WorkRecordTemplateRepository.class);
    WorkRecordFieldRepository fields = Mockito.mock(WorkRecordFieldRepository.class);
    WorkRecordTemplateService service = new WorkRecordTemplateService(templates, fields);
    CreateTemplateRequest request = new CreateTemplateRequest("日常记录", "daily", null, true, "{}");

    service.createTemplate("t1", request, null);

    verify(templates).create("t1", request, "system");
  }
}
```

## Phase WR-3：工作记录填写、查询与详情

### 目标

普通用户能填写记录，记录管理员能按租户查询记录。第一版只做软删除、列表、创建，不做审批和评论。

### API

- `GET /api/work-record/records`
- `POST /api/work-record/records`
- `GET /api/work-record/records/{recordId}`
- `DELETE /api/work-record/records/{recordId}`

### `WorkRecord.java`

```java
package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record WorkRecord(
    String id,
    String tenantId,
    String templateId,
    String title,
    String status,
    String ownerId,
    String creatorId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

### `CreateWorkRecordRequest.java`

```java
package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record CreateWorkRecordRequest(
    String templateId,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson) {}
```

### `WorkRecordRepository.java`

```java
package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecord> list(String tenantId, String ownerId, String status) {
    return jdbc.query(
        """
            select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                   builtin_data_json::text, custom_data_json::text, created_at, updated_at
            from work_record.wr_record
            where tenant_id = ?
              and deleted_at is null
              and (? is null or owner_id = ?)
              and (? is null or status = ?)
            order by record_time desc, created_at desc
            limit 200
            """,
        (rs, rowNum) -> mapRecord(rs),
        tenantId,
        ownerId,
        ownerId,
        status,
        status);
  }

  public Optional<WorkRecord> find(String tenantId, String id) {
    List<WorkRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
                       builtin_data_json::text, custom_data_json::text, created_at, updated_at
                from work_record.wr_record
                where tenant_id = ? and id = ? and deleted_at is null
                """,
            (rs, rowNum) -> mapRecord(rs),
            tenantId,
            id);
    return rows.stream().findFirst();
  }

  public WorkRecord create(String tenantId, CreateWorkRecordRequest request, String creatorId) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into work_record.wr_record(
              id, tenant_id, template_id, title, status, owner_id, creator_id, record_time,
              builtin_data_json, custom_data_json)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            """,
        id,
        tenantId,
        request.templateId(),
        request.title(),
        request.status() == null || request.status().isBlank() ? "draft" : request.status(),
        request.ownerId(),
        creatorId,
        request.recordTime(),
        blankJson(request.builtinDataJson()),
        blankJson(request.customDataJson()));
    return find(tenantId, id).orElseThrow();
  }

  public void softDelete(String tenantId, String id) {
    jdbc.update(
        "update work_record.wr_record set deleted_at = now() where tenant_id = ? and id = ?",
        tenantId,
        id);
  }

  private WorkRecord mapRecord(ResultSet rs) throws SQLException {
    return new WorkRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getString("owner_id"),
        rs.getString("creator_id"),
        rs.getObject("record_time", OffsetDateTime.class),
        rs.getString("builtin_data_json"),
        rs.getString("custom_data_json"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}
```

### `WorkRecordService.java`

```java
package io.aegisops.workrecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordService {
  private static final Set<String> STATUSES = Set.of("draft", "processing", "done", "archived");
  private final WorkRecordRepository repository;

  public WorkRecordService(WorkRecordRepository repository) {
    this.repository = repository;
  }

  public List<WorkRecord> list(String tenantId, String ownerId, String status) {
    if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
      throw new IllegalArgumentException("unsupported status");
    }
    return repository.list(tenantId, blankToNull(ownerId), blankToNull(status));
  }

  public WorkRecord get(String tenantId, String id) {
    requireText(id, "id");
    return repository
        .find(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("work record not found"));
  }

  public WorkRecord create(String tenantId, CreateWorkRecordRequest request, String creatorId) {
    requireText(request == null ? null : request.templateId(), "templateId");
    requireText(request.title(), "title");
    if (request.status() != null && !request.status().isBlank() && !STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("unsupported status");
    }
    OffsetDateTime recordTime = request.recordTime() == null ? OffsetDateTime.now() : request.recordTime();
    CreateWorkRecordRequest normalized =
        new CreateWorkRecordRequest(
            request.templateId(),
            request.title(),
            request.status(),
            request.ownerId(),
            recordTime,
            request.builtinDataJson(),
            request.customDataJson());
    return repository.create(tenantId, normalized, defaultActor(creatorId));
  }

  public void delete(String tenantId, String id) {
    requireText(id, "id");
    repository.softDelete(tenantId, id);
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String defaultActor(String userId) {
    return userId == null || userId.isBlank() ? "system" : userId;
  }
}
```

### `WorkRecordController.java`

```java
package io.aegisops.workrecord;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordController {
  private final WorkRecordService service;

  public WorkRecordController(WorkRecordService service) {
    this.service = service;
  }

  @GetMapping
  public List<WorkRecord> list(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) String status) {
    return service.list(tenantId, ownerId, status);
  }

  @GetMapping("/{recordId}")
  public WorkRecord get(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @PathVariable String recordId) {
    return service.get(tenantId, recordId);
  }

  @PostMapping
  public WorkRecord create(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
      @RequestBody CreateWorkRecordRequest request) {
    return service.create(tenantId, request, userId);
  }

  @DeleteMapping("/{recordId}")
  public void delete(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @PathVariable String recordId) {
    service.delete(tenantId, recordId);
  }
}
```

### `WorkRecordServiceTest.java`

```java
package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordServiceTest {
  @Test
  void create_shouldValidateTemplateId() {
    WorkRecordService service = new WorkRecordService(null);
    assertThatThrownBy(() -> service.create("t1", new CreateWorkRecordRequest("", "日报", "draft", null, null, "{}", "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateId");
  }

  @Test
  void create_shouldValidateTitle() {
    WorkRecordService service = new WorkRecordService(null);
    assertThatThrownBy(() -> service.create("t1", new CreateWorkRecordRequest("tpl1", "", "draft", null, null, "{}", "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void list_shouldRejectUnsupportedStatus() {
    WorkRecordService service = new WorkRecordService(null);
    assertThatThrownBy(() -> service.list("t1", null, "closed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported status");
  }

  @Test
  void delete_shouldDelegateToRepositoryWithTenant() {
    WorkRecordRepository repository = Mockito.mock(WorkRecordRepository.class);
    WorkRecordService service = new WorkRecordService(repository);

    service.delete("t1", "r1");

    verify(repository).softDelete("t1", "r1");
  }
}
```

## Phase WR-4：前端控制台

### 目标

在现有 `web/console` 单应用中加入字典管理、记录列表、记录编辑、模板设计页。第一版使用清晰的表格和表单，不做复杂拖拽库；字段顺序先通过上下移动或排序值维护。

### 路由增量：`web/console/src/App.tsx`

```tsx
import { DictionaryPage } from './pages/platform/DictionaryPage'
import { WorkRecordEditPage } from './pages/work-record/WorkRecordEditPage'
import { WorkRecordListPage } from './pages/work-record/WorkRecordListPage'
import { WorkRecordTemplateDesignerPage } from './pages/work-record/WorkRecordTemplateDesignerPage'

<Route path="/app/platform/dictionaries" element={<DictionaryPage />} />
<Route path="/platform/dictionaries" element={<DictionaryPage />} />
<Route path="/app/work-records" element={<WorkRecordListPage />} />
<Route path="/work-records" element={<WorkRecordListPage />} />
<Route path="/app/work-records/create" element={<WorkRecordEditPage />} />
<Route path="/app/work-records/:recordId/edit" element={<WorkRecordEditPage />} />
<Route path="/app/work-records/designer" element={<WorkRecordTemplateDesignerPage />} />
```

### `web/console/src/features/work-record/types.ts`

```ts
export type DictTypeRecord = {
  id: string;
  tenantId: string;
  dictCode: string;
  dictName: string;
  description?: string;
  enabled: boolean;
  sortOrder: number;
};

export type DictItemRecord = {
  id: string;
  itemLabel: string;
  itemValue: string;
  color?: string;
  enabled: boolean;
  sortOrder: number;
};

export type WorkRecordTemplate = {
  id: string;
  name: string;
  code: string;
  description?: string;
  enabled: boolean;
  schemaJson: string;
};

export type WorkRecordField = {
  id: string;
  templateId: string;
  fieldName: string;
  fieldCode: string;
  fieldType: string;
  required: boolean;
  optionSource: "static" | "dict";
  dictCode?: string;
  optionsJson: string;
  listVisible: boolean;
  filterable: boolean;
  sortOrder: number;
  enabled: boolean;
};

export type WorkRecord = {
  id: string;
  templateId: string;
  title: string;
  status: string;
  ownerId?: string;
  creatorId: string;
  recordTime: string;
  builtinDataJson: string;
  customDataJson: string;
  createdAt: string;
};
```

### `web/console/src/features/work-record/api.ts`

```ts
import { apiRequest } from "../../api/client";
import type {
  DictItemRecord,
  DictTypeRecord,
  WorkRecord,
  WorkRecordField,
  WorkRecordTemplate,
} from "./types";

export function listDictionaries() {
  return apiRequest<DictTypeRecord[]>("/api/platform/dictionaries");
}

export function listDictItems(dictCode: string) {
  return apiRequest<DictItemRecord[]>(
    `/api/platform/dictionaries/${dictCode}/items`,
  );
}

export function listTemplates() {
  return apiRequest<WorkRecordTemplate[]>("/api/work-record/templates");
}

export function listTemplateFields(templateId: string) {
  return apiRequest<WorkRecordField[]>(
    `/api/work-record/templates/${templateId}/fields`,
  );
}

export function listWorkRecords(
  params: { ownerId?: string; status?: string } = {},
) {
  const query = new URLSearchParams();
  if (params.ownerId) query.set("ownerId", params.ownerId);
  if (params.status) query.set("status", params.status);
  const suffix = query.toString() ? `?${query}` : "";
  return apiRequest<WorkRecord[]>(`/api/work-record/records${suffix}`);
}

export function createWorkRecord(payload: {
  templateId: string;
  title: string;
  status: string;
  ownerId?: string;
  recordTime?: string;
  builtinDataJson: string;
  customDataJson: string;
}) {
  return apiRequest<WorkRecord>("/api/work-record/records", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
```

### `web/console/src/pages/work-record/WorkRecordListPage.tsx`

```tsx
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { listWorkRecords } from "../../features/work-record/api";
import { Badge } from "../../components/ui/badge";
import { Button } from "../../components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "../../components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../../components/ui/table";

export function WorkRecordListPage() {
  const records = useQuery({
    queryKey: ["work-records"],
    queryFn: () => listWorkRecords(),
  });

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">工作记录</h1>
          <p className="text-sm text-muted-foreground">
            运维日报、故障记录、巡检记录与变更记录。
          </p>
        </div>
        <Button asChild>
          <Link to="/app/work-records/create">新建记录</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>记录列表</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>标题</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>记录时间</TableHead>
                <TableHead>创建人</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(records.data || []).map((record) => (
                <TableRow key={record.id}>
                  <TableCell>{record.title}</TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        record.status === "done" ? "default" : "secondary"
                      }
                    >
                      {record.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {new Date(record.recordTime).toLocaleString()}
                  </TableCell>
                  <TableCell>{record.creatorId}</TableCell>
                </TableRow>
              ))}
              {records.data?.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={4}
                    className="text-center text-muted-foreground"
                  >
                    暂无工作记录
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
```

### `web/console/src/pages/work-record/WorkRecordListPage.test.tsx`

```tsx
import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { renderWithProviders } from "../../test/test-utils";
import { WorkRecordListPage } from "./WorkRecordListPage";

vi.mock("../../features/work-record/api", () => ({
  listWorkRecords: vi.fn(async () => [
    {
      id: "r1",
      templateId: "tpl1",
      title: "数据库巡检",
      status: "done",
      creatorId: "u1",
      recordTime: "2026-07-06T10:00:00Z",
      builtinDataJson: "{}",
      customDataJson: "{}",
      createdAt: "2026-07-06T10:00:00Z",
    },
  ]),
}));

describe("WorkRecordListPage", () => {
  it("renders work records", async () => {
    renderWithProviders(<WorkRecordListPage />);

    expect(await screen.findByText("数据库巡检")).toBeInTheDocument();
    expect(screen.getByText("done")).toBeInTheDocument();
  });
});
```

## Phase WR-5：导出、审计与收尾验收

### 目标

提供 CSV 导出能力，补齐审计动作点和本地验证入口。第一版导出由 server 生成 CSV，不引入 EasyExcel；后续需要 Excel 样式再引入专门库。

### API

- `GET /api/work-record/records/export`

### `WorkRecordExportService.java`

```java
package io.aegisops.workrecord;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportService {
  private final WorkRecordRepository repository;

  public WorkRecordExportService(WorkRecordRepository repository) {
    this.repository = repository;
  }

  public byte[] exportCsv(String tenantId, String ownerId, String status) {
    List<WorkRecord> records = repository.list(tenantId, blankToNull(ownerId), blankToNull(status));
    StringBuilder csv = new StringBuilder();
    csv.append("id,title,status,ownerId,creatorId,recordTime\n");
    for (WorkRecord record : records) {
      csv.append(escape(record.id())).append(',')
          .append(escape(record.title())).append(',')
          .append(escape(record.status())).append(',')
          .append(escape(record.ownerId())).append(',')
          .append(escape(record.creatorId())).append(',')
          .append(escape(record.recordTime().toString()))
          .append('\n');
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
```

### `WorkRecordExportController.java`

```java
package io.aegisops.workrecord;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordExportController {
  private final WorkRecordExportService service;

  public WorkRecordExportController(WorkRecordExportService service) {
    this.service = service;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
      @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) String status) {
    byte[] body = service.exportCsv(tenantId, ownerId, status);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=work-records.csv")
        .contentType(new MediaType("text", "csv"))
        .body(body);
  }
}
```

### `WorkRecordExportServiceTest.java`

```java
package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordExportServiceTest {
  @Test
  void exportCsv_shouldEscapeCommaAndQuote() {
    WorkRecordRepository repository = Mockito.mock(WorkRecordRepository.class);
    when(repository.list("t1", null, null))
        .thenReturn(
            List.of(
                new WorkRecord(
                    "r1",
                    "t1",
                    "tpl1",
                    "巡检,\"核心\"",
                    "done",
                    "u2",
                    "u1",
                    OffsetDateTime.parse("2026-07-06T10:00:00Z"),
                    "{}",
                    "{}",
                    null,
                    null)));

    WorkRecordExportService service = new WorkRecordExportService(repository);
    String csv = new String(service.exportCsv("t1", null, null), StandardCharsets.UTF_8);

    assertThat(csv).contains("\"巡检,\"\"核心\"\"\"");
    assertThat(csv).startsWith("id,title,status,ownerId,creatorId,recordTime");
  }
}
```

### 审计动作点

后续实施时在以下 service 方法中写入 `aiops-audit`：

- `DictionaryService.createType`：`platform.dict_type.create`
- `DictionaryService.createItem`：`platform.dict_item.create`
- `WorkRecordTemplateService.createTemplate`：`work_record.template.create`
- `WorkRecordTemplateService.createField`：`work_record.template_field.create`
- `WorkRecordService.create`：`work_record.record.create`
- `WorkRecordService.delete`：`work_record.record.delete`
- `WorkRecordExportService.exportCsv`：`work_record.record.export`

### Phase 收尾验证

```bash
mvn -pl modules/aiops-platform -am test
mvn -pl modules/aiops-work-record -am test
mvn -pl apps/aiops-server -am test
pnpm --filter @aegisops/console test
bash scripts/ci/docs.sh
```

### 不进入本轮的功能

- 审批流。
- SLA。
- AI 总结。
- Excel 导入。
- 附件。
- 评论时间线。
- 告警、Incident、巡检联动。
- 独立部署或微前端。

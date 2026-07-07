---
title: 工作记录 Phase 03 模板与字段
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 03：工作记录模板与字段

## 目标

交付模板与字段索引能力，为 Formily 设计器和运行态服务。

采用双结构：

```text
wr_template.schema_json    负责设计器还原和运行态渲染
wr_template_field          负责列表列、筛选白名单、导出、后端校验、统计
```

## 后端现状与补齐点

已存在：

```text
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordTemplate*
modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordTemplateServiceTest.java
```

补齐：

- 保留字段编码校验与保留字拒绝。
- 新增 schema 保存时同步 `wr_template_field`。
- 新增字段排序、禁用字段、字典绑定校验。
- `field_code` 创建后默认不可修改；确需修改必须单独接口并写审计。

## API 契约

```text
GET  /api/work-record/templates
POST /api/work-record/templates
GET  /api/work-record/templates/{templateId}
PUT  /api/work-record/templates/{templateId}
POST /api/work-record/templates/{templateId}/fields
PUT  /api/work-record/templates/{templateId}/fields/{fieldId}
POST /api/work-record/templates/{templateId}/schema
```

`POST /schema` 请求：

```json
{
  "schemaJson": "{}",
  "fields": [
    {
      "fieldName": "记录类型",
      "fieldCode": "record_type",
      "fieldType": "select",
      "required": true,
      "optionSource": "dict",
      "dictCode": "record_type",
      "listVisible": true,
      "filterable": true,
      "statistical": false,
      "sortOrder": 10,
      "enabled": true
    }
  ]
}
```

## 字段类型

```text
text
textarea
number
date
datetime
select
multi_select
user
boolean
```

## 后端完整核心代码

```java
// modules/aiops-work-record/src/main/java/io/aegisops/workrecord/ReservedFieldCodes.java
package io.aegisops.workrecord;

import java.util.Set;

final class ReservedFieldCodes {
  private static final Set<String> VALUES =
      Set.of(
          "id",
          "tenant_id",
          "template_id",
          "title",
          "status",
          "owner_id",
          "creator_id",
          "record_time",
          "created_at",
          "updated_at",
          "deleted_at",
          "custom_data_json",
          "builtin_data_json");

  private ReservedFieldCodes() {}

  static boolean contains(String fieldCode) {
    return fieldCode != null && VALUES.contains(fieldCode.trim().toLowerCase());
  }
}
```

```java
// modules/aiops-work-record/src/main/java/io/aegisops/workrecord/TemplateSchemaRequest.java
package io.aegisops.workrecord;

import java.util.List;

public record TemplateSchemaRequest(String schemaJson, List<CreateFieldRequest> fields) {}
```

```java
// modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordTemplateService.java
public WorkRecordTemplate saveSchema(
    String tenantId, String templateId, TemplateSchemaRequest request, String actor) {
  requireText(tenantId, "tenantId");
  requireText(templateId, "templateId");
  requireJsonObject(request.schemaJson(), "schemaJson");

  List<CreateFieldRequest> fields = request.fields() == null ? List.of() : request.fields();
  for (CreateFieldRequest field : fields) {
    requireText(field.fieldCode(), "fieldCode");
    if (ReservedFieldCodes.contains(field.fieldCode())) {
      throw new IllegalArgumentException("reserved fieldCode: " + field.fieldCode());
    }
    requireSupportedFieldType(field.fieldType());
    if ("dict".equals(field.optionSource())) {
      requireText(field.dictCode(), "dictCode");
    }
  }

  repository.updateSchema(tenantId, templateId, request.schemaJson(), actor);
  fieldRepository.replaceFields(tenantId, templateId, fields);
  auditSink.record(
      tenantId,
      actor,
      "work_record.template.schema.update",
      "wr_template",
      templateId,
      Map.of("fieldCount", String.valueOf(fields.size())));
  return repository.findById(tenantId, templateId).orElseThrow();
}
```

## 前端类型完整代码

```ts
// web/portal/src/features/work-records/data/field-types.ts
export const workRecordFieldTypes = [
  "text",
  "textarea",
  "number",
  "date",
  "datetime",
  "select",
  "multi_select",
  "user",
  "boolean",
] as const;

export type WorkRecordFieldType = (typeof workRecordFieldTypes)[number];
```

```ts
// web/portal/src/features/work-records/data/reserved-field-codes.ts
export const reservedFieldCodes = new Set([
  "id",
  "tenant_id",
  "template_id",
  "title",
  "status",
  "owner_id",
  "creator_id",
  "record_time",
  "created_at",
  "updated_at",
  "deleted_at",
  "custom_data_json",
  "builtin_data_json",
]);

export function isReservedFieldCode(fieldCode: string): boolean {
  return reservedFieldCodes.has(fieldCode.trim().toLowerCase());
}
```

## 单元测试

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordTemplateServiceTest.java
@Test
void rejectsReservedFieldCodeWhenSavingSchema() {
  WorkRecordTemplateService service = newService();

  TemplateSchemaRequest request =
      new TemplateSchemaRequest(
          "{}",
          List.of(
              new CreateFieldRequest(
                  "标题", "title", "text", true, null, "static", null, "[]", true, true, false, 10, true)));

  assertThatThrownBy(() -> service.saveSchema("tenant-a", "template-a", request, "admin"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("reserved fieldCode");
}
```

```ts
// web/portal/src/features/work-records/data/reserved-field-codes.test.ts
import { describe, expect, it } from "vitest";
import { isReservedFieldCode } from "./reserved-field-codes";

describe("reserved field codes", () => {
  it("rejects built-in columns", () => {
    expect(isReservedFieldCode("title")).toBe(true);
    expect(isReservedFieldCode(" custom_data_json ")).toBe(true);
  });

  it("allows custom field codes", () => {
    expect(isReservedFieldCode("process_result")).toBe(false);
  });
});
```

## 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
cd web/portal && pnpm run test
```

验收标准：

- schema 与字段索引同步保存。
- 保留字段编码被拒绝。
- dict 字段必须绑定存在的 `dictCode`。

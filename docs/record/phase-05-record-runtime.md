---
title: 工作记录 Phase 05 记录运行态
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 05：记录新建、编辑与详情

## 目标

交付运行态页面：

```text
/work-records/new
/work-records/$recordId
/work-records/$recordId/edit
```

流程：

```text
读取模板 schema
扫描字典字段
请求字典项
注入 enum / dataSource
Formily 渲染表单
提交 custom_data_json
详情页按 schema 顺序只读展示
```

## 后端补齐点

已存在：

```text
WorkRecordService
WorkRecordFieldValidator
WorkRecordServiceTest
WorkRecordFieldValidatorTest
```

必须覆盖：

- 必填字段。
- 字段类型。
- select / multi_select 值合法性。
- 字段属于当前模板。
- 禁用字段不能写入。
- 普通用户只能读取自己的记录，管理员可读全部。

## 后端校验完整代码形态

```java
// modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java
public void validateWrite(
    List<WorkRecordField> fields, Map<String, Object> customData, DictionaryLookup dictionaries) {
  Map<String, WorkRecordField> enabledFields =
      fields.stream()
          .filter(WorkRecordField::enabled)
          .collect(Collectors.toMap(WorkRecordField::fieldCode, Function.identity()));

  for (String fieldCode : customData.keySet()) {
    if (!enabledFields.containsKey(fieldCode)) {
      throw new IllegalArgumentException("unknown or disabled field: " + fieldCode);
    }
  }

  for (WorkRecordField field : enabledFields.values()) {
    Object value = customData.get(field.fieldCode());
    if (field.required() && isBlankValue(value)) {
      throw new IllegalArgumentException("required field missing: " + field.fieldCode());
    }
    if (isBlankValue(value)) {
      continue;
    }
    validateType(field, value);
    validateOptions(field, value, dictionaries);
  }
}
```

## 前端运行态完整代码

```tsx
// web/portal/src/features/work-records/components/formily-runtime-form.tsx
import { createForm } from "@formily/core";
import { FormProvider, createSchemaField } from "@formily/react";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";

const SchemaField = createSchemaField({
  components: {},
});

type FormilyRuntimeFormProps = {
  schema: Record<string, unknown>;
  initialValues?: Record<string, unknown>;
  submitting?: boolean;
  onSubmit: (values: Record<string, unknown>) => void;
};

export function FormilyRuntimeForm({
  schema,
  initialValues,
  submitting,
  onSubmit,
}: FormilyRuntimeFormProps) {
  const form = createForm({ initialValues });

  return (
    <FormProvider form={form}>
      <SchemaField schema={schema} />
      <div className="flex justify-end gap-2">
        <Button
          disabled={submitting}
          onClick={() => {
            form.submit(onSubmit);
          }}
        >
          {t("common.save")}
        </Button>
      </div>
    </FormProvider>
  );
}
```

```ts
// web/portal/src/features/work-records/components/dict-schema-injector.ts
type DictItem = {
  itemLabel: string;
  itemValue: string;
};

type DictMap = Record<string, DictItem[]>;
type JsonObject = Record<string, unknown>;

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function injectDictionaryOptions<T extends JsonObject>(
  schema: T,
  dicts: DictMap,
): T {
  const cloned = structuredClone(schema);

  function visit(node: unknown) {
    if (!isObject(node)) return;
    const extension = node["x-work-record"];
    if (isObject(extension) && extension.optionSource === "dict") {
      const dictCode = String(extension.dictCode);
      node.enum = (dicts[dictCode] ?? []).map((item) => ({
        label: item.itemLabel,
        value: item.itemValue,
      }));
    }
    for (const value of Object.values(node)) {
      if (isObject(value) || Array.isArray(value)) visit(value);
    }
  }

  visit(cloned);
  return cloned;
}
```

## 单元测试

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordFieldValidatorTest.java
@Test
void rejectsIllegalSelectValue() {
  WorkRecordField field =
      field("record_priority", "select", true, "static", null, "[{\"label\":\"P2\",\"value\":\"P2\"}]");
  WorkRecordFieldValidator validator = new WorkRecordFieldValidator();

  assertThatThrownBy(
          () ->
              validator.validateWrite(
                  List.of(field), Map.of("record_priority", "P0"), DictionaryLookup.empty()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("illegal option");
}
```

```ts
// web/portal/src/features/work-records/components/dict-schema-injector.test.ts
import { describe, expect, it } from "vitest";
import { injectDictionaryOptions } from "./dict-schema-injector";

describe("injectDictionaryOptions", () => {
  it("injects dictionary enum into schema", () => {
    const schema = injectDictionaryOptions(
      {
        properties: {
          priority: {
            "x-work-record": {
              optionSource: "dict",
              dictCode: "record_priority",
            },
          },
        },
      },
      {
        record_priority: [{ itemLabel: "P2", itemValue: "P2" }],
      },
    );

    expect(schema.properties.priority.enum).toEqual([
      { label: "P2", value: "P2" },
    ]);
  });
});
```

## 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
cd web/portal && pnpm run test && pnpm run build
```

验收标准：

- 新建、编辑、详情页面均可访问。
- 非法动态字段值被后端拒绝。
- 详情页可展示禁用字典项的历史 label。

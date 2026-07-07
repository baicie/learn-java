---
title: 工作记录 Phase 04 Formily 设计器
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 04：Formily + Designable 表单设计器

## 目标

在 `web/portal` 中新增表单设计页：

```text
/work-records/designer
```

能力：

- 选择模板。
- 拖拽字段。
- 配置字段标题、编码、类型、必填、默认值。
- 配置列表展示、筛选、统计。
- 绑定平台字典。
- 保存 Formily schema，并同步字段索引。
- 预览运行态表单。

## 依赖

新增依赖需要单独 ADR 或在本 Phase PR 说明：

```bash
cd web/portal
pnpm add @formily/core @formily/react @formily/json-schema @designable/core @designable/react @designable/formily-antd
```

说明：

- 设计态允许 Designable 保留自身风格。
- 用户填写页不直接暴露 Designable UI。
- 不引入完整 LowCodeEngine。

## 前端文件

```text
web/portal/src/features/work-records/components/template-designer-page.tsx
web/portal/src/features/work-records/components/formily-designer-shell.tsx
web/portal/src/features/work-records/components/dict-schema-injector.ts
web/portal/src/features/work-records/data/formily-schema.ts
web/portal/src/features/work-records/api/template-api.ts
web/portal/src/features/work-records/hooks/use-record-template.ts
web/portal/src/features/work-records/hooks/use-dict-items.ts
```

## 字段扩展协议

Formily schema 的每个可保存业务字段必须带：

```json
{
  "x-work-record": {
    "fieldCode": "record_type",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_type",
    "listVisible": true,
    "filterable": true,
    "statistical": false
  }
}
```

## Schema 提取完整代码

```ts
// web/portal/src/features/work-records/data/formily-schema.ts
import { z } from "zod";
import { workRecordFieldTypes } from "./field-types";
import { isReservedFieldCode } from "./reserved-field-codes";

export const workRecordSchemaExtensionSchema = z.object({
  fieldCode: z.string().min(1),
  fieldType: z.enum(workRecordFieldTypes),
  optionSource: z.enum(["static", "dict"]).default("static"),
  dictCode: z.string().optional(),
  listVisible: z.boolean().default(false),
  filterable: z.boolean().default(false),
  statistical: z.boolean().default(false),
});

export type WorkRecordSchemaField = z.infer<
  typeof workRecordSchemaExtensionSchema
> & {
  fieldName: string;
  required: boolean;
  sortOrder: number;
  enabled: boolean;
  optionsJson: string;
};

type JsonObject = Record<string, unknown>;

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function extractWorkRecordFields(
  schema: JsonObject,
): WorkRecordSchemaField[] {
  const fields: WorkRecordSchemaField[] = [];

  function visit(node: unknown, order: { value: number }) {
    if (!isObject(node)) return;

    const extension = node["x-work-record"];
    if (extension) {
      const parsed = workRecordSchemaExtensionSchema.parse(extension);
      if (isReservedFieldCode(parsed.fieldCode)) {
        throw new Error(`reserved fieldCode: ${parsed.fieldCode}`);
      }
      if (parsed.optionSource === "dict" && !parsed.dictCode) {
        throw new Error(`dictCode is required: ${parsed.fieldCode}`);
      }
      fields.push({
        ...parsed,
        fieldName: String(node.title ?? parsed.fieldCode),
        required: Boolean(node.required),
        sortOrder: order.value,
        enabled: true,
        optionsJson: JSON.stringify(node.enum ?? []),
      });
      order.value += 10;
    }

    for (const value of Object.values(node)) {
      if (isObject(value) || Array.isArray(value)) {
        visit(value, order);
      }
    }
  }

  visit(schema, { value: 10 });
  return fields;
}
```

## 设计器保存完整代码

```tsx
// web/portal/src/features/work-records/components/template-designer-page.tsx
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";
import { saveTemplateSchema } from "../api/template-api";
import { extractWorkRecordFields } from "../data/formily-schema";
import { FormilyDesignerShell } from "./formily-designer-shell";

export function TemplateDesignerPage() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: saveTemplateSchema,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["work-record-templates"] });
      toast.success(t("common.save"));
    },
  });

  function handleSave(templateId: string, schema: Record<string, unknown>) {
    const fields = extractWorkRecordFields(schema);
    mutation.mutate({
      templateId,
      schemaJson: JSON.stringify(schema),
      fields,
    });
  }

  return (
    <FormilyDesignerShell
      title={t("workRecords.designer.title")}
      actions={
        <Button disabled={mutation.isPending}>{t("common.save")}</Button>
      }
      onSave={handleSave}
    />
  );
}
```

## 单元测试

```ts
// web/portal/src/features/work-records/data/formily-schema.test.ts
import { describe, expect, it } from "vitest";
import { extractWorkRecordFields } from "./formily-schema";

describe("extractWorkRecordFields", () => {
  it("extracts work record field metadata from formily schema", () => {
    const fields = extractWorkRecordFields({
      type: "object",
      properties: {
        recordType: {
          type: "string",
          title: "记录类型",
          required: true,
          "x-work-record": {
            fieldCode: "record_type",
            fieldType: "select",
            optionSource: "dict",
            dictCode: "record_type",
            listVisible: true,
            filterable: true,
            statistical: false,
          },
        },
      },
    });

    expect(fields).toHaveLength(1);
    expect(fields[0]).toMatchObject({
      fieldCode: "record_type",
      fieldType: "select",
      dictCode: "record_type",
      required: true,
    });
  });

  it("rejects reserved field codes", () => {
    expect(() =>
      extractWorkRecordFields({
        properties: {
          title: {
            title: "标题",
            "x-work-record": { fieldCode: "title", fieldType: "text" },
          },
        },
      }),
    ).toThrow("reserved fieldCode");
  });
});
```

## 验收

```bash
cd web/portal
pnpm run lint
pnpm run test
pnpm run build
```

验收标准：

- 设计器页能打开。
- 保存 schema 时同步生成字段索引。
- 字典字段缺少 `dictCode` 会在前端保存前失败。

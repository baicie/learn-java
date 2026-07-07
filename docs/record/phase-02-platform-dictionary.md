---
title: 工作记录 Phase 02 平台字典
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 02：平台字典

## 目标

把平台字典作为独立平台能力交付，供工作记录模板字段绑定使用。

第一批字典：

```text
record_type
record_status
record_priority
env_type
yes_no
process_result
```

## 后端现状与补齐点

已存在：

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/*
modules/aiops-platform/src/test/java/io/aegisops/platform/dictionary/DictionaryServiceTest.java
```

补齐：

- `GET /api/platform/dictionaries/{dictCode}/items` 支持 `includeDisabled=true`，用于历史记录展示。
- 字典项禁用，不物理删除。
- `DefaultDictionaryInitializer` 对每个租户幂等 seed 默认字典。
- 审计覆盖 `platform.dict_type.*` 与 `platform.dict_item.*`。

## API 契约

```text
GET  /api/platform/dictionaries
POST /api/platform/dictionaries
PUT  /api/platform/dictionaries/{dictCode}
GET  /api/platform/dictionaries/{dictCode}/items?includeDisabled=false
POST /api/platform/dictionaries/{dictCode}/items
PUT  /api/platform/dictionaries/{dictCode}/items/{itemId}
```

## 前端文件

```text
web/portal/src/features/dictionaries/index.tsx
web/portal/src/features/dictionaries/api.ts
web/portal/src/features/dictionaries/data/schema.ts
web/portal/src/features/dictionaries/hooks/use-dictionaries.ts
web/portal/src/features/dictionaries/components/dictionary-type-list.tsx
web/portal/src/features/dictionaries/components/dictionary-item-table.tsx
web/portal/src/features/dictionaries/components/dictionary-type-dialog.tsx
web/portal/src/features/dictionaries/components/dictionary-item-dialog.tsx
web/portal/src/features/dictionaries/api.test.ts
```

## 前端 API 完整代码

```ts
// web/portal/src/features/dictionaries/data/schema.ts
import { z } from "zod";

export const dictTypeSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictCode: z.string(),
  dictName: z.string(),
  description: z.string().nullable(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
});

export const dictItemSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictTypeId: z.string(),
  itemLabel: z.string(),
  itemValue: z.string(),
  color: z.string().nullable(),
  icon: z.string().nullable(),
  description: z.string().nullable(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
  extraJson: z.string().nullable(),
});

export const apiResponseSchema = <T extends z.ZodType>(schema: T) =>
  z.object({
    code: z.number(),
    message: z.string().optional(),
    data: schema,
  });

export type DictType = z.infer<typeof dictTypeSchema>;
export type DictItem = z.infer<typeof dictItemSchema>;
```

```ts
// web/portal/src/features/dictionaries/api.ts
import axios from "axios";
import { z } from "zod";
import {
  apiResponseSchema,
  dictItemSchema,
  dictTypeSchema,
  type DictItem,
  type DictType,
} from "./data/schema";

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

export async function listDictTypes(): Promise<DictType[]> {
  const { data } = await http.get("/api/platform/dictionaries");
  return apiResponseSchema(z.array(dictTypeSchema)).parse(data).data;
}

export async function listDictItems(
  dictCode: string,
  includeDisabled = false,
): Promise<DictItem[]> {
  const { data } = await http.get(
    `/api/platform/dictionaries/${dictCode}/items`,
    { params: { includeDisabled } },
  );
  return apiResponseSchema(z.array(dictItemSchema)).parse(data).data;
}
```

## 后端单元测试重点

```java
// modules/aiops-platform/src/test/java/io/aegisops/platform/dictionary/DictionaryServiceTest.java
@Test
void disabledItemsAreHiddenByDefaultButAvailableForHistoryDisplay() {
  DictionaryRepository repository = new InMemoryDictionaryRepository();
  DictionaryService service = new DictionaryService(repository, AuditSink.noop());

  service.createType("tenant-a", new CreateDictTypeRequest("record_priority", "优先级", null, 0, true), "admin");
  DictItemRecord item = service.createItem(
      "tenant-a",
      "record_priority",
      new CreateDictItemRequest("P2", "P2", null, null, null, null, 20, true),
      "admin");

  service.updateItem(
      "tenant-a",
      "record_priority",
      item.id(),
      new UpdateDictItemRequest(null, null, null, null, null, false, null, null));

  assertThat(service.listItems("tenant-a", "record_priority")).isEmpty();
  assertThat(service.listItems("tenant-a", "record_priority", true))
      .extracting(DictItemRecord::itemValue)
      .containsExactly("P2");
}
```

## 前端单元测试

```ts
// web/portal/src/features/dictionaries/api.test.ts
import { describe, expect, it, vi } from "vitest";
import axios from "axios";
import { listDictItems } from "./api";

vi.mock("axios", () => ({
  default: {
    create: () => ({
      get: vi.fn().mockResolvedValue({
        data: {
          code: 0,
          data: [
            {
              id: "item-1",
              tenantId: "tenant-a",
              dictTypeId: "type-1",
              itemLabel: "P2",
              itemValue: "P2",
              color: null,
              icon: null,
              description: null,
              systemBuiltin: false,
              enabled: true,
              sortOrder: 20,
              extraJson: null,
            },
          ],
        },
      }),
    }),
  },
}));

describe("dictionary api", () => {
  it("parses dictionary items", async () => {
    const items = await listDictItems("record_priority");
    expect(items).toHaveLength(1);
    expect(items[0]?.itemValue).toBe("P2");
  });
});
```

## 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-platform -am test
cd web/portal && pnpm run test
```

验收标准：

- 默认字典可幂等初始化。
- 禁用项默认不返回，历史展示可通过 `includeDisabled=true` 返回。
- 字典 feature 不依赖 work-record 私有 API。

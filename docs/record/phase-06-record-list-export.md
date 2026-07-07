---
title: 工作记录 Phase 06 列表筛选与导出
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 06：记录列表、筛选与导出

## 目标

交付记录列表：

- TanStack Table。
- URL search 管分页、模板、状态、关键字、动态字段筛选。
- 后端强制分页，默认 20，最大 100。
- 自定义字段筛选只允许 `filterable=true` 字段。
- 导出当前筛选结果，限制最大行数，不把 JWT 放 URL。

## 后端补齐点

`WorkRecordRepository` 不能固定 `limit 200`，必须接受分页参数：

```text
page 默认 1
size 默认 20
max size 100
```

动态字段筛选流程：

```text
读取 template fields
校验 field_code 存在
校验 filterable=true
按 field_type 构造 JSONB 查询
使用参数绑定，不拼接 value
```

## 前端文件

```text
web/portal/src/features/work-records/components/records-table.tsx
web/portal/src/features/work-records/components/records-columns.tsx
web/portal/src/features/work-records/components/records-toolbar.tsx
web/portal/src/features/work-records/components/export-records-dialog.tsx
web/portal/src/features/work-records/api/work-record-api.ts
web/portal/src/features/work-records/api/export-api.ts
web/portal/src/features/work-records/hooks/use-records.ts
```

## 前端 API 完整代码

```ts
// web/portal/src/features/work-records/api/work-record-api.ts
import axios from "axios";
import { z } from "zod";

const recordSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  title: z.string(),
  status: z.string(),
  ownerId: z.string().nullable(),
  creatorId: z.string(),
  recordTime: z.string(),
  builtinDataJson: z.string(),
  customDataJson: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const pageSchema = z.object({
  items: z.array(recordSchema),
  page: z.number(),
  pageSize: z.number(),
  total: z.number(),
});

const apiResponseSchema = z.object({
  code: z.number(),
  message: z.string().optional(),
  data: pageSchema,
});

export type WorkRecordListParams = {
  page: number;
  pageSize: number;
  templateId?: string;
  status?: string[];
  keyword?: string;
  filters?: Record<string, string | string[]>;
};

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });

export async function listWorkRecords(params: WorkRecordListParams) {
  const { data } = await http.get("/api/work-record/records", { params });
  return apiResponseSchema.parse(data).data;
}
```

## 表格完整代码骨架

```tsx
// web/portal/src/features/work-records/components/records-table.tsx
import { useEffect, useState } from "react";
import {
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnFiltersState,
  type SortingState,
  type VisibilityState,
} from "@tanstack/react-table";
import { DataTablePagination, DataTableToolbar } from "@/components/data-table";
import { useTableUrlState } from "@/hooks/use-table-url-state";
import { recordsColumns, type WorkRecordRow } from "./records-columns";

type RecordsTableProps = {
  data: WorkRecordRow[];
  search: Record<string, unknown>;
  navigate: (options: { search: Record<string, unknown> }) => void;
};

export function RecordsTable({ data, search, navigate }: RecordsTableProps) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [rowSelection, setRowSelection] = useState({});

  const {
    columnFilters,
    onColumnFiltersChange,
    pagination,
    onPaginationChange,
    ensurePageInRange,
  } = useTableUrlState({
    search,
    navigate,
    pagination: { defaultPage: 1, defaultPageSize: 20 },
    globalFilter: { enabled: false },
    columnFilters: [
      { columnId: "title", searchKey: "keyword", type: "string" },
      { columnId: "status", searchKey: "status", type: "array" },
      { columnId: "templateId", searchKey: "templateId", type: "string" },
    ],
  });

  const table = useReactTable({
    data,
    columns: recordsColumns,
    state: {
      sorting,
      columnVisibility,
      rowSelection,
      columnFilters,
      pagination,
    },
    enableRowSelection: true,
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    onColumnFiltersChange: onColumnFiltersChange as (
      updater: ColumnFiltersState,
    ) => void,
    onPaginationChange,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  useEffect(() => {
    ensurePageInRange(table.getPageCount());
  }, [ensurePageInRange, table]);

  return (
    <div className="flex flex-col gap-4">
      <DataTableToolbar table={table} />
      <div className="rounded-md border">{/* portal Table rows */}</div>
      <DataTablePagination table={table} />
    </div>
  );
}
```

## 单元测试

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordServiceTest.java
@Test
void rejectsFilterOnNonFilterableField() {
  WorkRecordService service = newServiceWithFields(
      field("process_result", "select", false, "static", null, "[]"));

  assertThatThrownBy(
          () ->
              service.list(
                  "tenant-a",
                  currentUser(),
                  new WorkRecordQuery("template-a", 1, 20, Map.of("process_result", "ok"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not filterable");
}
```

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordExportServiceTest.java
@Test
void exportIsLimited() {
  WorkRecordExportService service = new WorkRecordExportService(repositoryWithRows(10001), 5000);

  assertThatThrownBy(() -> service.exportCsv("tenant-a", admin(), WorkRecordQuery.defaultQuery()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("export row limit");
}
```

## 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
cd web/portal && pnpm run lint && pnpm run test && pnpm run build
```

验收标准：

- 列表分页由 URL search 驱动。
- `filterable=false` 字段无法筛选。
- 导出不使用 query token。
- 导出超限失败并提示。

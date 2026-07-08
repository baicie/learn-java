---
title: 工作记录 Phase 06 列表筛选与导出
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/index.md
  - docs/record/phase-02a-platform-calendar.md
  - docs/record/phase-03-work-record-template.md
  - docs/record/phase-05-record-runtime.md
---

# Phase 06：记录列表、动态筛选与导出

## 1. 阶段目标

本阶段交付“记录可查、可筛、可导出”的运营闭环。Phase 05 解决单条记录如何填写与查看，Phase 06 解决大量记录如何被管理员、记录负责人和普通用户高效检索。

第一版必须做到：

```text
1. 使用 TanStack Table 呈现工作记录列表。
2. 列表状态全部进入 URL search，刷新、复制链接、返回上一页都可恢复。
3. 后端强制分页，默认 20，最大 100。
4. 支持内置字段筛选：模板、状态、标题关键字、记录时间、创建人、负责人。
5. 支持自定义字段筛选，但只允许 filterable=true 的字段。
6. 支持按模板字段生成动态列，但仅展示 list_visible=true 的字段。
7. 支持导出当前筛选结果，导出请求不通过 query token 暴露身份凭据。
8. 导出必须有行数上限、权限校验、审计日志。
```

如果 Phase 02A 工作日历已落地，本阶段可以消费其公开 API 增加快捷筛选与导出摘要：

```text
1. 本工作周。
2. 最近 5 个工作日。
3. 月报导出中的本月工作日、已填写、缺失、节假日数量。
```

这些能力是增强项，不是列表和导出的硬依赖。

本阶段不做：

```text
1. 不做异步导出任务中心。
2. 不做跨模板混合自定义列聚合分析。
3. 不做报表透视、图表统计、BI 看板。
4. 不做保存筛选视图。
5. 不做 Excel 复杂样式模板。
```

这些能力都可以在 Phase 07 收口后作为独立增强项规划。

## 2. 产品体验

### 2.1 列表页面布局

页面路径：

```text
/work-records
```

页面结构：

```text
WorkRecordsPage
  WorkRecordsToolbar
    TemplateSelect
    StatusFacets
    KeywordSearch
    RecordTimeRangePicker
    DynamicFilterButton
    ColumnVisibilityButton
    ExportButton
  WorkRecordsTable
  WorkRecordsPagination
```

桌面端：

```text
顶部工具栏一行承载常用筛选。
动态字段筛选放入右侧 Sheet。
列显隐使用 DropdownMenu。
导出使用 Dialog 二次确认。
```

移动端：

```text
模板、状态、关键字保留在顶部。
时间范围、动态字段、列显隐进入筛选 Sheet。
表格横向滚动，首列标题固定优先展示。
```

### 2.2 列表列设计

固定列：

```text
title           记录标题
templateName    模板
status          状态
recordTime      记录时间
ownerName       负责人
creatorName     创建人
updatedAt       更新时间
actions         操作
```

动态列：

```text
来源：wr_template_field
条件：list_visible=true
顺序：sort_order 升序
展示名：label
取值：custom_data_json[field_code]
```

字段展示规则：

```text
input/textarea       原值展示，过长省略，悬停 Tooltip 展示完整值
number               按数字展示，不做货币格式假设
date/datetime        按当前 locale 格式化
select/radio         使用字典 label 或 static options label
multi_select         多个 Badge 展示，超过 3 个折叠
checkbox             true/false 映射为是/否
user                 展示用户 displayName，缺失时展示 ID
```

### 2.3 URL search 契约

URL search 是前端列表状态的单一来源。

```text
page=1
pageSize=20
templateId=tpl_001
status=submitted,draft
keyword=巡检
recordTimeFrom=2026-07-01T00:00:00+08:00
recordTimeTo=2026-07-08T23:59:59+08:00
filters=base64url(JSON)
columns=field_a,field_b,field_c
```

`filters` 使用 JSON 后 base64url 编码，避免复杂对象被 query string 打散：

```json
[
  {
    "fieldCode": "service_name",
    "operator": "contains",
    "value": "api"
  },
  {
    "fieldCode": "severity",
    "operator": "in",
    "values": ["p1", "p2"]
  }
]
```

前端必须做到：

```text
1. URL 参数解析失败时回退默认值，并提示筛选条件已重置。
2. 修改筛选条件后 page 重置为 1。
3. 列显隐只影响前端展示，不影响后端返回字段。
4. 导出使用当前 URL search 解析出的同一份 query state。
```

## 3. 后端接口设计

### 3.1 列表查询

```http
GET /api/work-record/records
```

查询参数：

```text
page                 number, default 1
pageSize             number, default 20, max 100
templateId           string, optional
status               string[], optional
keyword              string, optional
recordTimeFrom       datetime, optional
recordTimeTo         datetime, optional
creatorId            string, optional
ownerId              string, optional
filters              string, optional, base64url(JSON)
sort                 string, optional
```

响应：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "rec_001",
        "templateId": "tpl_001",
        "templateName": "日常巡检",
        "title": "数据库巡检记录",
        "status": "submitted",
        "recordTime": "2026-07-08T09:00:00+08:00",
        "ownerId": "user_001",
        "ownerName": "李雷",
        "creatorId": "user_002",
        "creatorName": "韩梅梅",
        "customData": {
          "service_name": "mysql-primary",
          "severity": "p2"
        },
        "createdAt": "2026-07-08T09:10:00+08:00",
        "updatedAt": "2026-07-08T09:20:00+08:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 127
  }
}
```

### 3.2 列表元数据

列表页需要模板、动态列、可筛选字段和字典 label。

```http
GET /api/work-record/records/list-metadata?templateId=tpl_001
```

响应：

```json
{
  "code": 0,
  "data": {
    "templates": [
      {
        "id": "tpl_001",
        "name": "日常巡检",
        "enabled": true
      }
    ],
    "columns": [
      {
        "fieldCode": "service_name",
        "label": "服务名称",
        "fieldType": "input",
        "listVisible": true,
        "filterable": true,
        "sortOrder": 10
      }
    ],
    "filterFields": [
      {
        "fieldCode": "severity",
        "label": "严重级别",
        "fieldType": "select",
        "operators": ["eq", "in"],
        "dictionaryCode": "incident_severity",
        "options": [
          { "value": "p1", "label": "P1" },
          { "value": "p2", "label": "P2" }
        ]
      }
    ]
  }
}
```

为什么单独设计 metadata 接口：

```text
1. 列表查询只负责数据，不把模板字段配置混入每页结果。
2. 前端切换 templateId 时可以独立刷新列与筛选器。
3. 字典 label 能在列表页统一解析，避免每行重复携带 options。
```

### 3.3 导出

导出使用 POST，不使用 GET：

```http
POST /api/work-record/records/export
Content-Type: application/json
```

请求体：

```json
{
  "templateId": "tpl_001",
  "status": ["submitted"],
  "keyword": "巡检",
  "recordTimeFrom": "2026-07-01T00:00:00+08:00",
  "recordTimeTo": "2026-07-08T23:59:59+08:00",
  "filters": [
    {
      "fieldCode": "severity",
      "operator": "in",
      "values": ["p1", "p2"]
    }
  ],
  "columns": ["title", "recordTime", "severity"],
  "format": "csv"
}
```

响应：

```http
200 OK
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="work-records-20260708.csv"
```

第一版只支持 CSV。后续如要支持 XLSX，必须独立评估内存占用、单元格注入防护、异步任务与文件生命周期。

## 4. 查询语法与安全规则

### 4.1 动态字段筛选语法

```text
eq          等于
in          多值包含
contains    文本包含
gte         大于等于
lte         小于等于
between     区间
exists      有值
```

字段类型允许的 operator：

```text
input/textarea       contains, eq, exists
number               eq, gte, lte, between, exists
date/datetime        eq, gte, lte, between, exists
select/radio         eq, in, exists
multi_select         in, exists
checkbox             eq, exists
user                 eq, in, exists
```

后端校验顺序：

```text
1. 解析 filters JSON，失败直接 400。
2. templateId 为空时拒绝动态字段筛选。
3. 根据 tenantId + templateId 读取字段索引。
4. 校验 fieldCode 存在。
5. 校验 filterable=true。
6. 校验 operator 与 fieldType 兼容。
7. 校验 value 类型与字段类型兼容。
8. 生成参数化查询。
```

### 4.2 SQL 与 JSONB 查询规则

动态字段存储在 `custom_data_json`。查询必须遵守：

```text
1. tenant_id 必须出现在所有查询条件中。
2. template_id 必须在动态字段查询时出现在条件中。
3. field_code 只能来自 wr_template_field 白名单。
4. value 必须使用参数绑定。
5. 不允许把用户输入拼接进 SQL 片段。
6. JSONB 查询表达式由后端 operator builder 根据 fieldType 构造。
```

示例表达式只表达方向，实际实现必须通过参数绑定完成：

```sql
custom_data_json ->> :fieldCode = :value
custom_data_json ->> :fieldCode ILIKE :pattern
(custom_data_json ->> :fieldCode)::numeric >= :numberValue
```

### 4.3 权限数据范围

普通用户：

```text
creator_id = currentUserId OR owner_id = currentUserId
```

记录管理员：

```text
拥有 work-record:record:read:all 后，可查询租户内全部记录。
```

导出权限：

```text
必须拥有 work-record:export。
导出数据范围仍受 read:self / read:all 约束。
```

### 4.4 导出安全

导出必须满足：

```text
1. 不使用 URL token。
2. 不通过 GET 暴露复杂筛选条件。
3. 默认最大导出 5000 行，可通过配置调整但不能无上限。
4. 导出前先 count，超过上限直接拒绝。
5. CSV 字段必须做转义。
6. 以 =、+、-、@ 开头的单元格要加前缀防止公式注入。
7. 导出动作写审计，包含筛选摘要、行数、模板 ID、操作者。
```

## 5. 前端实现设计

### 5.1 文件落点

```text
web/portal/src/routes/_authenticated/work-records/index.tsx
web/portal/src/features/work-records/api/work-record-api.ts
web/portal/src/features/work-records/data/list-query-schema.ts
web/portal/src/features/work-records/data/record-list-schema.ts
web/portal/src/features/work-records/hooks/use-record-list-query.ts
web/portal/src/features/work-records/hooks/use-records.ts
web/portal/src/features/work-records/hooks/use-record-list-metadata.ts
web/portal/src/features/work-records/components/records-toolbar.tsx
web/portal/src/features/work-records/components/records-table.tsx
web/portal/src/features/work-records/components/records-columns.tsx
web/portal/src/features/work-records/components/dynamic-filter-sheet.tsx
web/portal/src/features/work-records/components/dynamic-filter-row.tsx
web/portal/src/features/work-records/components/column-visibility-menu.tsx
web/portal/src/features/work-records/components/export-records-dialog.tsx
```

### 5.2 前端状态模型

```ts
export type WorkRecordListQuery = {
  page: number
  pageSize: number
  templateId?: string
  status: string[]
  keyword?: string
  recordTimeFrom?: string
  recordTimeTo?: string
  filters: WorkRecordDynamicFilter[]
  columns: string[]
}

export type WorkRecordDynamicFilter = {
  fieldCode: string
  operator: 'eq' | 'in' | 'contains' | 'gte' | 'lte' | 'between' | 'exists'
  value?: string | number | boolean
  values?: Array<string | number | boolean>
}
```

### 5.3 Query Hook

`useRecordListQuery` 负责：

```text
1. 从 TanStack Router search 解析 query。
2. 使用 zod 校验并提供默认值。
3. 提供 setQuery 方法。
4. 筛选变化时自动重置 page=1。
5. 提供 toApiParams 与 toExportPayload。
```

禁止把 URL 解析逻辑散落在 toolbar/table/dialog 中。

### 5.4 Table 设计

TanStack Table 使用服务端分页：

```text
manualPagination=true
manualFiltering=true
manualSorting=true
pageCount=Math.ceil(total / pageSize)
```

排序第一版只支持固定字段：

```text
recordTime
createdAt
updatedAt
```

动态字段排序不在第一版做。原因是 JSONB 类型转换、索引策略、空值排序和不同字段类型规则都需要单独设计。

### 5.5 导出 Dialog

导出前展示：

```text
当前模板
当前筛选摘要
预计导出字段
导出格式 CSV
最大行数提示
```

交互规则：

```text
1. 点击导出先调用 count 或复用列表 total。
2. total 超过上限时禁用确认按钮。
3. 确认后调用 POST /export，使用 blob 下载。
4. 请求失败时显示后端 message。
5. 导出完成后不改变当前列表状态。
```

## 6. 后端实现设计

### 6.1 DTO

```java
public record WorkRecordListRequest(
    int page,
    int pageSize,
    String templateId,
    List<String> status,
    String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    String creatorId,
    String ownerId,
    List<DynamicFieldFilter> filters,
    String sort) {}

public record DynamicFieldFilter(
    String fieldCode,
    String operator,
    Object value,
    List<Object> values) {}

public record WorkRecordExportRequest(
    String templateId,
    List<String> status,
    String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    List<DynamicFieldFilter> filters,
    List<String> columns,
    String format) {}
```

### 6.2 Service 分层

```text
WorkRecordQueryService
  - list(tenantId, currentUser, request)
  - metadata(tenantId, templateId)
  - countForExport(tenantId, currentUser, request)

WorkRecordFilterValidator
  - validate(template, fields, filters)

WorkRecordQueryBuilder
  - buildWhereClause(validatedQuery)
  - buildParameters(validatedQuery)

WorkRecordExportService
  - exportCsv(tenantId, currentUser, request)

CsvCellSanitizer
  - escape(value)
  - preventFormulaInjection(value)
```

Controller 只做 HTTP 协议适配，不拼 SQL，不做权限判断细节。

### 6.3 Repository

Repository 负责：

```text
1. 按 query builder 输出的条件查询分页列表。
2. 查询 total。
3. 查询导出数据。
4. 查询 list metadata 所需模板字段。
```

禁止：

```text
1. 固定 limit 200。
2. 在 Repository 中绕过权限数据范围。
3. 使用字符串拼接用户输入。
4. 返回未按租户过滤的数据。
```

## 7. 国际化

新增或补齐 key：

```text
workRecords.list.title
workRecords.list.description
workRecords.list.template
workRecords.list.status
workRecords.list.keyword
workRecords.list.recordTime
workRecords.list.dynamicFilters
workRecords.list.columnVisibility
workRecords.list.export
workRecords.list.empty
workRecords.list.filterReset
workRecords.export.title
workRecords.export.description
workRecords.export.format
workRecords.export.maxRows
workRecords.export.tooManyRows
workRecords.export.confirm
workRecords.export.success
workRecords.export.failed
workRecords.filters.operator.eq
workRecords.filters.operator.in
workRecords.filters.operator.contains
workRecords.filters.operator.gte
workRecords.filters.operator.lte
workRecords.filters.operator.between
workRecords.filters.operator.exists
```

只把用户可见文案放入 locale TS 文件。字段 code、API enum、permission code 不国际化。

## 8. 单元测试与集成测试

### 8.1 后端测试

```text
WorkRecordFilterValidatorTest
  - rejectsUnknownField
  - rejectsNonFilterableField
  - rejectsOperatorNotSupportedByFieldType
  - rejectsInvalidValueType
  - acceptsValidSelectInFilter
  - acceptsValidDateBetweenFilter

WorkRecordQueryServiceTest
  - normalUserOnlySeesOwnRecords
  - adminCanSeeTenantRecords
  - queryAlwaysRequiresTenantScope
  - paginationUsesRequestedPageAndPageSize
  - pageSizeCannotExceedMax

WorkRecordExportServiceTest
  - rejectsWithoutExportPermission
  - exportStillRespectsReadScope
  - rejectsWhenRowCountExceedsLimit
  - writesAuditLog
  - escapesCsvCells
  - preventsCsvFormulaInjection
```

### 8.2 前端测试

```text
useRecordListQuery.test.ts
  - parsesDefaultQuery
  - parsesDynamicFiltersFromUrl
  - resetsInvalidFilters
  - resetsPageWhenFilterChanges
  - buildsExportPayloadFromCurrentQuery

records-table.test.tsx
  - rendersBuiltinColumns
  - rendersDynamicVisibleColumns
  - hidesDynamicInvisibleColumns
  - callsQueryChangeWhenPaginationChanges

dynamic-filter-sheet.test.tsx
  - showsOnlyFilterableFields
  - changesAvailableOperatorsByFieldType
  - validatesRequiredFilterValue

export-records-dialog.test.tsx
  - disablesConfirmWhenTotalExceedsLimit
  - postsExportPayload
  - downloadsBlobOnSuccess
```

## 9. 完整验收标准

```text
1. 列表 URL search 能完整恢复分页、模板、状态、关键字、时间范围、动态筛选、列显隐。
2. 后端 pageSize 最大值生效，不能被前端绕过。
3. 普通用户只能看到自己创建或负责的记录。
4. 管理员能看到租户内全部记录。
5. filterable=false 的字段不能被动态筛选。
6. 未知 fieldCode 不能被动态筛选。
7. operator 与 fieldType 不匹配时返回 400。
8. 动态字段查询只在 templateId 明确时允许执行。
9. 导出不使用 query token。
10. 导出超限失败并给出可理解提示。
11. CSV 内容正确转义并防止公式注入。
12. 导出写审计日志。
13. 前端 lint 与相关单元测试通过。
14. 后端 work-record 相关测试通过。
```

## 10. 本阶段验证命令

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
pnpm --dir web/portal run lint
pnpm --dir web/portal run test
bash scripts/ci/docs.sh
```

如果 `web/portal` 仍存在模板自身的 build/test 环境问题，必须在交付说明中明确列出阻塞点、与本阶段变更的关系，以及已通过的替代验证。

---
title: 工作记录 API 契约
type: api
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-25
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/schema-contract.md
  - docs/record/permission-contract.md
---

# 工作记录 API 契约

## 1. 统一响应

所有 JSON API 使用统一响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

分页响应：

```json
{
  "total": 0,
  "page": 1,
  "pageSize": 20,
  "items": []
}
```

## 2. 字典 API

```
GET    /api/platform/dictionaries
POST   /api/platform/dictionaries
GET    /api/platform/dictionaries/{dictCode}
PUT    /api/platform/dictionaries/{dictCode}
DELETE /api/platform/dictionaries/{dictCode}

GET    /api/platform/dictionaries/{dictCode}/items
POST   /api/platform/dictionaries/{dictCode}/items
PUT    /api/platform/dictionaries/{dictCode}/items/{itemId}
DELETE /api/platform/dictionaries/{dictCode}/items/{itemId}
```

约束：

1. DELETE 语义为 enabled=false。
2. 字典项不可物理删除。
3. listItems 支持 includeDisabled。
4. 写入记录时只允许 enabled=true 的字典项。
5. 历史记录展示时允许 includeDisabled=true。

## 3. 工作日历 API

```
GET    /api/platform/calendars
GET    /api/platform/calendars/default?year=2026
PUT    /api/platform/calendars/{calendarId}/default

GET    /api/platform/calendars/{calendarId}/days?start=2026-01-01&end=2026-12-31
PUT    /api/platform/calendars/{calendarId}/days/{date}

GET    /api/platform/calendars/import-template?year=2026
POST   /api/platform/calendars/{calendarId}/days/import

GET    /api/platform/calendar-days/check?calendarId={calendarId}&date=2026-07-09
GET    /api/platform/calendar-days/range?calendarId={calendarId}&start=2026-07-01&end=2026-07-31
GET    /api/platform/calendar-days/workdays/count?calendarId={calendarId}&start=2026-07-01&end=2026-07-31
```

约束：

1. 所有查询按 tenantId 隔离。
2. 平台为每个租户自动初始化 2000–2050 年共 51 个年度日历，不提供创建年度日历的 API。
3. 每个年度默认包含完整日期；周一至周五为工作日，周六、周日为周末。
4. 模板下载和导入均要求 `platform:calendar:import` 权限。
5. XLSX 导入只接收法定节假日，导入后固定写入 `dayType=HOLIDAY`、`isWorkday=false`，不接受普通工作日、周末或调休工作日。
6. 导入请求使用 `multipart/form-data`，文件字段名为 `file`，仅允许 `.xlsx`，最大 5 MB、最多 1000 个非空数据行。
7. 导入日期必须属于所选日历年度，同一文件内日期不可重复。
8. 单日覆盖和 XLSX 导入必须写审计。
9. 工作日判断以 `platform_calendar_day.is_workday` 为准。

XLSX 模板固定为以下三列：

| 列名          | 必填 | 说明                         |
| ------------- | ---- | ---------------------------- |
| `date`        | 是   | 日期，格式为 `YYYY-MM-DD`    |
| `holidayName` | 是   | 法定节假日名称               |
| `remark`      | 否   | 备注                         |

模板下载返回 XLSX 二进制文件。导入成功时，统一 JSON 响应中的 `data` 为已处理的节假日行数。

## 4. 模板 API

```
GET    /api/work-record/templates
POST   /api/work-record/templates
GET    /api/work-record/templates/{templateId}
PUT    /api/work-record/templates/{templateId}
DELETE /api/work-record/templates/{templateId}

POST   /api/work-record/templates/{templateId}/draft
POST   /api/work-record/templates/{templateId}/publish
POST   /api/work-record/templates/{templateId}/copy

GET    /api/work-record/templates/{templateId}/versions
GET    /api/work-record/templates/{templateId}/versions/{versionId}

GET    /api/work-record/templates/{templateId}/fields
```

约束：

1. 模板删除语义为 disabled 或 archived。
2. 发布模板必须生成 template_version。
3. 记录必须绑定 template_version_id。
4. 已发布版本不可修改。
5. 草稿可编辑。
6. 已被记录引用的 fieldCode 不可修改。

## 5. 记录 API

```
GET    /api/work-record/records
POST   /api/work-record/records
GET    /api/work-record/records/{recordId}
PUT    /api/work-record/records/{recordId}
DELETE /api/work-record/records/{recordId}

POST   /api/work-record/records/{recordId}/submit
POST   /api/work-record/records/{recordId}/archive
POST   /api/work-record/records/export
```

记录创建请求：

```json
{
  "templateId": "tpl_001",
  "templateVersionId": "tplv_001",
  "title": "每日工作记录",
  "status": "draft",
  "ownerId": "user_001",
  "recordTime": "2026-07-09T10:00:00+09:00",
  "customData": {
    "record_type": "daily",
    "work_content": "处理服务器巡检异常",
    "priority": "P2"
  }
}
```

约束：

1. tenantId 从登录上下文取，不信任前端。
2. creatorId 从登录上下文取，不信任前端。
3. customData 只能包含模板字段定义内的 enabled 字段。
4. 记录删除语义为 deleted_at。
5. 普通用户只能读取 creatorId=自己 或 ownerId=自己的记录。
6. record manager 和 admin 可读取全部记录。

## 6. 列表查询参数

```
page
pageSize
templateId
templateVersionId
status
keyword
recordTimeFrom
recordTimeTo
creatorId
ownerId
filters
```

filters 示例：

```json
[
  {
    "fieldCode": "priority",
    "operator": "in",
    "values": ["P0", "P1"]
  }
]
```

约束：

1. 使用动态字段筛选时必须指定 templateId。
2. fieldCode 必须存在。
3. fieldCode 必须 filterable=true。
4. operator 必须在字段类型允许范围内。
5. value 类型必须匹配字段类型。
6. JSONB 查询必须参数化，禁止直接拼接字段名。

## 7. 导出 API

```
POST /api/work-record/records/export
```

请求：

```json
{
  "templateId": "tpl_001",
  "status": ["done"],
  "keyword": "巡检",
  "filters": [],
  "columns": ["title", "status", "priority", "work_content"],
  "format": "csv"
}
```

约束：

1. 第一版只支持 csv。
2. 默认最多导出 5000 行。
3. 导出必须使用当前筛选条件。
4. 动态列必须 exportable=true。
5. CSV 必须防公式注入。
6. 导出必须写审计。

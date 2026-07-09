---
title: 工作记录权限契约
type: api
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-09
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/api-contract.md
---

# 工作记录权限契约

## 1. 平台权限

```
platform:dict:read
platform:dict:write

platform:calendar:read
platform:calendar:write
platform:calendar:import
```

## 2. 工作记录权限

```
work-record:template:read
work-record:template:write

work-record:read:self
work-record:read:all

work-record:write
work-record:delete
work-record:export
```

## 3. 角色

### 系统管理员

拥有全部权限。

### 记录管理员

```
platform:dict:read
platform:calendar:read

work-record:template:read
work-record:template:write
work-record:read:all
work-record:write
work-record:delete
work-record:export
```

### 普通用户

```
work-record:read:self
work-record:write
```

### 只读用户

```
work-record:read:self
```

## 4. 数据范围

普通用户：

```
creator_id = currentUserId
or owner_id = currentUserId
```

记录管理员：可读全部记录。

系统管理员：可读全部记录。

## 5. API 权限映射

```
GET /api/platform/dictionaries
  platform:dict:read

POST /api/platform/dictionaries
PUT /api/platform/dictionaries/{dictCode}
DELETE /api/platform/dictionaries/{dictCode}
POST /api/platform/dictionaries/{dictCode}/items
PUT /api/platform/dictionaries/{dictCode}/items/{itemId}
DELETE /api/platform/dictionaries/{dictCode}/items/{itemId}
  platform:dict:write

GET /api/platform/calendars
GET /api/platform/calendar-days/*
  platform:calendar:read

POST /api/platform/calendars
PUT /api/platform/calendars/{calendarId}
DELETE /api/platform/calendars/{calendarId}
PUT /api/platform/calendars/{calendarId}/days/{date}
  platform:calendar:write

POST /api/platform/calendars/{calendarId}/days/import
  platform:calendar:import

GET /api/work-record/templates
GET /api/work-record/templates/{templateId}
GET /api/work-record/templates/{templateId}/fields
  work-record:template:read

POST /api/work-record/templates
PUT /api/work-record/templates/{templateId}
DELETE /api/work-record/templates/{templateId}
POST /api/work-record/templates/{templateId}/draft
POST /api/work-record/templates/{templateId}/publish
POST /api/work-record/templates/{templateId}/copy
  work-record:template:write

GET /api/work-record/records
GET /api/work-record/records/{recordId}
  work-record:read:self or work-record:read:all

POST /api/work-record/records
PUT /api/work-record/records/{recordId}
POST /api/work-record/records/{recordId}/submit
POST /api/work-record/records/{recordId}/archive
  work-record:write

DELETE /api/work-record/records/{recordId}
  work-record:delete

POST /api/work-record/records/export
  work-record:export
```

## 6. 前端权限

1. 菜单显示由权限控制。
2. 按钮显示由权限控制。
3. 前端隐藏不是安全边界。
4. 后端必须再次校验权限。

## 7. 审计要求

必须写审计：

```
platform.dict.type.create
platform.dict.type.update
platform.dict.item.create
platform.dict.item.update

platform.calendar.create
platform.calendar.update
platform.calendar.day.update
platform.calendar.import

work_record.template.create
work_record.template.update
work_record.template.publish
work_record.template.disable
work_record.field.create
work_record.field.update
work_record.field.disable
work_record.record.create
work_record.record.update
work_record.record.delete
work_record.record.export
```

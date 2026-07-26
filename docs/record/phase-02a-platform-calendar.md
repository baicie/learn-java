---
title: 工作记录 Phase 02A 平台工作日历
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-25
related:
  - docs/record/index.md
  - docs/record/phase-02-platform-dictionary.md
  - docs/record/phase-05-record-runtime.md
  - docs/record/phase-06-record-list-export.md
---

# Phase 02A：平台工作日历基础能力

## 1. 阶段定位

工作日历有必要引入，但不应该成为工作记录第一版核心页面。

本 Phase 的最佳定位：

```text
aiops-platform/calendar 轻量基础能力
工作记录可消费，但不拥有它
平台自动生成基础日历，用户只导入法定节假日例外
Portal 提供年度查看、模板下载和 XLSX 导入
```

为什么需要工作日历：

```text
1. 日报是否必须填写，不能只按自然日判断。
2. 周报、月报统计需要区分自然日与工作日。
3. 中国大陆存在节假日和调休，周末可能是工作日，工作日可能是假期。
4. 企业还会有公司额外假期、封版日、团建日、提前放假等自定义日期。
5. 后续巡检任务、值班排班、SLA、告警静默、执行计划也会复用。
```

因此它必须放在 platform：

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/calendar
```

不要放在：

```text
modules/aiops-work-record
```

## 2. 第一版范围

第一版必须做：

```text
1. 平台工作日历表。
2. 平台工作日历日期表。
3. 工作日判断 API。
4. 日期范围 API。
5. 工作日数量统计 API。
6. XLSX 法定节假日导入能力。
7. 自动生成 2000–2050 年 CN 年度日历，并提供导入模板。
8. 租户级自定义覆盖。
9. 权限、租户隔离、审计。
```

当前页面能力：

```text
1. /platform/calendars 页面。
2. 表格视图维护日期。
3. XLSX 模板下载与法定节假日导入 Dialog。
```

第一版不做：

```text
1. 不做漂亮月历拖拽。
2. 不做排班系统。
3. 不做自动爬取官方节假日。
4. 不做复杂节假日规则引擎。
5. 不做 ICS 导入。
6. 不做跨国家复杂区域继承。
7. 不把未填写日报逻辑做进本 Phase。
```

严格控范围时，本 Phase 只交付：

```text
platform_calendar
platform_calendar_day
CalendarService
CalendarController
XLSX holiday import
unit tests
```

## 3. 领域模型

### 3.1 Calendar

`Calendar` 表示某个租户在某个地区、年份、时区下的一份工作日历。

核心字段：

```text
tenantId
calendarCode
calendarName
regionCode
timezone
year
enabled
sourceType
description
```

示例：

```text
calendarCode: CN_2026
calendarName: 中国大陆 2026 工作日历
regionCode: CN
timezone: Asia/Shanghai
year: 2026
sourceType: generated
```

### 3.2 CalendarDay

`CalendarDay` 表示某一天在该日历中是否是工作日。

核心字段：

```text
calendarDate
dayOfWeek
dayType
isWorkday
holidayCode
holidayName
sourceType
remark
```

核心判断只暴露一个稳定问题：

```text
这一天是不是工作日？
```

业务模块不要到处写：

```text
周一到周五就是工作日
周六周日就是假期
```

## 4. dayType 枚举

```text
WORKDAY              普通工作日
WEEKEND              普通周末
HOLIDAY              法定/地区假期
ADJUSTED_WORKDAY     调休工作日
COMPANY_HOLIDAY      公司额外假期
COMPANY_WORKDAY      公司额外工作日
```

`isWorkday` 是业务判断字段。

`dayType` 是解释字段。

映射规则：

```text
WORKDAY              isWorkday=true
ADJUSTED_WORKDAY     isWorkday=true
COMPANY_WORKDAY      isWorkday=true
WEEKEND              isWorkday=false
HOLIDAY              isWorkday=false
COMPANY_HOLIDAY      isWorkday=false
```

允许管理员覆盖单日时同时修改 `dayType` 和 `isWorkday`，但后端必须校验二者兼容。

## 5. 数据库设计

### 5.1 platform_calendar

```sql
create table platform_calendar (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  calendar_code varchar(64) not null,
  calendar_name varchar(128) not null,
  region_code varchar(32) not null default 'CN',
  timezone varchar(64) not null default 'Asia/Shanghai',
  year integer not null,
  enabled boolean not null default true,
  source_type varchar(32) not null default 'manual',
  description text,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, calendar_code),
  unique (tenant_id, region_code, year)
);
```

索引：

```sql
create index idx_platform_calendar_tenant_year
  on platform_calendar (tenant_id, year);

create index idx_platform_calendar_tenant_enabled
  on platform_calendar (tenant_id, enabled);
```

### 5.2 platform_calendar_day

```sql
create table platform_calendar_day (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  calendar_id varchar(64) not null,
  calendar_date date not null,
  day_of_week integer not null,
  day_type varchar(32) not null,
  is_workday boolean not null,
  holiday_code varchar(64),
  holiday_name varchar(128),
  source_type varchar(32) not null default 'manual',
  remark text,
  created_by varchar(64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, calendar_id, calendar_date)
);
```

索引：

```sql
create index idx_platform_calendar_day_tenant_date
  on platform_calendar_day (tenant_id, calendar_date);

create index idx_platform_calendar_day_calendar_date
  on platform_calendar_day (tenant_id, calendar_id, calendar_date);

create index idx_platform_calendar_day_workday
  on platform_calendar_day (tenant_id, calendar_id, is_workday, calendar_date);
```

说明：

```text
1. 第一版不强制数据库外键，保持与现有模块风格一致；Service 层校验 calendar 存在。
2. tenant_id 必须冗余到 day 表，便于租户隔离查询和索引。
3. calendar_date 使用 date，不使用 timestamptz。
4. timezone 属于 Calendar，不属于每一条 CalendarDay。
```

## 6. API 设计

### 6.1 日历管理 API

```http
GET    /api/platform/calendars
GET    /api/platform/calendars/default?year=2026
PUT    /api/platform/calendars/{calendarId}/default
```

年度范围：

```text
平台为每个租户一次性初始化 2000–2050 年共 51 个年度日历。
不提供手工创建、编辑或删除年度日历的 API。
```

### 6.2 日期管理 API

```http
GET  /api/platform/calendars/{calendarId}/days?start=2026-01-01&end=2026-12-31
PUT  /api/platform/calendars/{calendarId}/days/{date}
GET  /api/platform/calendars/import-template?year=2026
POST /api/platform/calendars/{calendarId}/days/import
```

单日更新请求：

```json
{
  "dayType": "COMPANY_HOLIDAY",
  "isWorkday": false,
  "holidayCode": "COMPANY_ANNUAL_MEETING",
  "holidayName": "公司年会",
  "remark": "公司统一放假"
}
```

### 6.3 工作日查询 API

```http
GET /api/platform/calendar-days/check?calendarId={calendarId}&date=2026-07-08
GET /api/platform/calendar-days/range?calendarId={calendarId}&start=2026-07-01&end=2026-07-31
GET /api/platform/calendar-days/workdays/count?calendarId={calendarId}&start=2026-07-01&end=2026-07-31
```

默认选择规则：

```text
1. 页面查询显式传 calendarId，并校验该日历属于当前租户。
2. 工作记录模块按 current tenant + year 使用默认日历绑定。
3. 2000–2050 年的默认日历由迁移和新租户触发器自动建立。
4. 默认绑定或日期覆盖不完整时返回配置错误，不静默使用跨租户或跨年度数据。
```

工作日判断响应：

```json
{
  "date": "2026-07-08",
  "isWorkday": true,
  "dayType": "WORKDAY",
  "holidayName": null,
  "source": "calendar"
}
```

工作日数量响应：

```json
{
  "start": "2026-07-01",
  "end": "2026-07-31",
  "workdays": 23,
  "nonWorkdays": 8,
  "source": "calendar"
}
```

## 7. XLSX 法定节假日导入

第一版使用 XLSX 模板导入法定节假日，不做自动爬取，也不让用户导入全年普通日期。

导入模板：

| 列名          | 必填 | 说明                      |
| ------------- | ---- | ------------------------- |
| `date`        | 是   | 日期，格式 `YYYY-MM-DD`   |
| `holidayName` | 是   | 法定节假日名称            |
| `remark`      | 否   | 备注                      |

导入规则：

```text
1. date 必须属于 calendar.year。
2. holidayName 必填；remark 可选。
3. 同一天重复出现时拒绝整批导入。
4. 导入采用事务，任意一行失败则整批失败。
5. 已存在日期时按 upsert 更新为 HOLIDAY、isWorkday=false。
6. 只允许 .xlsx，最大 5 MB，最多 1000 个非空数据行。
7. 导入结果写审计，记录成功行数、变更摘要、calendarId 和文件 SHA-256。
```

普通工作日、周末和调休工作日不通过 XLSX 导入；需要特殊覆盖时使用单日维护 API。

## 8. 后端实现落点

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/calendar/
├─ Calendar.java
├─ CalendarDay.java
├─ CalendarController.java
├─ CalendarService.java
├─ CalendarRepository.java
├─ CalendarXlsxImporter.java
├─ UpdateCalendarDayRequest.java
├─ WorkdayCheckResponse.java
└─ WorkdayCountResponse.java
```

Flyway migration：

```text
apps/aiops-server/src/main/resources/db/migration/V00XX__init_platform_calendar.sql
```

`V00XX` 按当前最大 migration 号递增，不能占用已存在版本。

## 9. 前端页面设计

页面路径：

```text
/platform/calendars
```

文件落点：

```text
web/portal/src/routes/_authenticated/platform/calendars.tsx
web/portal/src/pages/calendars/index.tsx
web/portal/src/api/calendars.ts
web/portal/src/lib/calendars/calendar-helpers.ts
web/portal/src/components/calendars/calendar-import-dialog.tsx
```

页面结构：

```text
顶部：
  年份选择
  日历选择
  下载 XLSX 模板
  导入法定节假日

主体：
  月历视图与日期状态图例

月历单元格：
  日期与节假日名称
  工作日、周末、法定节假日和调休工作日状态
  有维护权限时可执行单日覆盖
  操作
```

第一版不做月历拖拽视图。

## 10. 工作记录如何消费

工作记录第一版不依赖工作日历才能运行。

允许消费点：

```text
1. 日报类模板：判断是否需要在工作日填写。
2. 列表快捷筛选：本工作周、最近 5 个工作日。
3. 月报导出：展示本月工作日、已填写、缺失、节假日数量。
4. 后续统计：按自然日 / 工作日 / 节假日统计。
```

第一版不做强制缺失判定。

也就是：

```text
工作日历可以增强工作记录统计
但不能阻塞模板设计、记录填写、列表导出主闭环
```

## 11. 权限与审计

权限：

```text
platform:calendar:read
platform:calendar:write
platform:calendar:import
```

审计动作：

```text
platform.calendar.default.change
platform.calendar.day.override
platform.calendar.day.import_create
platform.calendar.day.import_overwrite
platform.calendar.import
```

审计摘要：

```text
calendarId
calendarCode
year
regionCode
changedDate
importRowCount
sourceType
```

不要在审计中保存完整 XLSX 内容。

## 12. 国际化

新增 key：

```text
platform.calendars.title
platform.calendars.description
platform.calendars.year
platform.calendars.region
platform.calendars.import
platform.calendars.downloadTemplate
platform.calendars.date
platform.calendars.dayOfWeek
platform.calendars.dayType
platform.calendars.isWorkday
platform.calendars.holidayName
platform.calendars.remark
platform.calendars.dayType.WORKDAY
platform.calendars.dayType.WEEKEND
platform.calendars.dayType.HOLIDAY
platform.calendars.dayType.ADJUSTED_WORKDAY
platform.calendars.dayType.COMPANY_HOLIDAY
platform.calendars.dayType.COMPANY_WORKDAY
```

工作记录侧新增可选 key：

```text
workRecords.filters.thisWorkWeek
workRecords.filters.lastFiveWorkdays
workRecords.export.workdaySummary
```

## 13. 测试策略

后端：

```text
CalendarServiceTest
  - listsGeneratedCalendarsForTenant
  - rejectsTemplateYearOutsideSupportedRange
  - importsStatutoryHolidays
  - writesImportAudit

CalendarDayServiceTest
  - updatesSingleDay
  - rejectsIncompatibleDayTypeAndIsWorkday
  - rejectsDateOutsideCalendarYear
  - checksWorkdayByDate
  - countsWorkdaysInRange
  - rejectsMissingOrIncompleteDefaultCalendar

CalendarXlsxImporterTest
  - importsValidXlsx
  - rejectsDuplicateDateInXlsx
  - rejectsInvalidHeaders
  - rejectsDateOutsideYear
  - upsertsExistingDay
  - rejectsOversizedImport

CalendarTenantIsolationTest
  - cannotReadCalendarFromAnotherTenant
  - cannotUpdateDayFromAnotherTenant
  - rangeQueryNeverReturnsAnotherTenantDays
```

前端页面可选测试：

```text
calendar-api.test.ts
calendar-schema.test.ts
calendar-day-table.test.tsx
calendar-import-dialog.test.tsx
```

## 14. 验收标准

```text
1. 每个租户自动拥有 2000–2050 年共 51 个 CN_YYYY 工作日历及完整基础日期。
2. 能下载 XLSX 模板并仅导入法定节假日。
3. 能查询某一天是否工作日。
4. 能查询日期范围内每天的工作日状态。
5. 能统计范围内工作日数量。
6. 能覆盖某一天为公司假期或公司工作日。
7. 所有查询按 tenantId 隔离。
8. 导入和单日更新写审计。
9. 缺少默认绑定或日期覆盖不完整时返回明确的配置错误。
10. 工作记录模块不依赖 calendar 包内部实现，只通过公开 service/API 消费。
```

## 15. 验证命令

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-platform -am test
pnpm --dir web/portal run lint
bash scripts/ci/docs.sh
```

如果第一版不实现前端页面，可以不运行 calendar 页面测试，但必须运行后端 calendar 单元测试。

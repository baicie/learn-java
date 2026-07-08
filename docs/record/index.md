---
title: 可配置工作记录模块（终版设计 · portal-first）
type: design
status: review
phase: work-record
owner: platform-team
created: 2026-07-07
updated: 2026-07-08
supersedes:
  - docs/record/2026-07-06-record-index-pre-portal.md
deprecated_for:
  reason: 早期基于 web/console 的同主题设计稿；规划、技术栈与目录结构均已被本文档取代，仅供历史回溯。
related:
  - docs/record/phase-00-baseline-and-contract.md
  - docs/record/phase-01-portal-i18n-and-shell.md
  - docs/record/phase-02-platform-dictionary.md
  - docs/record/phase-02a-platform-calendar.md
  - docs/record/phase-03-work-record-template.md
  - docs/record/phase-04-formily-designer.md
  - docs/record/phase-05-record-runtime.md
  - docs/record/phase-06-record-list-export.md
  - docs/record/phase-07-role-permission-hardening.md
  - docs/reviews/work-record/2026-07-06-work-record-implementation-review.md
  - docs/reviews/work-record/2026-07-07-work-record-final-review.md
  - docs/reviews/work-record/2026-07-07-work-record-rule-based-review.md
  - docs/adr/0005-work-record-designer-formily-core-only.md
---

# AI-Ops 可配置工作记录模块 · 更新终版设计（portal-first）

> 本文档是"可配置工作记录模块"在 AegisOps 转入 `web/portal` 主前端后的权威设计入口。本次更新将表单能力定稿为 **portal 原生字段设计器 + Formily-compatible schema + Formily runtime**，并补充 schema 扩展协议、字段索引同步与 portal 目录落点。Designable 仅作为后续高级设计器候选，不进入第一版必选路径。
>
> 历史：`docs/record/2026-07-06-record-index-pre-portal.md` 是基于 `web/console` 的同等设计，已被本文档 `supersedes`。
>
> 配套：`docs/record/phase-*.md`（逐 Phase 详细设计、代码落点与测试）以及 `docs/reviews/work-record/*`（实现与规则评审）继续阅读。旧的 `docs/record/work-record-phases-design-code.md` 已废弃，仅供历史回溯。

## 1. 最终定位

模块名称：

```text
工作记录
```

产品定位：

```text
用户管理 + 角色权限 + 字典管理 + 工作日历基础能力 + portal 原生表单设计器 + Formily 运行态 + 工作记录填写 + 记录列表筛选导出
```

它不是完整工单系统，也不是完整低代码平台，而是：

```text
面向运维中台的可配置工作记录系统
```

第一版核心目标：

```text
管理员配置字典
管理员拖拽设计工作记录表
用户填写工作记录
管理员查看 / 筛选 / 导出
```

---

## 2. 新版 UI 基础判断

当前新版 UI 在：

```text
web/portal
```

它来自 shadcn-admin 模板，`package.json` 中项目名为 `shadcn-admin`，技术栈包含 React、Vite、TanStack Router、TanStack Query、TanStack Table、Radix UI、React Hook Form、Zod、Zustand、Recharts、Tailwind CSS 等。

入口 `main.tsx` 使用 TanStack Router 的 `createRouter` 和生成的 `routeTree.gen`，并通过 `QueryClientProvider`、`ThemeProvider`、`FontProvider`、`DirectionProvider` 包裹应用。

Vite 配置里已经接入 `@tanstack/router-plugin/vite`，并开启 `autoCodeSplitting`。

当前登录后布局走 `/_authenticated` 文件路由，对应 `AuthenticatedLayout`。
布局内部包含 `AppSidebar`、`SidebarProvider`、`SidebarInset`。

菜单当前来自：

```text
web/portal/src/components/layout/data/sidebar-data.ts
```

也就是静态 sidebar 数据，不是旧版 `web/console` 那种后端动态菜单。

所以新版设计按下面方式落地：

```text
前端主线：web/portal
路由：TanStack Router 文件路由
菜单：第一版改 sidebar-data.ts 静态菜单
表格：TanStack Table
表单设计器：portal 原生字段设计器，生成 Formily-compatible schema
表单运行态：Formily
外层 UI：shadcn-admin / Radix / Tailwind
后端：aiops-server + Maven 多模块
```

### 2.1 此次切换的关键差异（portal vs console）

```text
旧版本基于 web/console
  - React Router（路由集中注册在 App.tsx）
  - 自封装 ThemeContext、DirectionContext
  - API 客户端集中在 web/console/src/api/client.ts
  - 菜单手写 nav.ts，没有 navGroups 概念

新版本基于 web/portal
  - TanStack Router 文件路由 + autoCodeSplitting
  - 已有 ThemeProvider / FontProvider / DirectionProvider
  - sidebar-data.ts 显式 navGroups（General / Pages / Other）
  - features/* 分目录、hooks/api/components 分层
  - 已默认走 React 19 / Vite / TS / Tailwind v4 / shadcn/ui
```

这意味着本模块所有页面均要作为 **web/portal 内的业务 feature** 实现，而不是 console：

```text
web/portal/src/routes/_authenticated/work-records/...
web/portal/src/routes/_authenticated/platform/...
web/portal/src/features/work-records/...
web/portal/src/features/dictionaries/...
```

`web/console` 自此版本起不再接受工作记录相关的新增业务代码，存量页面保留供回退与历史使用。

---

## 3. 前端是否需要微前端

结论：

```text
不需要微前端
```

不做：

```text
qiankun
module federation
iframe
独立子应用
```

原因：

```text
1. web/portal 已经是完整后台模板
2. 工作记录和用户、权限、字典强相关
3. 当前功能没有独立部署诉求
4. 微前端会增加登录态、权限、路由、样式隔离复杂度
```

最终前端形态：

```text
工作记录作为 web/portal 内部 feature
```

未来只有在这些条件出现后再考虑微前端：

```text
工作记录要单独卖
工作记录要独立部署
工作记录要嵌入多个系统
工作记录有独立团队维护
```

---

## 4. Formily / Designable 的最终取舍

最终方案：

```text
设计态：portal 原生字段设计器（四区布局：字段库 / 画布 / 属性面板 / 实时预览），
       引擎使用 @formily/core / @formily/react / @formily/json-schema。
       禁止引入 @formily/antd*、@formily/designable-setters、@formily/fusion、
       @formily/element-plus、@formily/next 等任何 UI 框架适配包。
Schema 协议：Formily-compatible JSON schema + wr_template_field 字段索引
表单运行态：Formily SchemaField / Formily runtime
列表表格：TanStack Table
外层页面：web/portal shadcn-admin
```

也就是：

```text
字段库（9 类字段）   →  shadcn/ui 卡片，点击追加
画布（字段排序/选中） →  shadcn/ui + 上下箭头按钮，不引拖拽库
属性面板              →  shadcn/ui 实现的 setter，契约 {value, onChange, props}
实时预览              →  复用 FormilyRuntimeForm
setter 框架           →  portal 自研，禁引 @formily/designable-setters / @formily/antd-setters
```

具体工程约束、CI 守卫与备选方案见 ADR 0005（`docs/adr/0005-work-record-designer-formily-core-only.md`）。

原因：

```text
1. web/portal 的主 UI 是 shadcn-admin / Radix / Tailwind，引入 Antd/Fusion 风格的
   setter 会让设计器与 portal 其他页面的视觉/主题/i18n/权限体系割裂。
2. 第一版需要的是运维记录字段配置，不是完整低代码设计平台。
3. @formily/core 已经在 web/portal 中作为运行态依赖，安装已成事实；让设计态也复用
   @formily/core，可以让 schema 校验、字段反应式状态、订阅能力贯通设计态与运行态。
4. 仍不引入 Antd/Fusion 的 setter 与 UI 适配，避免双 UI 体系。
5. Formily runtime 保留动态表单、schema 渲染和未来联动能力。
6. 后端以 schema_json + wr_template_field 双结构存储，既能运行动态表单，也能支持
   列表筛选、导出和字段索引。
```

Designable 的定位：

```text
Designable 不废弃，但只作为后续高级设计器候选。
只有当第一版字段设计器无法满足布局、联动、复杂组件编排时，再通过 ADR 评估是否引入。
```

第一版不把 Designable 作为主路径，是为了避免：

```text
1. 双 UI 体系长期并存。
2. 表单设计器与 portal 权限/i18n/主题割裂。
3. 为低代码能力提前支付过高复杂度。
4. 后续列表筛选、导出、审计无法稳定依赖字段索引。
```

---

## 5. 最终功能范围

第一版做：

```text
1. 用户管理
2. 角色权限
3. 字典管理
4. 工作日历基础能力（表 + API + CSV 导入，页面可选）
5. 表单模板管理
6. portal 原生表单设计
7. 工作记录填写
8. 工作记录详情
9. 工作记录列表
10. 筛选
11. 导出
```

第一版不做：

```text
微前端
微服务
完整工单流程
审批流
SLA
Excel 导入
自动爬取节假日
复杂排班
评论时间线
附件上传
告警联动
巡检联动
AI 总结
复杂统计
字段级权限
复杂模板版本管理
```

---

## 6. 页面数量

严格 MVP 仍然是 7 个页面：

```text
1. 用户管理页
2. 角色权限页
3. 字典管理页
4. 表单设计页
5. 记录列表页
6. 记录新建 / 编辑页
7. 记录详情页
```

工程稳妥版可以增加第 8 个页面：

```text
8. 工作日历页
```

但第一版建议：

```text
先做 aiops-platform/calendar 表 + API + CSV 导入。
页面可以延后，不阻塞工作记录核心闭环。
```

其中：

```text
用户管理页：基于 portal 现有 users 页面改造
角色权限页：新增或迁移
字典管理页：新增
工作日历页：可选新增，默认延后；如实现，使用表格视图，不做月历拖拽
表单设计页：新增，使用 portal 原生字段设计器并生成 Formily-compatible schema
记录列表页：新增，使用 TanStack Table
记录新建 / 编辑页：新增，使用 Formily runtime
记录详情页：新增，按 schema 渲染只读视图
```

---

## 7. 菜单设计

第一版菜单：

```text
工作记录
├─ 记录列表
└─ 表单设计

平台管理
├─ 用户管理
├─ 角色权限
├─ 字典管理
└─ 工作日历（可选，页面延后时不显示）
```

不要把下面这些做成菜单：

```text
日常记录
故障记录
巡检记录
变更记录
发布记录
值班交接
```

它们应该是 `record_type` 字典项。

工作日历也不要放在“工作记录”下面。它是 `platform` 能力，后续巡检任务、值班排班、SLA、告警静默和执行计划都会复用。

---

## 8. 前端路由设计

基于 TanStack Router 文件路由：

```text
web/portal/src/routes/_authenticated/
├─ work-records/
│  ├─ index.tsx
│  ├─ new.tsx
│  ├─ $recordId.tsx
│  ├─ $recordId.edit.tsx
│  └─ designer.tsx
│
└─ platform/
   ├─ dictionaries.tsx
   ├─ calendars.tsx
   └─ roles.tsx
```

对应页面：

```text
/_authenticated/work-records/              记录列表
/_authenticated/work-records/new           新建记录
/_authenticated/work-records/$recordId      记录详情
/_authenticated/work-records/$recordId/edit 编辑记录
/_authenticated/work-records/designer       表单设计
/_authenticated/platform/dictionaries       字典管理
/_authenticated/platform/calendars          工作日历（可选）
/_authenticated/platform/roles              角色权限
```

---

## 9. 前端目录设计

### 9.1 工作记录 feature

```text
web/portal/src/features/work-records/
├─ index.tsx
├─ api/
│  ├─ work-record-api.ts
│  ├─ template-api.ts
│  └─ export-api.ts
├─ data/
│  ├─ schema.ts
│  ├─ formily-schema.ts
│  ├─ field-types.ts
│  └─ reserved-field-codes.ts
├─ components/
│  ├─ records-table.tsx
│  ├─ records-columns.tsx
│  ├─ records-toolbar.tsx
│  ├─ record-form.tsx
│  ├─ record-readonly-view.tsx
│  ├─ formily-runtime-form.tsx
│  ├─ formily-schema-loader.tsx
│  ├─ template-designer-page.tsx
│  ├─ formily-designer-shell.tsx
│  ├─ dict-schema-injector.ts
│  └─ export-records-dialog.tsx
├─ hooks/
│  ├─ use-records.ts
│  ├─ use-record-template.ts
│  ├─ use-template-fields.ts
│  ├─ use-dict-items.ts
│  └─ use-formily-schema.ts
└─ types.ts
```

### 9.2 字典 feature

```text
web/portal/src/features/dictionaries/
├─ index.tsx
├─ api.ts
├─ data/
│  └─ schema.ts
├─ components/
│  ├─ dictionary-type-list.tsx
│  ├─ dictionary-item-table.tsx
│  ├─ dictionary-type-dialog.tsx
│  └─ dictionary-item-dialog.tsx
└─ hooks/
   └─ use-dictionaries.ts
```

### 9.3 工作日历 feature（可选页面）

```text
web/portal/src/features/calendars/
├─ index.tsx
├─ api/
│  └─ calendar-api.ts
├─ data/
│  └─ calendar-schema.ts
├─ components/
│  ├─ calendar-table.tsx
│  ├─ calendar-day-table.tsx
│  ├─ calendar-import-dialog.tsx
│  └─ calendar-day-dialog.tsx
└─ hooks/
   └─ use-calendars.ts
```

第一版如果不做页面，只保留后端 API 与 i18n key 预留。

---

## 10. 表单设计器方案

### 10.1 设计态

页面：

```text
/work-records/designer
```

使用：

```text
portal 原生字段设计器（四区布局：字段库 / 画布 / 属性面板 / 实时预览）
引擎：@formily/core / @formily/react / @formily/json-schema
setter：portal 自行用 shadcn/ui 实现（TextSetter / NumberSetter / BooleanSetter /
        SelectSetter / DictSetter），不引 @formily/designable-setters /
        @formily/antd-setters
生成 Formily-compatible schema
同步 wr_template_field 字段索引
```

能力：

```text
从字段面板添加字段
支持字段排序（上下箭头按钮，不引拖拽库）
配置字段属性
配置字段标题
配置字段编码（首次保存前可改，之后锁定）
配置默认值
配置校验
配置布局
配置选项
配置是否列表展示
配置是否支持筛选
配置是否支持统计
绑定平台字典
保存 schema
预览表单
```

字段类型限定为 SKILL §6.15.5 列出的 9 类（text / textarea / number / date / datetime / select / multi_select / user / boolean），不做级联选择、子表单、公式字段、联动显示、条件必填、复杂布局、远程接口字段。

设计态引擎与禁用包清单、CI 守卫见 ADR 0005（`docs/adr/0005-work-record-designer-formily-core-only.md`）。Designable 仅作为后续高级设计器候选，原因同 §4。

### 10.2 运行态

页面：

```text
/work-records/new
/work-records/$recordId/edit
```

流程：

```text
后端返回 wr_template.schema_json
  ↓
前端扫描 schema
  ↓
发现字段引用字典
  ↓
请求字典项
  ↓
注入 enum / dataSource / options
  ↓
Formily 渲染表单
  ↓
用户填写
  ↓
提交 values
  ↓
保存到 wr_record.custom_data_json
```

### 10.3 只读态

页面：

```text
/work-records/$recordId
```

流程：

```text
读取记录
读取模板 schema
注入字典 label
按 schema 顺序渲染只读详情
```

---

## 11. 数据模型更新

如果上 Formily，不能只存 `wr_template_field`，也不能只存 `schema_json`。

最终采用双结构：

```text
wr_template.schema_json
  负责设计器还原和运行态表单渲染

wr_template_field
  负责列表列、筛选白名单、导出、后端校验、统计
```

也就是：

```text
Formily schema 是主表单协议
wr_template_field 是字段索引表 / 查询辅助表
```

---

## 12. 数据库设计

### 12.1 字典表

```sql
create table platform_dict_type (
  id uuid primary key,
  tenant_id uuid not null,
  dict_code varchar(128) not null,
  dict_name varchar(128) not null,
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_code)
);
```

```sql
create table platform_dict_item (
  id uuid primary key,
  tenant_id uuid not null,
  dict_type_id uuid not null,
  item_label varchar(128) not null,
  item_value varchar(128) not null,
  color varchar(32),
  icon varchar(64),
  description text,
  system_builtin boolean not null default false,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  extra_json jsonb not null default '{}'::jsonb,
  created_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, dict_type_id, item_value)
);
```

### 12.2 模板表

```sql
create schema if not exists work_record;

create table work_record.wr_template (
  id uuid primary key,
  tenant_id uuid not null,
  name varchar(128) not null,
  code varchar(64) not null,
  description text,
  enabled boolean not null default true,

  schema_json jsonb not null default '{}'::jsonb,
  designer_json jsonb not null default '{}'::jsonb,

  created_by uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (tenant_id, code)
);
```

字段说明：

```text
schema_json：
  Formily 表单 schema，用于运行态渲染和设计器加载

designer_json：
  portal 原生设计器状态、属性面板展开状态、布局辅助信息等
  不作为运行态真相源
```

### 12.3 字段索引表

```sql
create table work_record.wr_template_field (
  id uuid primary key,
  tenant_id uuid not null,
  template_id uuid not null,

  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,

  required boolean not null default false,

  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,

  list_visible boolean not null default false,
  filterable boolean not null default false,
  statistical boolean not null default false,

  sort_order integer not null default 0,
  enabled boolean not null default true,

  schema_path varchar(512),

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (tenant_id, template_id, field_code)
);
```

### 12.4 记录表

```sql
create table work_record.wr_record (
  id uuid primary key,
  tenant_id uuid not null,
  template_id uuid not null,

  title varchar(255) not null,
  status varchar(32) not null default 'draft',
  owner_id uuid,
  creator_id uuid not null,
  record_time timestamptz not null,

  builtin_data_json jsonb not null default '{}'::jsonb,
  custom_data_json jsonb not null default '{}'::jsonb,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
```

第一版核心表：

```text
platform_dict_type
platform_dict_item
work_record.wr_template
work_record.wr_template_field
work_record.wr_record
```

---

## 13. Formily schema 扩展协议

为了让 Formily schema 支持工作记录业务，需要给字段增加扩展属性。

示例：

```json
{
  "type": "object",
  "properties": {
    "priority": {
      "type": "string",
      "title": "优先级",
      "x-decorator": "FormItem",
      "x-component": "Select",
      "x-work-record-field-code": "priority",
      "x-work-record-field-type": "select",
      "x-work-record-option-source": "dict",
      "x-work-record-dict-code": "record_priority",
      "x-work-record-list-visible": true,
      "x-work-record-filterable": true,
      "x-work-record-statistical": true
    }
  }
}
```

扩展字段：

```text
x-work-record-field-code
x-work-record-field-type
x-work-record-option-source
x-work-record-dict-code
x-work-record-list-visible
x-work-record-filterable
x-work-record-statistical
```

保存模板时：

```text
1. 保存完整 Formily schema 到 wr_template.schema_json
2. 保存设计器元信息到 wr_template.designer_json
3. 从 schema 中抽取字段元信息
4. 同步写入 wr_template_field
```

---

## 14. 字典和 Formily 的关系

字段选项来源：

```text
static：字段自己的静态选项
dict：引用平台字典
```

静态选项示例：

```json
{
  "fieldCode": "impact_scope",
  "optionSource": "static",
  "options": [
    { "label": "无影响", "value": "none" },
    { "label": "部分影响", "value": "partial" },
    { "label": "严重影响", "value": "major" }
  ]
}
```

字典选项示例：

```json
{
  "fieldCode": "priority",
  "optionSource": "dict",
  "dictCode": "record_priority"
}
```

运行态注入流程：

```text
扫描 schema
  ↓
找到 x-work-record-option-source = dict
  ↓
读取 x-work-record-dict-code
  ↓
请求 /api/platform/dictionaries/{dictCode}/items
  ↓
转换成 Formily Select 所需 options
  ↓
注入 schema
  ↓
渲染表单
```

---

## 15. API 设计

### 15.1 字典 API

```text
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

删除实际是：

```text
enabled = false
```

### 15.2 模板 API

```text
GET    /api/work-record/templates
POST   /api/work-record/templates
GET    /api/work-record/templates/{id}
PUT    /api/work-record/templates/{id}
DELETE /api/work-record/templates/{id}
```

模板详情返回：

```json
{
  "id": "template-id",
  "name": "默认工作记录表",
  "code": "default_work_record",
  "schemaJson": {},
  "designerJson": {},
  "fields": []
}
```

### 15.3 字段元数据 API

字段元数据主要由模板保存时自动同步，但可以提供只读接口：

```text
GET /api/work-record/templates/{templateId}/fields
```

必要时提供字段配置更新接口：

```text
PUT /api/work-record/templates/{templateId}/fields/{fieldId}
POST /api/work-record/templates/{templateId}/fields/reorder
```

### 15.4 工作记录 API

```text
GET    /api/work-record/records
POST   /api/work-record/records
GET    /api/work-record/records/{id}
PUT    /api/work-record/records/{id}
DELETE /api/work-record/records/{id}
GET    /api/work-record/records/export
```

记录创建请求：

```json
{
  "templateId": "template-id",
  "title": "每日工作记录",
  "status": "done",
  "ownerId": "user-id",
  "recordTime": "2026-07-07T10:00:00+09:00",
  "customData": {
    "record_type": "daily",
    "work_content": "处理服务器巡检异常",
    "priority": "P2",
    "env": "prod"
  }
}
```

---

## 16. 后端模块设计

### 16.1 字典模块

```text
modules/aiops-platform
└─ src/main/java/io/aegisops/platform/dictionary
   ├─ api
   │  └─ DictionaryController.java
   ├─ application
   │  └─ DictionaryService.java
   ├─ domain
   │  ├─ DictType.java
   │  └─ DictItem.java
   └─ infrastructure
      └─ DictionaryRepository.java
```

### 16.2 工作记录模块

```text
modules/aiops-work-record
└─ src/main/java/io/aegisops/workrecord
   ├─ api
   │  ├─ WorkRecordController.java
   │  ├─ WorkRecordTemplateController.java
   │  └─ WorkRecordExportController.java
   │
   ├─ application
   │  ├─ WorkRecordService.java
   │  ├─ WorkRecordTemplateService.java
   │  ├─ WorkRecordSchemaService.java
   │  ├─ WorkRecordFieldIndexService.java
   │  ├─ WorkRecordQueryService.java
   │  └─ WorkRecordExportService.java
   │
   ├─ domain
   │  ├─ WorkRecord.java
   │  ├─ WorkRecordTemplate.java
   │  ├─ WorkRecordField.java
   │  ├─ FieldType.java
   │  └─ OptionSource.java
   │
   └─ infrastructure
      ├─ jdbc
      ├─ schema
      ├─ excel
      └─ validator
```

新增重点服务：

```text
WorkRecordSchemaService：
  负责保存 / 读取 / 清洗 Formily schema

WorkRecordFieldIndexService：
  负责从 Formily schema 中抽取字段元数据并同步 wr_template_field
```

---

## 17. 权限设计

权限码：

```text
platform:dict:view
platform:dict:create
platform:dict:update
platform:dict:delete

work-record:view
work-record:record:view
work-record:record:create
work-record:record:update
work-record:record:delete
work-record:record:export

work-record:template:view
work-record:template:create
work-record:template:update
work-record:template:delete
```

角色：

```text
系统管理员：
  全部权限

记录管理员：
  字典查看
  表单设计
  查看全部记录
  导出记录

普通用户：
  创建记录
  查看自己的记录
  编辑自己的记录

只读用户：
  查看记录
```

后端权限规则：

```text
普通用户：
  creator_id = 当前用户
  or owner_id = 当前用户

记录管理员：
  可查看全部记录

系统管理员：
  全部权限
```

---

## 18. 关键实现注意事项

### 18.1 先做 Formily runtime 与 schema 契约 Spike

因为 `web/portal` 当前是 React 19。`package.json` 显示 React 和 React DOM 为 19.2.7。

必须先验证：

```text
Formily runtime 能否正常渲染
Vite build 是否通过
React 19 下是否有明显兼容问题
样式是否严重冲突
portal 原生设计器生成的 schema 能否被 runtime 稳定消费
```

如果 Formily runtime 兼容性不好，则降级为：

```text
第一版：portal 原生字段配置 + React Hook Form 动态渲染
第二版：再接 Formily runtime 或通过 ADR 评估替代方案
```

优先目标仍然是第一版使用 Formily runtime；Designable 不进入第一版主路径。

### 18.2 不要只存 Formily schema

必须同步 `wr_template_field`。

原因：

```text
列表动态列需要字段元数据
筛选白名单需要字段元数据
导出字段需要字段元数据
后端校验需要字段元数据
统计字段需要字段元数据
```

### 18.3 field_code 不能随便改

Formily schema 的 `properties` key 就是业务字段编码。

例如：

```json
{
  "properties": {
    "priority": {}
  }
}
```

这个 `priority` 后面不能随便改，否则历史记录中的：

```json
{
  "priority": "P2"
}
```

会读不到。

### 18.4 字典项不能物理删除

禁用即可：

```text
enabled = false
```

历史记录仍然需要展示 label。

### 18.5 自定义字段筛选必须走白名单

后端流程：

```text
读取 template_id
读取 wr_template_field
确认 field_code 存在
确认 filterable = true
确认字段类型合法
再构造 JSONB 查询
```

禁止直接用前端传入的字段名拼 SQL。

### 18.6 导出必须限流

第一版同步导出，限制：

```text
最多 5000 或 10000 行
```

后面再做异步导出。

---

## 19. portal 构建接管注意事项

当前根 `package.json` 的 build、lint、typecheck、test 等脚本仍然指向 `web/console`。

如果新版 UI 确认以 `web/portal` 为主，需要调整：

```text
pnpm -C web/console run build
改为
pnpm -C web/portal run build
```

涉及：

```text
build
lint
format
typecheck
test
ci:frontend
```

同时，后端打包静态资源也要从旧 console 切到 portal：

```text
web/portal/dist -> aiops-server static
```

建议新增 profile：

```text
with-portal
```

而不是直接删除旧 `with-console`，这样可以保留回退能力。

### 19.1 console 废弃声明

```text
web/console 自本设计起仅做历史保留与回退用途：
  - 不再新增业务模块（包括工作记录的所有功能）
  - 不再追加依赖、组件、路由
  - 不参与本地与 CI 默认质量门禁
  - 现有页面继续保留以便旧用户回退
  - 干净移除留待 Phase 8 生产加固阶段统一评估
```

---

## 20. 最新 Phase 设计与代码交付包

本节替代旧的粗粒度路线图。当前仓库事实是：

```text
后端：
  modules/aiops-platform 已存在 dictionary 包
  modules/aiops-work-record 已存在模板 / 字段 / 记录 / 导出服务与单元测试
  apps/aiops-server 已包含 V0012 / V0013 / V0014 工作记录 migration

前端：
  主前端切换为 web/portal
  web/portal 已有 i18next + zh-CN / en-US TS 资源文件
  当前菜单可先不动，业务菜单以后只追加，不删除模板菜单
```

因此后续实施不从零开始，而是按“补齐后端缺口 + 新增 portal feature + 国际化 key”推进。

### Phase WR-0：现状固化与边界校准

目标：

```text
确认已实现后端能力、冻结 console 历史设计、把后续开发入口统一到 web/portal。
```

后端代码现状：

```text
modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/*
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/*
apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql
apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql
apps/aiops-server/src/main/resources/db/migration/V0014__init_work_record_default_template.sql
```

后端测试现状：

```text
modules/aiops-platform/src/test/java/io/aegisops/platform/dictionary/DictionaryServiceTest.java
modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordTemplateServiceTest.java
modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordServiceTest.java
modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordFieldValidatorTest.java
modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordExportServiceTest.java
```

需要补齐：

```text
1. 将 docs/record/work-record-phases-design-code.md 标记为历史代码包或重写为 portal-first
2. 在本文档保留 console 废弃声明
3. 明确工作记录模块不是 Incident 主闭环优先项，只作为 execution 体系的轻量记录能力推进
4. 不新增微服务，不引入审批流，不动现有 portal 模板菜单
```

验收：

```text
mvn -pl modules/aiops-work-record -am test
mvn -pl modules/aiops-platform -am test
pnpm -C web/portal run test
```

### Phase WR-1：portal 菜单、路由与 i18n 骨架

目标：

```text
在不移除现有模板菜单的前提下，追加工作记录与平台管理入口，并建立 TS key-value 国际化骨架。
```

前端新增代码：

```text
web/portal/src/routes/_authenticated/work-records/index.tsx
web/portal/src/routes/_authenticated/work-records/new.tsx
web/portal/src/routes/_authenticated/work-records/$recordId.tsx
web/portal/src/routes/_authenticated/work-records/$recordId.edit.tsx
web/portal/src/routes/_authenticated/work-records/designer.tsx
web/portal/src/routes/_authenticated/platform/dictionaries.tsx
web/portal/src/routes/_authenticated/platform/roles.tsx

web/portal/src/features/work-records/index.tsx
web/portal/src/features/work-records/components/records-empty-state.tsx
web/portal/src/features/work-records/components/template-designer-placeholder.tsx
web/portal/src/features/dictionaries/index.tsx
web/portal/src/features/roles/index.tsx

web/portal/src/i18n/locales/zh-CN/work-records.ts
web/portal/src/i18n/locales/en-US/work-records.ts
web/portal/src/i18n/locales/zh-CN/platform.ts
web/portal/src/i18n/locales/en-US/platform.ts
```

修改代码：

```text
web/portal/src/components/layout/data/sidebar-data.ts
web/portal/src/i18n/locales/zh-CN/nav.ts
web/portal/src/i18n/locales/en-US/nav.ts
web/portal/src/i18n/locales/zh-CN/index.ts
web/portal/src/i18n/locales/en-US/index.ts
web/portal/src/i18n/config.ts
```

路由 search schema：

```tsx
const recordsSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(10),
  status: z
    .array(z.enum(['draft', 'processing', 'done', 'archived']))
    .optional()
    .catch([]),
  title: z.string().optional().catch(''),
})
```

单元测试：

```text
web/portal/src/features/work-records/components/records-empty-state.test.tsx
web/portal/src/features/work-records/components/template-designer-placeholder.test.tsx
```

验收：

```text
侧边栏追加工作记录 / 记录列表 / 表单设计 / 平台管理 / 字典管理 / 角色权限
所有新增路由能打开
页面关键文案均来自 t('workRecords.*') 或 t('platform.*')
routeTree.gen.ts 自动更新
```

### Phase WR-2：字典管理 portal 接入

目标：

```text
把已存在的后端 dictionary 能力接入 portal，先支持记录类型、优先级、环境、状态等关键字典。
```

后端保留与补齐：

```text
保留：
  DictionaryController.java
  DictionaryService.java
  DictionaryRepository.java
  DefaultDictionaryInitializer.java
  DictionaryServiceTest.java

补齐：
  DictionaryControllerTest.java
  默认字典 seed 覆盖测试
  字典项禁用后仍可按 value 回显 label 的查询方法
```

前端完整代码落点：

```text
web/portal/src/features/dictionaries/
├─ index.tsx
├─ data/
│  ├─ schema.ts
│  └─ data.ts
├─ data/client.ts
├─ hooks/use-dictionaries.ts
└─ components/
   ├─ dictionaries-provider.tsx
   ├─ dictionary-type-list.tsx
   ├─ dictionary-item-table.tsx
   ├─ dictionary-item-columns.tsx
   ├─ dictionary-type-dialog.tsx
   ├─ dictionary-item-dialog.tsx
   └─ dictionary-delete-dialog.tsx
```

前端单元测试：

```text
web/portal/src/features/dictionaries/components/dictionaries-provider.test.tsx
web/portal/src/features/dictionaries/data/schema.test.ts
web/portal/src/features/dictionaries/hooks/use-dictionaries.test.tsx
```

API client 约束：

```text
统一新增 data/client.ts
使用 axios 实例
baseURL 读取 VITE_API_BASE_URL
响应使用 zod parse
不在 client.ts toast
错误交给 QueryClient onError
```

验收：

```text
能列出字典类型
能新增 / 编辑 / 禁用字典类型
能新增 / 编辑 / 禁用字典项
record_type / record_priority / record_env / record_status 默认存在
portal 字典页面所有关键文案已国际化
```

### Phase WR-2A：平台工作日历基础能力

目标：

```text
新增 aiops-platform/calendar 轻量基础能力，用于判断工作日、统计工作日数量，并为后续日报缺失判断、月报统计、巡检任务、值班排班和 SLA 计算预留基础。
```

第一版交付：

```text
1. platform_calendar 表。
2. platform_calendar_day 表。
3. CalendarService / CalendarController。
4. 工作日 check / range / count API。
5. CSV 导入。
6. 租户级自定义覆盖。
7. 权限、审计和单元测试。
```

第一版可选：

```text
/platform/calendars 工作日历页
```

默认建议：

```text
先做表 + API + CSV 导入。
页面延后，不阻塞工作记录核心闭环。
```

完整设计见：

```text
docs/record/phase-02a-platform-calendar.md
```

验收：

```text
能创建 CN_YYYY 工作日历
能导入 CSV 日期
能判断某一天是否工作日
能统计日期范围内工作日数量
所有 calendar 查询按 tenantId 隔离
导入和单日覆盖写审计
```

### Phase WR-3：Formily runtime 与 schema 契约 Spike

目标：

```text
在 React 19 + Vite 8 + portal 样式下验证 Formily runtime、schema 生成与字典注入是否能稳定工作。
```

新增依赖（运行时 + 设计态共享）：

```text
@formily/core
@formily/react
@formily/json-schema
@formily/validator
```

禁用包（参见 ADR 0005，CI 由 scripts/ci/check-formily-deps.sh 守卫）：

```text
@formily/antd
@formily/antd-components
@formily/antd-setters
@formily/antd-icons
@formily/designable-setters
@formily/fusion
@formily/element-plus
@formily/next
@formily/icons
```

新增代码：

```text
web/portal/src/features/work-records/data/formily-schema.ts
web/portal/src/features/work-records/data/designer/field-types.ts
web/portal/src/features/work-records/data/designer/palette.ts
web/portal/src/features/work-records/data/designer/schema-builder.ts
web/portal/src/features/work-records/components/formily-runtime-form.tsx
web/portal/src/features/work-records/components/designer/designer-canvas.tsx
web/portal/src/features/work-records/components/designer/designer-palette.tsx
web/portal/src/features/work-records/components/designer/designer-property-panel.tsx
web/portal/src/features/work-records/components/designer/designer-preview.tsx
web/portal/src/features/work-records/components/designer/setter/{text,number,boolean,select,dict}-setter.tsx
web/portal/src/features/work-records/components/dict-schema-injector.ts
```

`formily-designer-shell.tsx` 重写为四区布局：左字段库 / 中画布 / 右上半属性 / 右下半预览。

单元测试：

```text
web/portal/src/features/work-records/components/dict-schema-injector.test.ts
web/portal/src/features/work-records/data/formily-schema.test.ts
web/portal/src/features/work-records/data/designer/field-types.test.ts
web/portal/src/features/work-records/data/designer/schema-builder.test.ts
web/portal/src/features/work-records/components/designer/designer-canvas.test.tsx
web/portal/src/features/work-records/components/designer/designer-property-panel.test.tsx
```

降级策略：

```text
如果 Formily runtime 与 React 19 / Vite 8 不兼容：
  Phase WR-3 交付 schema 契约与字段索引
  Phase WR-4 使用 portal 原生设计器（不依赖 Formily runtime）继续生成同一份 schema
  Phase WR-5 暂时改为 React Hook Form + zod-v4-resolver 动态渲染
  Formily runtime 延后到单独 ADR 决策
```

验收：

```text
pnpm -C web/portal run build 通过
runtime 能渲染最小 schema
设计器四区布局与 setter 行为符合 ADR 0005
scripts/ci/check-formily-deps.sh 在 CI 中通过
字典注入函数纯单测通过
禁用包不在 lockfile 中出现
```

### Phase WR-4：模板后端升级与字段索引同步

目标：

```text
把已存在的模板后端从“手工字段 CRUD”升级为“保存 Formily schema，同时自动同步 wr_template_field”。
```

当前已实现：

```text
WorkRecordTemplateService.createTemplate()
WorkRecordTemplateService.updateTemplate()
WorkRecordTemplateService.createField()
WorkRecordTemplateService.updateField()
WorkRecordFieldValidator
WorkRecordTemplateServiceTest
```

需要新增 / 修改：

```text
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordSchemaService.java
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldIndexService.java
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/FormilyFieldDescriptor.java
modules/aiops-work-record/src/main/java/io/aegisops/workrecord/SaveTemplateSchemaRequest.java

WorkRecordTemplate.java 增加 designerJson
CreateTemplateRequest.java 增加 designerJson
UpdateTemplateRequest.java 增加 designerJson
WorkRecordTemplateRepository.java 保存 / 更新 designer_json
```

数据库 migration：

```text
apps/aiops-server/src/main/resources/db/migration/V0015__init_work_record_formily_schema_index.sql
```

migration 内容：

```sql
alter table wr_template
  add column if not exists designer_json jsonb not null default '{}'::jsonb;

alter table wr_template_field
  add column if not exists schema_path varchar(512);
```

后端单元测试：

```text
WorkRecordSchemaServiceTest
  - normalize 空 schema 为 {}
  - 拒绝非 object JSON
  - 保留 x-work-record-* 扩展属性

WorkRecordFieldIndexServiceTest
  - 从 properties 抽取字段编码
  - dict 字段必须带 dictCode
  - field_code 使用 properties key，不接受运行时改名
  - listVisible / filterable / statistical 正确落库

WorkRecordTemplateServiceTest 补充：
  - updateTemplate 保存 schema 后调用字段索引同步
  - repository 更新失败时不写 audit
```

验收：

```text
保存模板 schema_json 与 designer_json
同步生成 wr_template_field
重复 field_code 被拒绝或覆盖为同一字段
字段索引同步有完整单测
```

### Phase WR-5：记录填写、编辑与详情

目标：

```text
用户可基于模板填写记录，详情页按 schema 只读展示。
```

当前后端已实现：

```text
WorkRecordService.create()
WorkRecordService.update()
WorkRecordService.get()
WorkRecordService.list()
WorkRecordFieldValidator.validateAgainstTemplate()
WorkRecordServiceTest
```

后端补齐：

```text
WorkRecordControllerTest
WorkRecordFieldValidatorTest 增加 select / multi_select / required / unknown field case
WorkRecordServiceTest 增加 owner / creator / read all 权限组合
```

前端完整代码落点：

```text
web/portal/src/features/work-records/
├─ data/
│  ├─ schema.ts
│  ├─ client.ts
│  ├─ field-types.ts
│  └─ reserved-field-codes.ts
├─ hooks/
│  ├─ use-record-template.ts
│  ├─ use-record.ts
│  ├─ use-save-record.ts
│  └─ use-dict-items.ts
└─ components/
   ├─ record-form.tsx
   ├─ formily-runtime-form.tsx
   ├─ formily-schema-loader.tsx
   ├─ record-readonly-view.tsx
   └─ record-page-header.tsx
```

前端单元测试：

```text
record-readonly-view.test.tsx
formily-schema-loader.test.tsx
reserved-field-codes.test.ts
schema.test.ts
```

验收：

```text
新建页读取默认模板
编辑页回填已有 customDataJson
详情页按 schema 顺序只读渲染
字典 value 能显示 label
非法自定义字段值被后端拒绝
普通用户不能读取别人的记录
```

### Phase WR-6：记录列表、动态列、筛选与导出

目标：

```text
管理员可在 portal 以 TanStack Table 查看、筛选、导出工作记录。
```

当前后端已实现：

```text
WorkRecordRepository.page()
WorkRecordRepository.pageForUser()
WorkRecordExportController
WorkRecordExportService
WorkRecordExportServiceTest
```

后端补齐：

```text
1. 自定义字段筛选 DTO
2. filterable 白名单校验
3. JSONB 查询参数化实现
4. 导出上限配置，默认 5000 行
5. 导出使用当前筛选条件
```

前端完整代码落点：

```text
web/portal/src/features/work-records/components/
├─ records-table.tsx
├─ records-columns.tsx
├─ records-toolbar.tsx
├─ records-primary-buttons.tsx
├─ records-provider.tsx
├─ records-dialogs.tsx
├─ export-records-dialog.tsx
├─ data-table-row-actions.tsx
└─ data-table-bulk-actions.tsx
```

前端单元测试：

```text
records-provider.test.tsx
records-columns.test.tsx
export-records-dialog.test.tsx
```

验收：

```text
分页 / 状态筛选 / 标题搜索同步到 URL search
动态列只来自 list_visible 字段
自定义筛选只允许 filterable 字段
导出按钮使用当前筛选条件
超出导出上限返回明确错误
```

### Phase WR-7：权限、审计、质量门禁

目标：

```text
把工作记录模块从“可用”推进到“可试用”，重点补齐权限、审计和测试。
```

后端必须覆盖：

```text
1. work-record:read:self
2. work-record:read:all
3. work-record:template:read
4. work-record:template:write
5. work-record:export
6. platform:dict:read
7. platform:dict:write
8. platform:calendar:read
9. platform:calendar:write
10. platform:calendar:import
```

审计动作：

```text
work_record.template.create
work_record.template.update
work_record.field.create
work_record.field.update
work_record.record.create
work_record.record.update
work_record.record.delete
work_record.record.export
platform.dict.type.create
platform.dict.type.update
platform.dict.item.create
platform.dict.item.update
```

质量门禁：

```bash
mvn -pl modules/aiops-platform -am test
mvn -pl modules/aiops-work-record -am test
mvn -pl apps/aiops-server -am test
pnpm -C web/portal run lint
pnpm -C web/portal run build
pnpm -C web/portal run test
```

验收：

```text
所有新增后端服务有单元测试
所有新增 portal Provider / schema / 纯函数有单元测试
权限不足返回 403
所有敏感写操作写审计
文档、代码、测试三者一致
```

## 21. portal 国际化方案

当前 `web/portal` 已经采用：

```text
i18next
react-i18next
i18next-browser-languagedetector
TS 文件存储 key-value
默认语言 zh-CN
cookie: aegisops_portal_lang
```

继续沿用现有结构，不引入 JSON 资源文件：

```text
web/portal/src/i18n/locales/zh-CN/<namespace>.ts
web/portal/src/i18n/locales/en-US/<namespace>.ts
```

新增 namespace：

```text
workRecords
platform
```

`zh-CN/work-records.ts`：

```ts
export const workRecords = {
  title: '工作记录',
  description: '填写、查看和导出可配置工作记录',
  list: {
    title: '记录列表',
    create: '新建记录',
    export: '导出记录',
    searchPlaceholder: '筛选记录标题...',
  },
  designer: {
    title: '表单设计',
    description: '配置工作记录模板、字段和字典绑定',
    save: '保存模板',
    preview: '预览',
  },
  form: {
    title: '记录标题',
    template: '记录模板',
    recordTime: '记录时间',
    owner: '负责人',
    submit: '提交记录',
  },
  status: {
    draft: '草稿',
    processing: '处理中',
    done: '已完成',
    archived: '已归档',
  },
  columns: {
    title: '标题',
    status: '状态',
    owner: '负责人',
    creator: '创建人',
    recordTime: '记录时间',
    actions: '操作',
  },
} as const
```

`en-US/work-records.ts`：

```ts
export const workRecords = {
  title: 'Work Records',
  description: 'Create, review, and export configurable work records',
  list: {
    title: 'Records',
    create: 'New Record',
    export: 'Export Records',
    searchPlaceholder: 'Filter record titles...',
  },
  designer: {
    title: 'Form Designer',
    description: 'Configure templates, fields, and dictionary bindings',
    save: 'Save Template',
    preview: 'Preview',
  },
  form: {
    title: 'Record Title',
    template: 'Template',
    recordTime: 'Record Time',
    owner: 'Owner',
    submit: 'Submit Record',
  },
  status: {
    draft: 'Draft',
    processing: 'Processing',
    done: 'Done',
    archived: 'Archived',
  },
  columns: {
    title: 'Title',
    status: 'Status',
    owner: 'Owner',
    creator: 'Creator',
    recordTime: 'Record Time',
    actions: 'Actions',
  },
} as const
```

`zh-CN/platform.ts`：

```ts
export const platform = {
  title: '平台管理',
  dictionaries: {
    title: '字典管理',
    description: '管理平台枚举、字段选项和表单字典',
    type: '字典类型',
    item: '字典项',
    code: '编码',
    name: '名称',
    label: '标签',
    value: '值',
    enabled: '启用',
  },
  roles: {
    title: '角色权限',
    description: '管理角色、权限和菜单访问范围',
  },
  calendars: {
    title: '工作日历',
    description: '维护工作日、节假日、调休和公司自定义日期',
    year: '年份',
    region: '地区',
    import: '导入 CSV',
    date: '日期',
    dayOfWeek: '星期',
    dayType: '日期类型',
    isWorkday: '是否工作日',
    holidayName: '节日名称',
    remark: '备注',
  },
} as const
```

`en-US/platform.ts`：

```ts
export const platform = {
  title: 'Platform',
  dictionaries: {
    title: 'Dictionaries',
    description: 'Manage enums, field options, and form dictionaries',
    type: 'Dictionary Type',
    item: 'Dictionary Item',
    code: 'Code',
    name: 'Name',
    label: 'Label',
    value: 'Value',
    enabled: 'Enabled',
  },
  roles: {
    title: 'Roles & Permissions',
    description: 'Manage roles, permissions, and menu access',
  },
} as const
```

必须同步：

```text
1. zh-CN/index.ts 与 en-US/index.ts import 并导出 workRecords / platform
2. i18n/config.ts 的 NAMESPACES 追加 workRecords / platform
3. nav.ts 追加工作记录与平台管理菜单 key
4. 组件中只使用静态 key：t('workRecords.list.title')
5. 禁止字符串拼接 key：t(`workRecords.status.${status}`) 仅在 status 已被 zod enum 校验后允许
```

首批国际化范围：

```text
菜单
页面标题 / 描述
表格列名
状态文案
按钮
Dialog 标题 / 描述
Toast 成功 / 失败提示
空态
```

暂缓国际化范围：

```text
Formily 第三方组件内置校验文案
后端异常原始 message
后续可能移除的模板示例页面
```

---

## 22. 总工期

一个人 + AI：

```text
能演示：2 周左右
内部可试用：3～4 周
做得比较稳：4～5 周
```

推荐节奏：

```text
第 1 周：
  portal 接管
  Formily runtime 与 schema 契约 Spike
  菜单路由
  字典管理
  工作日历表 + API（不阻塞页面）

第 2 周：
  模板后端
  schema 保存
  字段索引同步
  表单设计页

第 3 周：
  记录填写
  编辑
  详情
  字典注入

第 4 周：
  列表
  筛选
  导出
  权限
  测试
```

---

## 23. 最终架构总结

最终架构：

```text
前端：
  web/portal
  shadcn-admin 外壳
  TanStack Router
  TanStack Query
  TanStack Table
  portal 原生字段设计器
  Formily runtime

后端：
  aiops-server
  aiops-platform/dictionary
  aiops-platform/calendar
  aiops-work-record

数据库：
  platform_dict_type
  platform_dict_item
  platform_calendar
  platform_calendar_day
  work_record.wr_template
  work_record.wr_template_field
  work_record.wr_record
```

最终取舍：

```text
表单设计器采用 portal 原生字段设计器
表单运行态使用 Formily
列表表格继续使用 TanStack Table
字典自研平台能力
后端保存 schema_json，同时同步字段索引表
不做微前端
不做微服务
不做完整工单系统
Designable 作为后续高级设计器候选，不进入第一版主路径
```

一句话：

```text
第一版保留 Formily runtime 的动态表单优势，但设计器必须贴合 portal：schema 负责渲染，wr_template_field 负责查询、筛选、导出和后端校验。
```

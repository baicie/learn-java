---
title: 可配置工作记录模块（终版设计 · pre-portal · 已废弃）
type: design
status: deprecated
phase: work-record
owner: platform-team
created: 2026-07-06
updated: 2026-07-07
related:
  - docs/record/index.md
  - docs/record/work-record-phases-design-code.md
deprecated_reason: 原设计基于 web/console，已被 docs/record/index.md (2026-07-07, status: review) 替代为 portal-first 视角。
superseded_by: docs/record/index.md
---

# 可配置工作记录模块（终版设计 · pre-portal · 已废弃）

> ⚠️ **本版本已废弃（deprecated）**。原文基于 `web/console` 单前端，2026-07-07 起 AegisOps 改以 `web/portal` 为唯一前端（本仓库 `feat/portal-theme-i18n` 已 cherry-pick 上 portal + theme + i18n）。
>
> 请改读当前权威版本：[可配置工作记录模块（终版设计 · portal-first）](index.md)。
>
> 本文件仅作历史保留，不再被维护。

下面是结合前面所有讨论后的 **终版设计**。我建议这个模块不要做成“大工单系统”，而是定为：

```text id="x3vcuo"
AI-Ops 运维中台 · 可配置工作记录模块
```

核心能力就是：

```text id="t52l7g"
用户管理 + 角色权限 + 字典管理 + 管理员拖拽制表 + 用户填写记录 + 管理员筛选导出
```

---

# 1. 最终结论

## 1.1 前端不需要微前端

当前 `ai-ops mvp` 的前端已经是 `web/console` 单应用，技术栈是 React、Vite、React Router、TanStack Query、shadcn 风格组件，直接加 `work-record` feature 最合适。

当前路由也是集中注册在 `App.tsx` 里，已有数据源、告警、事件、巡检、用户、角色、模块、审计等页面。

所以第一版：

```text id="o5ggmv"
不要微前端
不要 qiankun
不要 module federation
不要 iframe
不要单独子应用
```

直接做：

```text id="qp7okr"
web/console/src/pages/work-record
web/console/src/features/work-record
```

未来只有在下面情况出现时，再考虑微前端：

```text id="8s03sn"
工作记录要单独卖
要独立部署
要嵌入多个系统
有独立团队维护
技术栈要和 ai-ops 不同
```

---

# 2. 产品定位

## 2.1 模块名称

一级菜单建议叫：

```text id="xg8gi2"
工作记录
```

不要叫“工单中心”。

因为“工单中心”会天然引出：

```text id="8atgq3"
审批流
派单
SLA
流程引擎
通知
工单模板
服务目录
```

第一版不需要这么重。

## 2.2 产品定位

```text id="n98uvn"
轻量工作记录系统
动态表单系统
运维日报 / 故障记录 / 巡检记录 / 变更记录沉淀工具
```

它的本质是：

```text id="sewwbt"
管理员设计表
用户填数据
管理员查数据
系统可导出
后续可统计
```

---

# 3. 最小闭环

最终第一版闭环：

```text id="u6k3pk"
系统管理员创建用户
  ↓
配置角色权限
  ↓
配置基础字典
  ↓
管理员拖拽设计工作记录表
  ↓
普通用户每天填写工作记录
  ↓
记录管理员查看全部记录
  ↓
筛选 / 导出
```

第一版只做这个闭环。

不做：

```text id="nflvoz"
审批流
SLA
复杂统计
AI 总结
告警联动
巡检联动
Excel 导入
评论时间线
附件
流程引擎
微前端
```

这些后续再加。

---

# 4. 总体架构

当前 `ai-ops mvp` 后端已经是 Maven 多模块工程，根工程包含 `aiops-common`、`aiops-web`、`aiops-persistence`、`aiops-audit`、`aiops-tenant`、`aiops-user`、`aiops-security`、`aiops-platform`、`aiops-alert`、`aiops-incident`、`aiops-inspection` 等模块，并由 `apps/aiops-server` 聚合启动。

所以终版架构：

```text id="e883kf"
ai-ops
├─ apps
│  └─ aiops-server
│
├─ modules
│  ├─ aiops-user              # 复用：用户管理
│  ├─ aiops-security          # 复用：角色权限
│  ├─ aiops-platform          # 增强：字典管理
│  ├─ aiops-audit             # 复用：审计日志
│  └─ aiops-work-record       # 新增：工作记录模块
│
└─ web
   └─ console
      └─ src
         ├─ pages/platform
         │  └─ DictionaryPage.tsx
         ├─ pages/work-record
         └─ features/work-record
```

`apps/aiops-server` 当前已经通过依赖聚合多个业务模块。
新增 `aiops-work-record` 后，也按同样方式接入。

---

# 5. 模块边界

## 5.1 复用现有能力

```text id="yokwhv"
用户管理：复用 aiops-user
角色权限：复用 aiops-security
审计日志：复用 aiops-audit
菜单导航：复用 aiops-platform
```

当前前端已经有用户和角色路由：

```text id="4sbjhb"
/platform/users
/app/platform/users
/platform/roles
/app/platform/roles
```

这些路由已经在 `App.tsx` 中存在。

## 5.2 新增能力

```text id="jepxju"
字典管理：放到 aiops-platform
工作记录：新增 aiops-work-record
```

不要把工作记录写进：

```text id="b2xu4r"
aiops-incident
aiops-inspection
aiops-platform
```

否则后面边界会乱。

---

# 6. 菜单设计

当前菜单由 `/api/platform/navigation/menus` 获取，菜单项包含 `id`、`moduleId`、`parentId`、`path`、`title`、`icon`、`permissionCode`、`sortOrder`、`enabled`。

前端再通过 `buildMenuTree` 按 `parentId` 组装菜单树。

终版菜单：

```text id="xlw8x7"
工作台

工作记录
├─ 记录列表
└─ 表单设计

平台管理
├─ 用户管理
├─ 角色权限
├─ 字典管理
├─ 模块管理
└─ 审计日志
```

不要把下面这些做成菜单：

```text id="my0iwz"
日常记录
故障记录
巡检记录
变更记录
发布记录
值班交接
```

它们应该是 **字典项**，在记录列表里作为“记录类型”筛选。

---

# 7. 数据库设计

## 7.1 数据库边界

第一版建议：

```text id="ady55q"
同一个 PostgreSQL 实例
新增 work_record schema
平台字典放平台表
```

不建议第一版就多个数据库。原因：

```text id="p6q0hm"
少一个 DataSource
少一套事务管理
少一套 Flyway 配置
测试更简单
部署更简单
```

未来独立产品化时，再拆成：

```text id="bhh8fs"
aegisops_work_record
```

---

## 7.2 字典表

字典是平台能力，放平台模块。

### `platform_dict_type`

```sql id="nqsizg"
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

### `platform_dict_item`

```sql id="jtkh16"
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

第一批内置字典：

```text id="t3scdz"
record_type        工作记录类型
record_status      工作记录状态
record_priority    优先级
env_type           环境类型
yes_no             是否
process_result     处理结果
```

字典项示例：

```text id="keamj6"
record_type:
  daily        日常记录
  fault        故障记录
  inspection   巡检记录
  change       变更记录
  release      发布记录
  handover     值班交接
  other        其他

record_status:
  draft        草稿
  processing   处理中
  done         已完成
  archived     已归档

record_priority:
  P0           紧急
  P1           高
  P2           中
  P3           低
```

---

## 7.3 工作记录表

### `work_record.wr_template`

```sql id="z7rl0p"
create schema if not exists work_record;

create table work_record.wr_template (
  id uuid primary key,
  tenant_id uuid not null,
  name varchar(128) not null,
  code varchar(64) not null,
  description text,
  enabled boolean not null default true,
  schema_json jsonb not null default '{}'::jsonb,
  created_by uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);
```

### `work_record.wr_template_field`

```sql id="hjpjn9"
create table work_record.wr_template_field (
  id uuid primary key,
  tenant_id uuid not null,
  template_id uuid not null,
  field_name varchar(128) not null,
  field_code varchar(128) not null,
  field_type varchar(32) not null,

  required boolean not null default false,
  default_value text,

  option_source varchar(32) not null default 'static',
  dict_code varchar(128),
  options_json jsonb not null default '[]'::jsonb,

  list_visible boolean not null default false,
  filterable boolean not null default false,
  statistical boolean not null default false,

  sort_order integer not null default 0,
  enabled boolean not null default true,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (tenant_id, template_id, field_code)
);
```

### `work_record.wr_record`

```sql id="uov8mj"
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

第一版就这 5 张表：

```text id="g7a37s"
platform_dict_type
platform_dict_item
work_record.wr_template
work_record.wr_template_field
work_record.wr_record
```

够了。

---

# 8. 字典与表单设计器关系

字段选项来源分两种。

## 8.1 静态选项

适合某个表单独有的选项。

```json id="eduxbh"
{
  "fieldName": "影响范围",
  "fieldCode": "impact_scope",
  "fieldType": "select",
  "optionSource": "static",
  "options": [
    { "label": "无影响", "value": "none" },
    { "label": "部分影响", "value": "partial" },
    { "label": "严重影响", "value": "major" }
  ]
}
```

## 8.2 字典选项

适合通用枚举。

```json id="ya66be"
{
  "fieldName": "优先级",
  "fieldCode": "priority",
  "fieldType": "select",
  "optionSource": "dict",
  "dictCode": "record_priority"
}
```

原则：

```text id="3wdo4b"
通用枚举走字典表
临时枚举走 options_json
```

---

# 9. 后端设计

## 9.1 `aiops-platform` 增加字典能力

```text id="l8qpao"
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

## 9.2 新增 `aiops-work-record`

```text id="1du2qx"
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
   │  ├─ WorkRecordFieldService.java
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
      ├─ excel
      └─ validator
```

## 9.3 Maven 接入

根 `pom.xml` 新增：

```xml id="pvlzqn"
<module>modules/aiops-work-record</module>
```

`apps/aiops-server/pom.xml` 新增：

```xml id="qqnrqu"
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record</artifactId>
  <version>${project.version}</version>
</dependency>
```

---

# 10. API 设计

## 10.1 字典 API

```text id="fb2xbf"
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

## 10.2 表单模板 API

```text id="7ia5lx"
GET    /api/work-record/templates
POST   /api/work-record/templates
GET    /api/work-record/templates/{id}
PUT    /api/work-record/templates/{id}
DELETE /api/work-record/templates/{id}
```

## 10.3 字段 API

```text id="1uq0n6"
GET    /api/work-record/templates/{templateId}/fields
POST   /api/work-record/templates/{templateId}/fields
PUT    /api/work-record/templates/{templateId}/fields/{fieldId}
DELETE /api/work-record/templates/{templateId}/fields/{fieldId}
POST   /api/work-record/templates/{templateId}/fields/reorder
```

## 10.4 工作记录 API

```text id="x9i6ws"
GET    /api/work-record/records
POST   /api/work-record/records
GET    /api/work-record/records/{id}
PUT    /api/work-record/records/{id}
DELETE /api/work-record/records/{id}
GET    /api/work-record/records/export
```

---

# 11. 前端设计

## 11.1 目录结构

```text id="6h9ceq"
web/console/src/pages/platform
└─ DictionaryPage.tsx
```

```text id="2l0bnh"
web/console/src/pages/work-record
├─ WorkRecordListPage.tsx
├─ WorkRecordEditPage.tsx
├─ WorkRecordDetailPage.tsx
└─ WorkRecordTemplateDesignerPage.tsx
```

```text id="w7f9tx"
web/console/src/features/work-record
├─ api.ts
├─ types.ts
├─ constants.ts
├─ components
│  ├─ DynamicForm.tsx
│  ├─ DynamicTable.tsx
│  ├─ FieldPalette.tsx
│  ├─ FieldCanvas.tsx
│  ├─ FieldPropertyPanel.tsx
│  ├─ DictSelect.tsx
│  └─ RecordFilterBar.tsx
└─ hooks
   ├─ useWorkRecords.ts
   ├─ useWorkRecordTemplates.ts
   └─ useDictItems.ts
```

## 11.2 新增路由

```tsx id="c7cfwe"
<Route path="/app/platform/dictionaries" element={<DictionaryPage />} />

<Route path="/app/work-records" element={<WorkRecordListPage />} />
<Route path="/app/work-records/create" element={<WorkRecordEditPage />} />
<Route path="/app/work-records/:recordId" element={<WorkRecordDetailPage />} />
<Route path="/app/work-records/:recordId/edit" element={<WorkRecordEditPage />} />
<Route path="/app/work-records/designer" element={<WorkRecordTemplateDesignerPage />} />
```

---

# 12. 页面终版设计

## 12.1 字典管理页

```text id="d998na"
左侧：字典类型
  - 工作记录类型
  - 工作记录状态
  - 优先级
  - 环境类型
  - 是否
  - 处理结果

右侧：字典项表格
  - 标签
  - 值
  - 颜色
  - 排序
  - 启用状态
  - 操作
```

能力：

```text id="mspbeh"
新增字典类型
编辑字典类型
启用 / 禁用
新增字典项
编辑字典项
调整排序
```

## 12.2 表单设计页

三栏布局：

```text id="b6qbk7"
左侧：字段组件区
中间：表单画布
右侧：字段属性面板
```

字段组件：

```text id="hev07k"
单行文本
多行文本
数字
日期
日期时间
单选
多选
人员
开关
```

字段属性：

```text id="7ylv58"
字段名称
字段编码
字段类型
是否必填
默认值
选项来源
选择字典
自定义选项
是否列表展示
是否支持筛选
是否支持统计
排序
```

## 12.3 记录列表页

```text id="d7zcvz"
顶部：
  新建记录
  导出

快捷视图：
  全部
  我的
  今日
  本周
  待处理
  已完成

筛选：
  关键字
  日期范围
  负责人
  状态
  记录类型
  自定义字段筛选

表格：
  标题
  类型
  状态
  负责人
  日期
  自定义字段...
  操作
```

## 12.4 新建 / 编辑记录页

根据管理员设计的表动态渲染。

基础字段：

```text id="k8og90"
标题
记录时间
负责人
状态
```

动态字段：

```text id="tcd72u"
来自 wr_template_field
保存到 custom_data_json
```

示例保存数据：

```json id="v52ezf"
{
  "title": "每日工作记录",
  "status": "done",
  "ownerId": "user-id",
  "recordTime": "2026-07-06T10:00:00+09:00",
  "customData": {
    "record_type": "daily",
    "work_content": "处理服务器巡检异常",
    "priority": "P2",
    "env": "prod",
    "need_follow_up": true
  }
}
```

---

# 13. 权限设计

## 13.1 权限码

```text id="nshgvf"
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

## 13.2 角色

```text id="eutssu"
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

第一版权限可以先简单实现：

```text id="lntaa0"
管理员看全部
普通用户看自己的
```

后续再细化到字段权限。

---

# 14. AI 维护边界

你最担心的是 AI 后续维护失控，所以要在工程上限制它。

## 14.1 AI 可修改范围

每个 phase 默认只允许修改：

```text id="zs826s"
modules/aiops-work-record/**
modules/aiops-platform/**/dictionary/**
web/console/src/pages/work-record/**
web/console/src/features/work-record/**
web/console/src/pages/platform/DictionaryPage.tsx
```

不允许乱改：

```text id="kldiid"
aiops-security
aiops-user
aiops-tenant
aiops-alert
aiops-incident
aiops-inspection
全局 Layout
全局 api client 大量重构
```

## 14.2 必须补测试

当前工程已经配置 Spotless、Checkstyle，并且 `aiops-server` 也有 ArchUnit、Testcontainers PostgreSQL 测试依赖。

建议新增：

```text id="ow42m2"
DictionaryServiceTest
WorkRecordTemplateServiceTest
WorkRecordFieldServiceTest
WorkRecordServiceTest
WorkRecordQueryServiceTest
WorkRecordControllerTest
WorkRecordArchitectureTest
```

---

# 15. 路线图

## Phase 1：菜单与空页面

目标：

```text id="7t1ffy"
字典管理菜单出现
工作记录菜单出现
页面能打开
```

内容：

```text id="yeohp0"
新增路由
新增空页面
新增菜单种子数据
确认用户管理 / 角色权限复用
```

耗时：

```text id="j1a0py"
1 天
```

---

## Phase 2：字典管理

目标：

```text id="qd8u4t"
平台可维护字典
表单设计器可引用字典
```

内容：

```text id="q3f41a"
platform_dict_type
platform_dict_item
字典类型 CRUD
字典项 CRUD
内置字典初始化
前端字典管理页
```

耗时：

```text id="z9gb0o"
2～3 天
```

---

## Phase 3：工作记录模块骨架

目标：

```text id="i8wff0"
新增 aiops-work-record 模块
能创建模板和字段
```

内容：

```text id="xkckux"
新增 Maven 模块
接入 aiops-server
新增 work_record schema
新增 wr_template
新增 wr_template_field
模板 CRUD
字段 CRUD
```

耗时：

```text id="xsmfrx"
2～3 天
```

---

## Phase 4：管理员拖拽表单设计

目标：

```text id="6tm8fm"
管理员能拖拽配置一张工作记录表
```

内容：

```text id="an2kr6"
字段组件区
表单画布
字段属性面板
字段排序
字段保存
字段预览
字典选项绑定
```

耗时：

```text id="i4ch5g"
4～6 天
```

---

## Phase 5：用户填写工作记录

目标：

```text id="78c6d4"
用户能按模板填写记录
```

内容：

```text id="r52145"
wr_record 表
动态表单渲染
保存 custom_data_json
编辑记录
详情页
```

耗时：

```text id="y4l3j8"
3～5 天
```

---

## Phase 6：记录列表、筛选、导出

目标：

```text id="cxfm6l"
管理员能查看、筛选、导出记录
```

内容：

```text id="kz2uh8"
动态表格列
基础筛选
自定义字段筛选
我的记录 / 全部记录
Excel 导出
```

耗时：

```text id="s9h9zw"
4～6 天
```

---

# 16. MVP 总工期

如果你一个人 + AI：

```text id="rfzg9u"
能演示：7～10 天
内部可试用：2～3 周
做得比较稳：4～5 周
```

推荐不要赶太狠。

最稳版本：

```text id="rn6rio"
Phase 1 + Phase 2 + Phase 3 + Phase 4 + Phase 5 + Phase 6
```

大概：

```text id="q8yw0j"
3～4 周
```

---

# 17. 后续增强路线

MVP 之后再加：

```text id="v7leam"
1. Excel 导入
2. 统计分析
3. 评论时间线
4. 附件上传
5. 关联告警 / 巡检 / 事件
6. 从告警一键生成记录
7. 从巡检报告一键生成记录
8. AI 自动总结工作记录
9. AI 根据历史记录推荐处理方式
10. 独立产品化部署
```

但这些都不是第一版必须。

---

# 18. 终版取舍

最终取舍是：

```text id="i5kgv0"
做用户管理：复用现有
做角色权限：复用现有
做字典管理：新增到 aiops-platform
做拖拽制表：新增到 aiops-work-record
做记录填写：新增到 aiops-work-record
做筛选导出：新增到 aiops-work-record
不做微前端
不做完整低代码引擎
不做完整工单系统
不做复杂流程
```

最终系统形态：

```text id="bu1fuo"
AegisOps 运维中台
├─ 用户 / 权限 / 字典
└─ 可配置工作记录
   ├─ 管理员设计表
   ├─ 用户填写记录
   ├─ 管理员查看筛选
   └─ 导出沉淀
```

这就是我认为最稳、最适合当前 `ai-ops mvp` 的终版设计。

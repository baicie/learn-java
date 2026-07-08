---
title: 工作记录 Phase 01 Portal 壳、导航与国际化
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/phase-00-baseline-and-contract.md
  - .agents/skills/portal/SKILL.md
---

# Phase 01：Portal 壳、导航与国际化

## 1. 目标

本 Phase 不做复杂业务逻辑，目标是把工作记录模块以 portal-native 的方式接入后台壳：

```text
1. 建立 i18n key-value 基础设施。
2. 追加工作记录和平台管理菜单。
3. 建立全部目标路由。
4. 建立页面 layout、loading、empty、error 的统一骨架。
5. 不删除模板已有菜单和页面。
6. 预留工作日历 i18n key 与路由位置，页面可延后。
```

## 2. 设计原则

```text
1. 不侵入 shadcn/ui 组件。
2. 不改模板已有 users/tasks/settings 的数据和页面。
3. 新业务只放 features/<feature>/。
4. 路由只放 routes/_authenticated/。
5. 菜单先静态追加，后续若接后端导航再单独 Phase。
6. 关键业务文案先国际化，模板遗留英文文案后续清理。
```

## 3. 国际化方案

采用 TypeScript key-value 文件：

```text
web/portal/src/i18n/
├─ index.ts
└─ locales/
   └─ zh-CN/
      ├─ common.ts
      ├─ platform.ts
      ├─ work-records.ts
      └─ index.ts
```

### 3.1 为什么不用完整 i18n 框架

第一版不引入 i18next/react-intl：

```text
1. 当前只有中文主界面诉求。
2. 需要的是 key 管理，不是运行时多语言切换。
3. 引入框架会扩散到全模板页面，增加改造面。
4. TypeScript key 可以获得编译期校验。
```

后续如果确需多语言：

```text
1. 保持 key 不变。
2. 将 messages map 替换为 i18next backend。
3. 页面调用仍使用 t(key)。
```

### 3.2 key 命名

```text
common.*
platform.nav.*
platform.dictionaries.*
platform.calendars.*
platform.roles.*
workRecords.nav.*
workRecords.list.*
workRecords.template.*
workRecords.designer.*
workRecords.form.*
workRecords.detail.*
workRecords.export.*
```

### 3.3 示例

```ts
export const workRecords = {
  'workRecords.nav.root': '工作记录',
  'workRecords.nav.list': '记录列表',
  'workRecords.nav.designer': '表单设计',
  'workRecords.list.title': '记录列表',
  'workRecords.list.description': '查看、筛选与导出工作记录。',
} as const
```

## 4. 菜单设计

静态菜单追加到 `sidebar-data.ts`。

```text
工作记录
├─ 记录列表
└─ 表单设计

平台管理
├─ 用户管理
├─ 角色权限
├─ 字典管理
└─ 工作日历（可选，页面延后时不挂菜单）
```

### 4.1 不做成菜单的内容

这些是字典项，不是菜单：

```text
日常记录
故障记录
巡检记录
变更记录
发布记录
值班交接
```

原因：

```text
1. 它们会因租户和团队变化。
2. 它们是 record_type 字典项。
3. 做成菜单会导致每种记录类型都要建路由。
```

## 5. 路由设计

```text
routes/_authenticated/
├─ work-records/
│  ├─ index.tsx
│  ├─ new.tsx
│  ├─ $recordId.tsx
│  ├─ $recordId.edit.tsx
│  └─ designer.tsx
└─ platform/
   ├─ dictionaries.tsx
   ├─ calendars.tsx
   └─ roles.tsx
```

URL：

```text
/work-records
/work-records/new
/work-records/$recordId
/work-records/$recordId/edit
/work-records/designer
/platform/dictionaries
/platform/calendars
/platform/roles
```

`/platform/calendars` 是 Phase 02A 的可选页面。严格 MVP 只做后端 API 时，该路由和菜单可以不启用，但 i18n key 与目录规划应保留。

## 6. 页面壳标准

每个页面必须包含：

```text
Header
Search
ThemeSwitch
ConfigDrawer
ProfileDropdown
Main
页面标题
页面说明
主要操作按钮区
内容区
```

`WorkRecordsLayout` 可作为工作记录 feature 内部 layout，但不应上升为全局组件，避免过早抽象。

## 7. URL Search 契约

记录列表路由：

```ts
const recordsSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(20),
  templateId: z.string().optional().catch(''),
  status: z.array(z.string()).optional().catch([]),
  keyword: z.string().optional().catch(''),
})
```

后续动态字段筛选扩展：

```text
filter.<fieldCode>=value
filter.<fieldCode>=value1,value2
```

第一版可先序列化成：

```text
filters: JSON string
```

但长期推荐使用显式 search key，便于分享链接和调试。

## 8. 文件落点

```text
web/portal/src/i18n/*
web/portal/src/features/work-records/*
web/portal/src/features/dictionaries/*
web/portal/src/features/calendars/*
web/portal/src/features/roles/*
web/portal/src/routes/_authenticated/work-records/*
web/portal/src/routes/_authenticated/platform/*
web/portal/src/components/layout/data/sidebar-data.ts
```

## 9. 交互状态

### 9.1 Loading

```text
列表页：表格骨架
详情页：详情骨架
设计器页：左右面板骨架
字典页：左侧类型列表骨架 + 右侧表格骨架
```

### 9.2 Empty

```text
无模板：
  提示“还没有工作记录模板”
  action：新建模板

无记录：
  提示“暂无记录”
  action：新建记录

无字典：
  提示“暂无字典”
  action：新建字典
```

### 9.3 Error

业务错误交给 QueryCache / Mutation onError：

```text
API 401：统一跳登录
API 403：toast + 保留页面
API 400：toast 或表单字段错误
API 500：统一错误处理
```

## 10. 测试

必须覆盖：

```text
i18n key 返回中文
sidebar 包含新增菜单
路由 search schema 默认值
页面 layout 渲染标题
```

## 11. 验收

```bash
cd web/portal
pnpm run lint
pnpm run test
pnpm run build
```

验收标准：

```text
1. sidebar 能看到工作记录和平台管理。
2. 七个目标路由能打开。
3. routeTree.gen.ts 已更新。
4. 没有改动模板原业务页面。
5. i18n key 编译期可校验。
```

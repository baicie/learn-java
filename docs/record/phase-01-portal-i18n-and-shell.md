---
title: 工作记录 Phase 01 Portal 国际化与页面空壳
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
  - .agents/skills/portal/SKILL.md
---

# Phase 01：Portal 国际化与页面空壳

## 目标

先把 portal 的工作记录入口搭起来：

- 新增 ts key-value 国际化基础设施。
- 新增工作记录、平台管理路由空壳。
- 追加 sidebar 菜单，不删除模板原菜单。
- 所有业务文案先走关键 key，其余模板文案后续逐步替换。

## 前端文件

```text
web/portal/src/i18n/index.ts
web/portal/src/i18n/locales/zh-CN/work-records.ts
web/portal/src/i18n/locales/zh-CN/platform.ts
web/portal/src/i18n/locales/zh-CN/common.ts
web/portal/src/i18n/locales/zh-CN/index.ts
web/portal/src/i18n/i18n.test.ts

web/portal/src/features/work-records/index.tsx
web/portal/src/features/work-records/components/work-records-placeholder.tsx
web/portal/src/features/dictionaries/index.tsx
web/portal/src/features/roles/index.tsx

web/portal/src/routes/_authenticated/work-records/index.tsx
web/portal/src/routes/_authenticated/work-records/new.tsx
web/portal/src/routes/_authenticated/work-records/$recordId.tsx
web/portal/src/routes/_authenticated/work-records/$recordId.edit.tsx
web/portal/src/routes/_authenticated/work-records/designer.tsx
web/portal/src/routes/_authenticated/platform/dictionaries.tsx
web/portal/src/routes/_authenticated/platform/roles.tsx
```

## 国际化完整代码

```ts
// web/portal/src/i18n/locales/zh-CN/work-records.ts
export const workRecords = {
  "workRecords.nav.root": "工作记录",
  "workRecords.nav.list": "记录列表",
  "workRecords.nav.designer": "表单设计",
  "workRecords.list.title": "记录列表",
  "workRecords.list.description": "查看、筛选与导出工作记录。",
  "workRecords.new.title": "新建记录",
  "workRecords.edit.title": "编辑记录",
  "workRecords.detail.title": "记录详情",
  "workRecords.designer.title": "表单设计",
  "workRecords.designer.description": "配置工作记录模板、字段与字典绑定。",
} as const;
```

```ts
// web/portal/src/i18n/locales/zh-CN/platform.ts
export const platform = {
  "platform.nav.root": "平台管理",
  "platform.nav.users": "用户管理",
  "platform.nav.roles": "角色权限",
  "platform.nav.dictionaries": "字典管理",
  "platform.dictionaries.title": "字典管理",
  "platform.dictionaries.description": "维护工作记录和平台通用枚举。",
  "platform.roles.title": "角色权限",
  "platform.roles.description": "管理角色与权限分配。",
} as const;
```

```ts
// web/portal/src/i18n/locales/zh-CN/common.ts
export const common = {
  "common.loading": "加载中",
  "common.empty": "暂无数据",
  "common.create": "新建",
  "common.edit": "编辑",
  "common.save": "保存",
  "common.cancel": "取消",
  "common.delete": "删除",
  "common.export": "导出",
  "common.search": "搜索",
} as const;
```

```ts
// web/portal/src/i18n/locales/zh-CN/index.ts
import { common } from "./common";
import { platform } from "./platform";
import { workRecords } from "./work-records";

export const zhCN = {
  ...common,
  ...platform,
  ...workRecords,
} as const;

export type MessageKey = keyof typeof zhCN;
```

```ts
// web/portal/src/i18n/index.ts
import { zhCN, type MessageKey } from "./locales/zh-CN";

type Messages = Record<MessageKey, string>;

const messages: Messages = zhCN;

export function t(key: MessageKey): string {
  return messages[key] ?? key;
}

export type { MessageKey };
```

## 页面空壳完整代码

```tsx
// web/portal/src/features/work-records/components/work-records-placeholder.tsx
import { Header } from "@/components/layout/header";
import { Main } from "@/components/layout/main";
import { ProfileDropdown } from "@/components/profile-dropdown";
import { Search } from "@/components/search";
import { ThemeSwitch } from "@/components/theme-switch";
import { t, type MessageKey } from "@/i18n";

type WorkRecordsPlaceholderProps = {
  titleKey: MessageKey;
  descriptionKey?: MessageKey;
};

export function WorkRecordsPlaceholder({
  titleKey,
  descriptionKey,
}: WorkRecordsPlaceholderProps) {
  return (
    <>
      <Header fixed>
        <Search className="me-auto" />
        <ThemeSwitch />
        <ProfileDropdown />
      </Header>
      <Main className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">{t(titleKey)}</h2>
            {descriptionKey ? (
              <p className="text-muted-foreground">{t(descriptionKey)}</p>
            ) : null}
          </div>
        </div>
      </Main>
    </>
  );
}
```

```tsx
// web/portal/src/features/work-records/index.tsx
import { WorkRecordsPlaceholder } from "./components/work-records-placeholder";

export function WorkRecords() {
  return (
    <WorkRecordsPlaceholder
      titleKey="workRecords.list.title"
      descriptionKey="workRecords.list.description"
    />
  );
}
```

## 路由代码样例

```tsx
// web/portal/src/routes/_authenticated/work-records/index.tsx
import z from "zod";
import { createFileRoute } from "@tanstack/react-router";
import { WorkRecords } from "@/features/work-records";

const recordsSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(20),
  templateId: z.string().optional().catch(""),
  status: z.array(z.string()).optional().catch([]),
  keyword: z.string().optional().catch(""),
});

export const Route = createFileRoute("/_authenticated/work-records/")({
  validateSearch: recordsSearchSchema,
  component: WorkRecords,
});
```

## 菜单增量

只在 `web/portal/src/components/layout/data/sidebar-data.ts` 追加分组：

```tsx
{
  title: t('workRecords.nav.root'),
  items: [
    { title: t('workRecords.nav.list'), url: '/work-records', icon: ClipboardList },
    { title: t('workRecords.nav.designer'), url: '/work-records/designer', icon: Settings2 },
  ],
},
{
  title: t('platform.nav.root'),
  items: [
    { title: t('platform.nav.users'), url: '/users', icon: Users },
    { title: t('platform.nav.roles'), url: '/platform/roles', icon: ShieldCheck },
    { title: t('platform.nav.dictionaries'), url: '/platform/dictionaries', icon: BookOpen },
  ],
}
```

## 单元测试

```ts
// web/portal/src/i18n/i18n.test.ts
import { describe, expect, it } from "vitest";
import { t } from "./index";

describe("i18n", () => {
  it("returns zh-CN text by key", () => {
    expect(t("workRecords.nav.root")).toBe("工作记录");
  });

  it("keeps key type strict for known messages", () => {
    const label = t("platform.nav.dictionaries");
    expect(label).toBe("字典管理");
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

- 页面路由可生成 `routeTree.gen.ts`。
- 访问 `/work-records`、`/work-records/designer`、`/platform/dictionaries`、`/platform/roles` 不白屏。
- 菜单只追加，不删除模板原有菜单。

---
title: Portal 顶部页面页签布局实现计划
type: design
status: accepted
phase: phase-21
owner: ai
created: 2026-07-19
updated: 2026-07-19
related:
  - docs/designs/phase-21/2026-07-14-portal-source-layout-and-work-record-completion.md
---

# Portal 顶部页面页签布局实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development and verification-before-completion to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 Default、Compact、Full layout 现有行为的前提下，增加可选的 Tabs layout，让访问过的页面像浏览器页签一样显示在内容区顶部、可切换和关闭。

**Architecture:** `LayoutProvider` 继续管理现有 sidebar variant/collapsible，并新增独立持久化的 `pageTabs` 状态，避免改变旧 cookie 与布局语义。新的 `PageTabs` 组件监听 TanStack Router 当前地址，以 pathname + search 为页签身份；页签列表保存在 sessionStorage，仅在 Tabs layout 启用时渲染。

**Tech Stack:** React 19、TypeScript、TanStack Router、Vitest Browser、Tailwind CSS v4、现有 shadcn Button/ScrollArea/Tooltip 原语。

---

### Task 1: 扩展布局设置状态

**Files:**

- Modify: `web/portal/src/context/layout-provider.tsx`
- Modify: `web/portal/src/components/config-drawer.tsx`
- Test: `web/portal/src/components/config-drawer.test.tsx`
- Create: `web/portal/src/assets/custom/icon-layout-tabs.tsx`

- [x] **Step 1: Write the failing test**

新增浏览器测试：选择 `Tabs` 后 `layout_page_tabs=true`；再选择 `Default` 后变为 `false`；布局局部重置与全局重置也恢复 `false`，且原有 Default/Compact/Full 断言保持不变。

- [x] **Step 2: Run test to verify it fails**

Run: `pnpm run test src/components/config-drawer.test.tsx`

Expected: FAIL，因为页面尚无 `Select tabs layout` 单选项，也未写入 `layout_page_tabs` cookie。

- [x] **Step 3: Write minimal implementation**

在 `LayoutProvider` 新增：

```ts
const LAYOUT_PAGE_TABS_COOKIE_NAME = 'layout_page_tabs'
const DEFAULT_PAGE_TABS = false

pageTabs: boolean
setPageTabs: (enabled: boolean) => void
```

`LayoutConfig` 的受控值优先返回 `tabs`；选择旧布局先关闭 page tabs，再执行原 sidebar 行为；选择 `tabs` 时打开 sidebar 并启用 page tabs。新增四宫格 Tabs 预览图标。

- [x] **Step 4: Run test to verify it passes**

Run: `pnpm run test src/components/config-drawer.test.tsx`

Expected: PASS，且原布局测试全部通过。

### Task 2: 建立纯页签状态模型

**Files:**

- Create: `web/portal/src/components/layout/page-tabs-model.ts`
- Test: `web/portal/src/components/layout/page-tabs-model.test.ts`

- [x] **Step 1: Write the failing tests**

覆盖 `visitTab` 去重并更新标题、`closeTab` 关闭非活动页不导航、关闭活动页选择右侧优先再左侧、最后一个页签保持不关闭，以及损坏 sessionStorage 数据回退为空数组。

- [x] **Step 2: Run tests to verify they fail**

Run: `pnpm run test src/components/layout/page-tabs-model.test.ts`

Expected: FAIL，因为模型文件尚不存在。

- [x] **Step 3: Write minimal implementation**

定义：

```ts
type PageTab = { href: string; title: string }
type CloseTabResult = { tabs: PageTab[]; nextHref?: string }
```

导出纯函数 `visitTab`、`closeTab`、`readStoredTabs`，把路由 UI 与列表状态转换分离。

- [x] **Step 4: Run tests to verify they pass**

Run: `pnpm run test src/components/layout/page-tabs-model.test.ts`

Expected: PASS。

### Task 3: 渲染并接入顶部页签栏

**Files:**

- Create: `web/portal/src/components/layout/page-tabs.tsx`
- Test: `web/portal/src/components/layout/page-tabs.test.tsx`
- Modify: `web/portal/src/components/layout/authenticated-layout.tsx`

- [x] **Step 1: Write the failing component tests**

使用可控的路由状态验证：当前页面自动加入并激活；点击页签导航；关闭非活动页不导航；关闭活动页导航相邻页；关闭按钮阻止触发页签导航；禁用 Tabs layout 时不渲染页签栏。

- [x] **Step 2: Run tests to verify they fail**

Run: `pnpm run test src/components/layout/page-tabs.test.tsx`

Expected: FAIL，因为 `PageTabs` 尚不存在。

- [x] **Step 3: Write minimal implementation**

`PageTabs` 用 `useLocation` 监听完整 href，用导航配置解析静态页面标题，并为详情/编辑/新建页面提供稳定的路径回退标题。页签使用 `Link` 切换、`Button` 关闭，横向溢出可滚动；`AuthenticatedLayout` 在 `SidebarInset` 内、Outlet 上方按 `pageTabs` 条件渲染。

- [x] **Step 4: Run tests to verify they pass**

Run: `pnpm run test src/components/layout/page-tabs.test.tsx src/components/layout/page-tabs-model.test.ts src/components/config-drawer.test.tsx`

Expected: PASS。

### Task 4: 完整验证与差异审查

**Files:**

- Verify all modified files above.

- [x] **Step 1: Run Portal quality gates**

Run from `web/portal`:

```bash
pnpm run lint
pnpm run typecheck
pnpm run test
pnpm run knip
pnpm run build
```

Expected: 所有命令 exit 0，测试无失败，构建成功。

Verification note: `lint`、`typecheck`、全量 `test` 与 `build` 通过；`knip` 仍被仓库既有的 11 个未使用导出类型阻断，本次新增文件未出现在报告中。

- [x] **Step 2: Run docs validation**

Run: `pnpm exec tsx scripts/docs.ts check`（仓库根目录）

Expected: exit 0，本文 frontmatter、路径与命名符合治理规则。

- [x] **Step 3: Review scope and compatibility**

检查 `git diff --check` 与 `git diff --stat`：不修改 `src/components/ui/`，不新增依赖，不改变旧 layout cookie 含义，所有实现仅位于独立 `codex/top-tabs-layout` worktree。

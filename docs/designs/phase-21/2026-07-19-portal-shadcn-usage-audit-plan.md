---
title: Portal shadcn/ui 使用审计实施计划
type: design
status: accepted
phase: phase-21
owner: ai
created: 2026-07-19
updated: 2026-07-19
related:
  - https://github.com/baicie/ai-ops/issues/49
  - .agents/skills/portal/references/shadcn-component-selection.md
---

# Portal shadcn/ui Usage Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 审计 Portal 中应复用 shadcn/ui 或项目业务组件却仍采用手写实现的位置，并形成可独立执行的整改任务。

**Architecture:** 只扫描 `web/portal/src/auth`、`pages`、`components` 下的生产 TSX，排除测试 mock 与 `components/ui` 生成文件。先用静态模式定位候选，再阅读上下文区分应整改、合理例外和暂缓项；本轮不修改生产行为。

**Tech Stack:** React 19、TypeScript、shadcn/ui（Radix）、Tailwind CSS v4、ripgrep、GitHub Issues。

---

### Task 1: 建立可复现的扫描基线

**Files:**

- Inspect: `web/portal/src/auth/**/*.tsx`
- Inspect: `web/portal/src/pages/**/*.tsx`
- Inspect: `web/portal/src/components/**/*.tsx`
- Exclude: `web/portal/src/components/ui/**/*.tsx`
- Exclude: `web/portal/src/**/*.test.tsx`

- [x] **Step 1: 扫描原生交互控件**

  Run: `rg -n --glob '*.tsx' -g '!**/*.test.tsx' -g '!web/portal/src/components/ui/**' '<(button|input|select|textarea|table|dialog)\b' web/portal/src/auth web/portal/src/pages web/portal/src/components`

  Expected: 只剩需要人工判断的生产代码候选。

- [x] **Step 2: 扫描手写状态色、加载态与间距反模式**

  Run: `rg -n --glob '*.tsx' -g '!**/*.test.tsx' -g '!web/portal/src/components/ui/**' 'animate-pulse|bg-(red|green|amber|slate)-|text-(red|green|amber|slate)-|space-[xy]-' web/portal/src/auth web/portal/src/pages web/portal/src/components`

  Expected: 输出语义色、Badge、Alert、Skeleton 和布局约定候选。

- [x] **Step 3: 校验弹层无障碍结构**

  Run: 对每个 `DialogContent`、`SheetContent`、`AlertDialogContent` 文件统计对应 Title 与 Description。

  Expected: 每个弹层实例都有对应标题和描述。

### Task 2: 分类候选并形成审计表

**Files:**

- Create: `docs/reviews/phase-21/2026-07-19-portal-shadcn-usage-audit.md`

- [x] **Step 1: 阅读候选上下文**

  对每个命中项确认它是生产交互、测试 mock、主题预览、领域色还是可复用组件缺口。

- [x] **Step 2: 记录决策信息**

  每项记录文件位置、当前实现、推荐组件、收益、风险、回归范围和“应整改 / 合理例外 / 暂缓”结论。

- [x] **Step 3: 记录扫描覆盖和限制**

  在审计文档中写明扫描目录、排除项、统计数据和可复现命令，避免把静态命中数误当成缺陷数。

### Task 3: 拆分后续整改任务

**Files:**

- External: GitHub Issues
- Modify: `docs/reviews/phase-21/2026-07-19-portal-shadcn-usage-audit.md`

- [x] **Step 1: 创建 Input 复用整改 Issue**

  范围限定为 `PasswordInput` 复用项目 `Input`，保留显示/隐藏密码行为与现有浏览器测试。

- [x] **Step 2: 创建工作记录设计器语义组件整改 Issue**

  范围限定为 FormCanvas、SchemaDiffPanel、FormPreview 的 Badge、Alert、语义色和键盘交互。

- [x] **Step 3: 创建异步状态统一整改 Issue**

  范围限定为 RecordHistoryCard、PlatformUsersPage 和工作记录空态，复用 AsyncState、Alert 与 Skeleton。

- [x] **Step 4: 回填 Issue 链接**

  将新 Issue 编号写入审计文档的“后续任务”章节。

### Task 4: 验证并交付

**Files:**

- Verify: `docs/designs/phase-21/2026-07-19-portal-shadcn-usage-audit-plan.md`
- Verify: `docs/reviews/phase-21/2026-07-19-portal-shadcn-usage-audit.md`

- [x] **Step 1: 校验文档格式与治理规则**

  Run: `pnpm exec prettier --config .prettierrc --check docs/designs/phase-21/2026-07-19-portal-shadcn-usage-audit-plan.md docs/reviews/phase-21/2026-07-19-portal-shadcn-usage-audit.md`

  Expected: `All matched files use Prettier code style!`

- [x] **Step 2: 校验文档索引**

  Run: `pnpm exec tsx scripts/docs.ts check`

  Expected: 文档检查通过且索引与生成结果一致。

- [ ] **Step 3: 提交并创建 PR**

  Commit: `docs(portal): 完成 shadcn 组件使用审计`

  PR 必须关联并关闭 Issue #49，说明本轮仅做审计、未修改生产行为。

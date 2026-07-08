---
title: portal 测试栈选型（vitest browser + Playwright e2e）
type: adr
status: accepted
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-08
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/SKILL.md §6.15.8
  - .agents/skills/aegisops/SKILL.md §18
  - docs/record/index.md
  - docs/record/index.md §20
  - docs/record/phase-04-formily-designer.md
  - docs/adr/0005-work-record-designer-formily-core-only.md
  - web/portal/package.json
  - web/portal/vite.config.ts
---

# ADR 0006: portal 测试栈选型（vitest browser + Playwright e2e）

## 状态

- **已接受（2026-07-08）**
- 触发来源：ADR 0005 设计器实施前置依赖、SKILL §6.15.8 测试纪律、SKILL §18 Phase 测试要求
- 本 ADR 是阶段性决议，技术栈替换必须重新走 ADR 流程

## 背景

`web/portal/package.json` 已经预装：

```text
vitest                       ^4.1.10
@vitest/browser-playwright   ^4.1.10
@vitest/coverage-v8          ^4.1.10
@vitest/ui                   ^4.1.10
vitest-browser-react         ^2.2.0
playwright                   1.61.1
@faker-js/faker              ^10.5.0
```

并已写好脚本：

```text
test                → vitest run --browser.headless
test:watch          → vitest --browser.headless
test:ui             → vitest --ui --browser.headless
test:browser        → vitest
test:coverage       → vitest run --coverage --browser.headless
test:browser:install → playwright install chromium --with-deps
```

`vite.config.ts` 通过 `/// <reference types="vitest/config" />` + `test: { ... }` 块把 vitest 配置收口在 vite.config 中（官方支持的"单文件配置"模式）。`src/` 下已经存在 38+ 个 `*.test.ts(x)` 与若干 `__screenshots__` 目录，**组件级浏览器单测已经跑通**。

## 现状盘点

| 维度 | 现状 |
|---|---|
| 组件/纯函数单测 | ✅ vitest browser 模式，38+ 文件，覆盖 components / data / hooks / lib / stores / i18n |
| 浏览器 provider | ✅ `@vitest/browser-playwright` 默认走 Chromium |
| 覆盖率 | ✅ `@vitest/coverage-v8`，脚本 `test:coverage` 已就位 |
| UI 调试 | ✅ `@vitest/ui`，脚本 `test:ui` 已就位 |
| 截图回归 | ✅ 已存在 `__screenshots__` 目录与基础用例 |
| 跨页 e2e（路由 + 真实浏览器） | ❌ 缺失 |
| e2e 配置（独立 `playwright.config.ts`） | ❌ 缺失 |
| 拦截 HTTP 替身（MSW / nock） | ❌ 缺失 |
| e2e 跑通后是否进 CI | ❌ 脚本 `e2e` 不在 `package.json` |

## 决策

1. **组件 / 纯函数单测继续用 vitest browser**（@vitest/browser-playwright + vitest-browser-react）。覆盖：
   - 纯函数（`field-types.ts` / `schema-builder.ts` / `formily-schema.ts` / `dict-schema-injector.ts`）
   - 组件渲染与交互（`designer-canvas.tsx` / `designer-palette.tsx` / `designer-property-panel.tsx` / `designer-preview.tsx`）
   - 截图回归（属性面板、画布、运行时表单）

2. **跨页端到端测试用 Playwright 独立 e2e 模式**，不混进 vitest：
   - 新增 `web/portal/playwright.config.ts`（独立配置）
   - 新增 `web/portal/e2e/` 目录与按 feature 分组（`work-records/*.spec.ts` 等）
   - 新增 `package.json` 脚本：
     ```text
     e2e              → playwright test
     e2e:ui           → playwright test --ui
     e2e:install      → playwright install chromium --with-deps
     e2e:codegen      → playwright codegen
     ```
   - 跨页 / 路由 / 表单提交 / 列表筛选 / 导出按钮、真实 fetch 行为 → e2e
   - 组件内状态、回调、setter 行为 → vitest

3. **HTTP 替身统一用 MSW v2**（vitest 与 e2e 共享 mock handlers），理由：
   - MSW v2 在 vitest jsdom/browser 模式下都可跑
   - MSW v2 在 Playwright 走 service worker，无侵入
   - `handlers/` 与 `tests/fixtures/` 在 vitest 与 e2e 共享，避免双写

4. **e2e 默认关闭，仅在 PR 标 `e2e` 或手动触发时跑**：
   - 本地：`pnpm --filter web/portal e2e` / `pnpm --filter web/portal e2e:ui`
   - CI：默认工作流跑 vitest，e2e 由 `workflow_dispatch` 或 `pull_request` label `e2e` 触发
   - 失败必须上传 Playwright `html` 报告与 trace

5. **不引入 Cypress / WebdriverIO / Puppeteer**，避免双浏览器引擎、双 mock 框架、双 CI 路径。

6. **CI 脚本与本地脚本同步**：
   - `scripts/ci/frontend.sh` 增加 vitest 必跑（`pnpm -C web/portal run test`）
   - 新增 `scripts/ci/frontend-e2e.sh` 跑 Playwright e2e（默认 dispatch 触发）
   - `scripts/ci/verify-local.sh` 与 e2e 解耦，但文档化 e2e 入口

## 备选

- **A. Cypress**：生态成熟，但与已就位的 Playwright 二选一会造成浏览器与 CI 路径双倍。**不采纳**。
- **B. 把 e2e 全部塞进 vitest browser**（`@vitest/browser-playwright` 已能跑真实浏览器）：能少一个依赖，但失去了 trace 报告、`test.step`、`expect.toHaveScreenshot`、`page.goto` 等成熟 e2e 能力，且跨路由 SPA 导航在 vitest 下需要复杂 bootstrap。**不采纳**。
- **C. 等 Playwright v2 / Vitest 5 稳定再统一**：今日不写，技术债继续累积。**不采纳**。
- **D. 引入 Storybook + Storybook Test Runner 做视觉回归**：项目当前没有 Storybook 接入，引入成本远高于扩展现有 `__screenshots__`。**不采纳**。

## 影响

- 现有 vitest 脚本、配置、测试**全部保留**。
- 新增文件：
  - `web/portal/playwright.config.ts`
  - `web/portal/e2e/` 目录与首批 e2e 用例
  - `web/portal/tests/mocks/handlers.ts`（MSW v2 handlers）
  - `web/portal/tests/mocks/server.ts`（vitest 端）
  - `web/portal/tests/mocks/browser.ts`（Playwright 端）
  - `web/portal/scripts/check-e2e-coverage.ts`（可选，统计 e2e 覆盖路径）
- `package.json` 新增 `e2e*` 脚本与 `msw@^2` 依赖。
- 不动 vite.config 的 vitest 段；不动现有 `*.test.ts(x)`。
- 不动后端；不动 docs/record/index.md 既定 Phase 描述（仅 §20 各 Phase 的"前端单元测试"清单追加 e2e 路径）。

## 验证

- 单元测试：
  - `pnpm --filter web/portal run test` 全绿
  - `pnpm --filter web/portal run test:coverage` 覆盖率报告
  - 设计器相关文件：`field-types.test.ts` / `schema-builder.test.ts` / `designer-canvas.test.tsx` / `designer-property-panel.test.tsx` / `designer-preview.test.tsx` 全部新增并跑通
- e2e 首批必须用例：
  - 登录 → 工作记录列表 → 新建记录 → 提交 → 详情回填
  - 表单设计页：添加 9 类字段各一 → 编辑属性 → 保存 → 再次加载字段一致
  - 字典绑定：选择 dict 字段 → 运行时下拉枚举来自字典
- CI：
  - `scripts/ci/frontend.sh` 跑 vitest 必过
  - `scripts/ci/frontend-e2e.sh` 跑 Playwright 必过（默认 dispatch；label `e2e` PR 必跑）
  - 失败 trace / html 报告上传 artifact
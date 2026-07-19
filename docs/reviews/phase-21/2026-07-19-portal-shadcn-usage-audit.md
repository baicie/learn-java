---
title: Portal shadcn/ui 组件使用审计
type: review
status: accepted
phase: phase-21
owner: ai
created: 2026-07-19
updated: 2026-07-19
related:
  - https://github.com/baicie/ai-ops/issues/49
  - https://github.com/baicie/ai-ops/issues/51
  - https://github.com/baicie/ai-ops/issues/52
  - https://github.com/baicie/ai-ops/issues/53
  - .agents/skills/portal/references/shadcn-component-selection.md
---

# Portal shadcn/ui 组件使用审计

## 1. 结论

Portal 已经较完整地复用了 shadcn/ui。扫描的 123 个生产 TSX 文件中，仅发现 1 个原生表单控件实例；18 个 Dialog、Sheet、AlertDialog 内容实例均具有对应 Title 与 Description。

需要整改的重点不是“安装更多组件”，而是以下三类一致性问题：

1. 业务封装重复复制 shadcn `Input` 样式，后续升级可能漂移。
2. 工作记录设计器手写 Badge、Alert 和状态色，未复用已有原语。
3. 少数页面自行表达加载、错误和空态，未复用项目 `AsyncState` 组件。

本次审计不修改生产代码。高价值整改项拆为独立 Issue，以便分别走 TDD、视觉回归和可访问性验证。

## 2. 扫描范围

扫描目录：

- `web/portal/src/auth/`
- `web/portal/src/pages/`
- `web/portal/src/components/`

排除：

- `web/portal/src/components/ui/`：shadcn CLI 生成代码。
- `**/*.test.tsx`：测试中用于隔离依赖的最小原生 mock。
- 路由生成文件与非 UI TypeScript。

静态扫描用于发现候选，最终结论均结合组件上下文人工判断。行号基于 `1f354129`。

## 3. 审计表

| 位置                                                             | 当前实现                                             | 推荐组件或模式                                                        | 收益                                             | 风险与回归范围                                 | 结论                       |
| ---------------------------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------ | ---------------------------------------------- | -------------------------- |
| `components/password-input.tsx:23`                               | 原生 `<input>` 并复制完整 Input class                | 组合项目 `Input` + `Button`                                           | 跟随 shadcn Input 的焦点、非法态、暗色和尺寸更新 | 验证 ref、RHF field spread、disabled、显隐切换 | 应整改                     |
| `components/work-records/designer/form-canvas.tsx:43`            | 可点击 `div role="button"` 内嵌四个 Button           | 将选择触发区与操作 Button 分离，保持合法交互嵌套                      | 改善键盘语义和焦点顺序                           | 需覆盖 Enter/Space 选择与四个操作不冒泡        | 应整改                     |
| `components/work-records/designer/form-canvas.tsx:63`            | 三组手写圆角状态 `<span>`，含 amber/slate 原色       | `Badge` 的 outline、secondary 等现有 variant；业务状态映射集中维护    | 统一尺寸、暗色和状态表达                         | 对比锁定、禁用和字段类型三种状态               | 应整改                     |
| `components/work-records/designer/schema-diff-panel.tsx:23`      | 手写红/绿色边框和背景提示块                          | 失败使用 `Alert variant="destructive"`；成功使用默认 Alert + 语义图标 | 统一警告语义与暗色模式                           | 覆盖本地失败、发布成功、发布失败               | 应整改                     |
| `components/work-records/designer/form-preview.tsx:40`           | 必填星号使用 `text-red-500`                          | 复用 `FormFieldShell` 的必填语义或至少使用 `text-destructive`         | 消除硬编码原色，统一表单语义                     | 预览只读结构中需避免嵌套 label                 | 应整改                     |
| `components/work-records/runtime/record-history-card.tsx:15`     | Card 内手写加载文本、红色错误文本和空态文本          | `Skeleton`、`Alert` 或项目 `AsyncState` 组合                          | 状态反馈一致、错误态可访问                       | 覆盖 loading、error、empty、events 四态        | 应整改                     |
| `components/iam/platform-users-page.tsx:69`                      | 手写加载失败卡片和 Badge 状态                        | 页面级错误/空态复用 `QueryStateBoundary`、`ErrorState`、`EmptyState`  | 与资产、数据源页面状态一致                       | 保留重试按钮与权限态                           | 应整改                     |
| `components/work-records/list/record-table.tsx:39`               | 手写虚线空表格                                       | 项目 `EmptyState compact`                                             | 统一空态文案、图标和操作入口                     | 表格容器高度可能变化                           | 应整改                     |
| `components/work-records/runtime/record-extension-panel.tsx:158` | Tabs 内多处手写“暂无”文本                            | Tabs 保留；内容区按需复用 compact EmptyState                          | 减少重复空态结构                                 | 评论、附件、关系的紧凑布局需分别确认           | 暂缓，随对应功能整改       |
| `components/config-drawer.tsx:63`                                | Sheet 使用正确，但内容布局用 `space-y-6`             | 保留 Sheet；将布局改为 `flex flex-col gap-6`                          | 符合项目间距约定                                 | 纯样式、低风险                                 | 应整改，但不属于组件缺失   |
| `auth/sign-in.tsx:55`                                            | 已使用 Button、Input、Label；容器使用 `space-y-*`    | 组件选择保持，布局后续改 gap                                          | 组件复用正确                                     | 登录视觉回归                                   | 合理组件选择；样式另行治理 |
| `pages/settings/appearance/appearance-form.tsx:116`              | 主题缩略图硬编码白、slate 色                         | 保留有意的浅色/深色示例色                                             | 缩略图需要展示目标主题，而非跟随当前语义色       | 验证两个预览在任意当前主题下仍可区分           | 合理例外                   |
| `pages/calendars/index.tsx:610`                                  | 班休图例使用 red/emerald 领域色                      | 保留领域色，未来可抽为日历状态 token                                  | 颜色承担班休视觉编码                             | 同时保留文本标签，避免只靠颜色传达             | 合理例外                   |
| `components/iam/platform-user-table.tsx:132`                     | TableRow 与分页区域使用 `border-t`                   | 保留表格结构边界，不强行插入 Separator                                | 避免破坏 table DOM 与 sticky/hover 行为          | 表格视觉回归                                   | 合理例外                   |
| 15 个弹层文件，18 个 Content 实例                                | Dialog/Sheet/AlertDialog 均配套 Title 与 Description | 保持现状                                                              | 已满足 shadcn/Radix a11y 约束                    | 新增弹层继续纳入审计                           | 通过                       |
| 测试文件中的原生 button/input                                    | 测试 mock 用最小 DOM 替代复杂依赖                    | 保持最小 mock                                                         | 测试意图清晰且不进入产品 UI                      | 不把测试命中计入产品缺陷                       | 合理例外                   |

## 4. 组件安装判断

本轮没有发现必须新增 shadcn 组件才能解决的问题。已安装的 `Input`、`Badge`、`Alert`、`Skeleton`、`Separator`、`Dialog`、`Sheet`、`Table` 等足以覆盖高价值整改项。

`Empty`、`Field`、`Drawer`、`HoverCard` 等未安装组件不应仅为提高组件覆盖率而引入：

- 空态已有项目 `AsyncState.EmptyState`，优先复用业务封装。
- 表单已有 `FormFieldShell` 与 shadcn Form，先统一现有模式。
- Portal 当前桌面侧面板使用 Sheet，没有明确的移动端底部 Drawer 需求。
- 未发现必须使用 HoverCard 承载的悬停预览场景。

## 5. 后续任务

按风险与职责拆分为三个任务：

1. [#51 PasswordInput 复用项目 Input](https://github.com/baicie/ai-ops/issues/51)，保留既有表单行为。
2. [#52 工作记录设计器复用语义组件](https://github.com/baicie/ai-ops/issues/52)，统一 Badge、Alert、表单语义并修正字段选择交互。
3. [#53 统一 Portal 异步状态组件](https://github.com/baicie/ai-ops/issues/53)，覆盖加载、错误和空态。

每个任务都应先补或调整浏览器测试，观察测试因缺失行为而失败后再修改生产代码。

## 6. 可复现扫描

```powershell
rg -n --glob '*.tsx' `
  -g '!**/*.test.tsx' `
  -g '!web/portal/src/components/ui/**' `
  '<(button|input|select|textarea|table|dialog)\b' `
  web/portal/src/auth web/portal/src/pages web/portal/src/components

rg -n --glob '*.tsx' `
  -g '!**/*.test.tsx' `
  -g '!web/portal/src/components/ui/**' `
  'animate-pulse|bg-(red|green|amber|slate)-|text-(red|green|amber|slate)-|space-[xy]-' `
  web/portal/src/auth web/portal/src/pages web/portal/src/components
```

静态扫描不能直接判定缺陷：主题预览、日历领域色、表格边界和测试 mock 必须阅读上下文后分类。

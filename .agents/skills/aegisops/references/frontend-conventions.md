# Frontend Conventions (AegisOps Console)

本文件是 AegisOps 控制台前端工程的主题、组件、样式与代码组织规范。 所有规则与 `.agents/skills/aegisops/SKILL.md` §4.1、`.agents/skills/shadcn/SKILL.md`、`vercel-composition-patterns` 共同生效; 冲突时以本文件 + Skill 为准。

适用范围:

```txt
web/console/
  当前栈: React 19 + Vite + TypeScript + Tailwind v4
  基组件库: shadcn/ui (base = base-ui, style = base-nova)
  数据层: TanStack Query
  图表: ECharts / React Flow / Monaco Editor / Recharts (shadcn Chart)
```

---

## 1. 核心优先级

```text
shadcn/ui 组件  >  既有领域组件  >  Tailwind 语义类  >  原生标签
```

任何 UI 需求都按下面的顺序决策:

```text
1. shadcn/ui 是否有现成组件?
2. 项目内 src/components/console/ 是否有领域封装?
3. Tailwind 语义类 + 项目基础变量是否够用?
4. 只有以上都不满足, 才允许写自定义组件, 且必须先与用户确认。
```

**永远不要直接在页面里写内联样式或硬编码色值代替组件职责。**

---

## 2. shadcn/ui 使用流程 (强制)

### 2.1 三步决策

每次新增 UI 元素前必须执行:

```text
step 1: 查 src/components/ui/ 已安装组件
step 2: 不在则执行 pnpm dlx shadcn@latest search <关键词> 查看注册表
step 3: 需要新装则执行 pnpm dlx shadcn@latest add <组件>
```

默认包管理器是 **pnpm** (与 `pnpm-lock.yaml` 一致)。 命令格式:

```bash
# 1. 查看已安装组件
ls web/console/src/components/ui

# 2. 在 @shadcn 注册表搜索 (跨 registry)
pnpm dlx shadcn@latest search @shadcn -q "sidebar"

# 3. 安装
pnpm dlx shadcn@latest add button dialog select

# 4. 安装前预览 (更新已安装组件时强烈推荐)
pnpm dlx shadcn@latest add <name> --dry-run
pnpm dlx shadcn@latest add <name> --diff <file>
```

### 2.2 安装提示规则 (强制)

```text
shadcn 注册表存在组件       -> 直接安装, 不要询问
shadcn 官方没有, 社区注册表存在 -> 提示用户从下列选项中决策:
                                 a) 安装社区注册表组件 (告知来源 registry)
                                 b) 基于现有 shadcn 原语手工封装
                                 c) 暂时跳过, 留待后续 Phase 处理
shadcn 和社区都没有           -> 提示用户决策:
                                 a) 退回到 Tailwind 语义 + 现有组件手工组装
                                 b) 新建项目内部组件 (必须放对位置, 见 §6)
                                 c) 引入第三方 UI 库 (需通过架构评审)
```

**永远不要默默自己装未在 Skill 中列出的第三方依赖。** 任何 `package.json` 改动都要先征得同意。

### 2.3 安装后必做校验

执行 `add` 后必须:

```text
1. 读取新加入的 src/components/ui/<name>.tsx
2. 检查 import 路径是否匹配 components.json aliases (@/components/ui/@/lib/@/hooks)
3. 检查 icon 库是否为 lucide-react (项目 iconLibrary = "lucide")
4. 确认是否使用了本项目禁用写法 (§4 / §5)
5. 若使用了第三方 registry 的组件且包含特殊 import, 用 cn 项目自身别名重写
```

---

## 3. base-ui vs radix (项目关键差异)

`components.json` 中 `base` = **base**, 不是 radix。 这影响所有需要 `asChild` 的场景。

| 场景             | radix 写法                | base-ui 写法 (本项目)           |
| ---------------- | ------------------------- | ------------------------------- |
| 自定义触发元素   | `<DialogTrigger asChild>` | `<DialogTrigger render={<a/>}>` |
| Button 透传      | `asChild={true}`          | `render={<a/>}`                 |
| Polymorphic 渲染 | `Slot`                    | `render` prop                   |

判断当前 base 的命令:

```bash
pnpm dlx shadcn@latest info
```

**禁止把 radix 写法抄到本项目。** 复制外部 shadcn 示例代码时务必先确认 base。

---

## 4. 样式与主题系统

### 4.1 唯一的 CSS 入口

所有全局样式只写在:

```text
web/console/src/styles.css
```

不要新建第二个全局 CSS 文件, 不要在组件文件里写 `@layer base` 或 `:root { --... }`。 需要新增变量请追加到 `styles.css` 的 `@theme inline` 与 `:root` 块。

### 4.2 语义色 token

`@theme inline` 已暴露的语义色, 必须使用这些, 严禁写 `bg-blue-500`、`text-red-600` 等原色:

```text
--color-background / --color-foreground
--color-card / --color-card-foreground
--color-popover / --color-popover-foreground
--color-primary / --color-primary-foreground
--color-secondary / --color-secondary-foreground
--color-muted / --color-muted-foreground
--color-destructive / --color-destructive-foreground
--color-border / --color-input / --color-ring
```

使用方式:

```tsx
// 正确
<div className="bg-background text-foreground" />
<div className="border-border bg-card text-card-foreground" />
<div className="text-muted-foreground" />

// 错误
<div className="bg-white text-black dark:bg-zinc-900" />
<div className="bg-blue-500 text-white" />
```

仅以下三类场景允许使用 `gray-*` / `blue-*` 等 Tailwind 原色:

```text
1. shadcn 安装产生的组件源码内 (不要二次修改, 除非有具体设计理由)
2. StatusBadge / SeverityBadge 等基于 cva 的语义化组件内 (已封装好, 调用方不要再覆盖)
3. 已经被 styles.css 中 @theme inline 显式定义为项目色阶的扩展 (--color-blue-500 等仅限此处)
```

### 4.3 暗色模式约定

项目使用三态主题: `system` / `light` / `dark`, 由 `src/lib/theme/` 维护。 严禁:

```text
手动写 dark: 前缀的色值 (dark:bg-zinc-900)
在 useEffect 里直接操作 document.documentElement.classList (应走 applyTheme)
在 localStorage 用裸 key (必须用 THEME_STORAGE_KEY = 'aegisops.theme')
新写一套主题切换 hook (统一用 useTheme)
```

`useTheme` 读取后再调用 `setTheme`。 SSR-safe 由 `ThemeInit` 保障, 渲染层不用关心。

### 4.4 间距 / 尺寸 / 文本截断

```tsx
// 正确
<div className="flex flex-col gap-4" />
<div className="flex gap-2" />
<Avatar className="size-10" />
<span className="truncate" />

// 错误
<div className="space-y-4" />             // space-* 禁用
<div className="space-x-2" />
<Avatar className="h-10 w-10" />           // size-* 优先
<span className="overflow-hidden text-ellipsis whitespace-nowrap" />  // 用 truncate
```

### 4.5 玻璃拟态 (谨慎使用)

`styles.css` 已经提供:

```text
.glass           弱背景模糊
.glass-card      强模糊 + 阴影
.glass-card-active 选中态
.glass-header    顶部导航弱模糊
```

使用限制:

```text
只用于: 顶部导航、侧边栏、悬浮 Card、Active 状态
禁止用于: 主内容容器、长列表、表格、密集数据 (可读性差)
禁止重复定义新的 .glass-* 类, 如需新风格先与用户决策
```

### 4.6 圆角 / 字号 / 动效

`@theme inline` 暴露的项目级变量, 必须使用:

```text
radius: --radius-sm / --radius-md / --radius-lg / --radius-xl
font:   --font-heading (Geist Variable, 标题与品牌区)
animation: --animate-fade-in / slide-up / slide-down / slide-in-right / pulse-slow / accordion-*
```

禁止在组件中直接写 `rounded-[10px]`、`text-[13px]` 这类魔术值, 如需新增请先在 `styles.css` 中定义语义 token。

### 4.7 focus 可见性

全局 `:focus-visible` 已经统一了 `outline-width: 2px` 与 `outline-color: var(--color-blue-500)`。 **不要在组件里覆盖 focus ring**, 除非该组件需要完全自定义的可见状态 (例如危险操作按钮), 且要有明确的视觉理由。

---

## 5. 组件使用规范

### 5.1 内置组件速查

| 需求                | 必须使用                                                             | 禁止                                               |
| ------------------- | -------------------------------------------------------------------- | -------------------------------------------------- |
| 按钮                | `<Button variant="..." size="...">`                                  | `<button className="bg-...">`                      |
| 表单输入            | `<Field> + <FieldLabel> + <Input>`                                   | 裸 `<div>` + `<label>` + `<input>`                 |
| 表单校验            | `<Field data-invalid>` + `aria-invalid`                              | 手写红色边框与提示文案                             |
| 2~7 选              | `<ToggleGroup>`                                                      | 循环 `<Button>` + 自己维护 active                  |
| 多个相关复选框      | `<FieldSet> + <FieldLegend>`                                         | `<div>` + `<h3>`                                   |
| 输入框内嵌图标/按钮 | `<InputGroup> + <InputGroupAddon>`                                   | 手写 `<div className="relative">`                  |
| 调用提示            | `<Alert>`                                                            | 自制带颜色的 div                                   |
| 空状态              | `<Empty>`                                                            | 自制 SVG + 文案                                    |
| Toast               | `toast()` from sonner                                                | alert() / 自制悬浮层                               |
| 分割线              | `<Separator>`                                                        | `<hr>` 或 `<div className="border-t">`             |
| 加载占位            | `<Skeleton>`                                                         | `<div className="animate-pulse ...">`              |
| 徽标                | `<Badge variant="...">`                                              | 自制 `<span className="bg-...">`                   |
| 弹窗标题            | 必须有 `<DialogTitle>` 等                                            | 仅放内容, 漏掉 Title                               |
| 模态确认            | `<AlertDialog>`                                                      | 普通 `<Dialog>`                                    |
| 数据更新按钮        | `<Button disabled>` + `<Spinner data-icon>`                          | `<Button isLoading>` (本项目 Button 不存在该 prop) |
| AI 对话流容器       | shadcn `MessageScroller`                                             | 自造滚动容器 / `overflow-y-auto` div               |
| AI 消息气泡         | shadcn `Message` + `Bubble`                                          | 自造 `<div className="rounded p-3">`               |
| 工具调用展示        | AI Elements `Tool`                                                   | 手写 JSON <pre>                                    |
| AI 审批卡片         | AI Elements `Confirmation`                                           | 普通 `Dialog` / `<button onClick={approve}>`       |
| AI 思考链           | AI Elements `Reasoning` 或 prompt-kit `Reasoning` / `ChainOfThought` | 手写折叠面板                                       |
| 流式终端            | AI Elements `Terminal`                                               | 自己接 xterm.js                                    |
| 文件树              | AI Elements `FileTree`                                               | 手写嵌套 `<ul>`                                    |

AI 工作台完整组件栈与命令见 `references/ai-agent-frontend-stack.md`。

### 5.2 Form 写法示例

```tsx
// 正确
<FieldGroup>
  <Field>
    <FieldLabel htmlFor="email">邮箱</FieldLabel>
    <Input id="email" type="email" placeholder="name@example.com" />
    <FieldDescription>用于告警通知送达</FieldDescription>
  </Field>
  <Field data-invalid={!!errors.password}>
    <FieldLabel htmlFor="password">密码</FieldLabel>
    <Input id="password" type="password" aria-invalid={!!errors.password} />
    <FieldError>{errors.password?.message}</FieldError>
  </Field>
</FieldGroup>

// 错误
<div className="space-y-4">
  <label>邮箱</label>
  <input className="border rounded px-2 py-1" />
</div>
```

### 5.3 Button + Icon 规范

```tsx
// 正确: lucide-react, data-icon, 不写 size-*
import { SearchIcon } from 'lucide-react'

<Button>
  <SearchIcon data-icon="inline-start" />
  查询
</Button>

// 错误: 直接写 className size, 字符串图标名
<button className="px-4 py-2 bg-primary text-primary-foreground rounded-md">
  <SearchIcon className="size-4" />
  查询
</button>
```

### 5.4 加载态组合

```tsx
<Button disabled={isPending}>
  {isPending && <Spinner data-icon="inline-start" />}
  提交
</Button>
```

不要尝试给 Button 加不存在的 `isLoading` / `isPending` prop, 本项目 Button 基于 base-ui, 仅暴露原生 props。

### 5.5 空状态 / 错误态

```tsx
// 列表为空
<Empty>
  <EmptyMedia>
    <InboxIcon />
  </EmptyMedia>
  <EmptyTitle>暂无告警</EmptyTitle>
  <EmptyDescription>对接数据源后, 这里会展示命中规则的事件</EmptyDescription>
  <EmptyAction>
    <Button variant="outline">配置数据源</Button>
  </EmptyAction>
</Empty>

// 错误
<Alert variant="destructive">
  <AlertTitle>同步失败</AlertTitle>
  <AlertDescription>{error.message}</AlertDescription>
</Alert>
```

### 5.6 卡片组合

```tsx
<Card>
  <CardHeader>
    <CardTitle>活跃告警</CardTitle>
    <CardDescription>最近 24 小时</CardDescription>
  </CardHeader>
  <CardContent>{/* 内容 */}</CardContent>
  <CardFooter className="justify-end gap-2">
    <Button variant="ghost">忽略</Button>
    <Button>查看详情</Button>
  </CardFooter>
</Card>
```

不要把内容直接塞进 `<Card>` 不带 `CardHeader` / `CardContent` 结构。

### 5.7 Tabs 用法

```tsx
<Tabs defaultValue="overview">
  <TabsList>
    <TabsTrigger value="overview">概览</TabsTrigger>
    <TabsTrigger value="timeline">时间线</TabsTrigger>
  </TabsList>
  <TabsContent value="overview">{/* ... */}</TabsContent>
  <TabsContent value="timeline">{/* ... */}</TabsContent>
</Tabs>
```

`TabsTrigger` 必须放在 `TabsList` 内。

---

## 6. 组件目录与命名

### 6.1 目录约定

```text
web/console/src/
├── components/
│   ├── ui/             shadcn 安装产物, 只读, 除非 shadcn update 否则不要手工修改
│   └── console/        项目内部领域组件, 比如 StatusBadge / MarkdownPreview
├── hooks/              通用 hooks, 例如 usePhase1Queries
├── layout/             整页布局, AppLayout
├── pages/              路由级页面, 一个文件一个页面
├── api/                API 客户端
│   └── client.ts       仅保留 client.ts 单文件 (Phase 0), 后续按领域拆分时见 §8
├── lib/
│   ├── theme/          主题基础设施, 不放业务
│   └── utils.ts        cn / 通用工具
├── auth/               鉴权相关
└── styles.css          全局样式唯一入口
```

`src/components/ui/` 视为 shadcn 仓库视图, 任何修改都应该走 `pnpm dlx shadcn@latest add --diff`。

### 6.2 命名与文件

```text
目录:   小写 / 单词短横线 (auth, layout, lib, hooks)
组件文件: PascalCase (StatusBadge.tsx), 测试 同名 .test.tsx
Hook 文件: camelCase (useTheme.ts 或 useTheme.tsx)
领域组件放 components/console/, 命名清晰表达领域 (incident-detail-card, status-badge)
```

### 6.3 领域组件封装原则

满足以下任一条件时, 强制封装领域组件 (`src/components/console/`):

```text
1. 在 2+ 个页面出现相同的多组件组合 (Card + Header + Body + Action)
2. 含业务映射表 (例如 STATUS_TONE_MAP), 不应散落在页面里
3. 含复杂 prop (如 incident 详情卡片需要 5+ 数据字段), 避免 prop drilling
4. 带可访问性 / 状态机逻辑 (例如 ApprovalDialog)
```

封装的领域组件命名要表达业务意图, 不重复 ui/ 命名空间:

```text
ui/button.tsx        -> Button  (通用)
console/incident-detail-card.tsx -> IncidentDetailCard (领域)
```

### 6.4 通用组件 vs 领域组件

| 维度     | `components/ui/`                  | `components/console/`                |
| -------- | --------------------------------- | ------------------------------------ |
| 职责     | 通用 UI 原语                      | 领域语义                             |
| 数据     | 仅 props, 不直接取 API            | 可消费 hooks / API 数据              |
| 颜色     | 通过 variant 切换, 自身无业务色   | 包含业务色映射 (status, severity 等) |
| 复用性   | 跨项目可移植                      | 业务内复用                           |
| 修改方式 | shadcn update                     | 自由编辑                             |
| 例子     | Button, Card, Dialog, Tabs, Empty | StatusBadge, IncidentDetailCard      |

---

## 7. 状态管理与组件组合

遵循 `vercel-composition-patterns` (组件组合优于 boolean prop) 与 `vercel-react-best-practices` (RSC/重渲染优化)。

### 7.1 必须避免的写法

```text
禁止用 boolean prop 表达模式: <Button isThread isEditing isForwarding>
禁止在 render props 里堆回调: <Dialog renderHeader={...} renderFooter={...}>
禁止在 render 内嵌函数式组件: function Parent() { function Inner() {...}; return <Inner /> }
禁止把可派生状态放入 useEffect / useState: fullName = useMemo + setState
禁止把异步 flag 与本地条件同时 await 而本地条件先判: 必须先 if (local) 再 await
```

### 7.2 推荐结构

```text
- 字段可由现有 props/state 算出 -> 直接在 render 派生
- 多个子组件需要共享状态 -> 提升到 Provider + Context
- 复杂列表 -> 用 Extracted Memoized Component, 配合 useDeferredValue
- 客户端数据 -> TanStack Query, 严禁把 fetch 塞进 useEffect
- 服务端实时流 -> EventSource / SSE, 走统一 lib/sse.ts 工具 (Phase 0 后引入)
```

### 7.3 服务器状态约定

```text
Query key: ['领域', '动作', ...入参], 例如 ['incidents', 'list', { status }]
Mutation: invalidate 相关 keys, 不要手动 refetch
轮询 / 实时: SSE 优先, WebSocket 兜底
```

### 7.4 避免无意义 useMemo

```tsx
// 错误
const isLoading = useMemo(
  () => user.loading || notif.loading,
  [user.loading, notif.loading],
);

// 正确
const isLoading = user.loading || notif.loading;
```

只对复杂计算或对象引用稳定化使用 useMemo。

### 7.5 事件处理与 Effect

```text
- 用户点击触发 -> onClick 里直接处理, 不要再绕 state + useEffect
- 必须订阅全局事件 (window scroll, keyboard) -> 把 handler 放 useEffectEvent / ref, 避免重复订阅
- 在 onClick 内调用 useState 需要用函数式更新: setX(prev => ...)
```

---

## 8. API 客户端与类型

当前结构:

```text
web/console/src/api/
  client.ts     单文件, TanStack Query hooks + fetch 工具
hooks/
  usePhase1Queries.ts  按阶段聚合的 hooks (暂存)
```

约束:

```text
- 所有 fetch 必须有 TypeScript 入参与出参, 禁止 any
- 失败抛出统一 Error, 含 status / message / endpoint
- 不同领域 (incident, alert, datasource) 后续按需拆分为 client/incident.ts 等, 但本规则不强制 Phase 0 立刻拆分
- 严禁页面层直接 fetch, 统一从 api/client 或 hooks 暴露
- 与后端契约变更必须同步更新 docs/api/
```

---

## 9. 页面与路由

### 9.1 页面文件

```text
web/console/src/pages/
  DashboardPage.tsx          入口
  LoginPage.tsx              鉴权
  IncidentsPage.tsx          列表
  IncidentDetailPage.tsx     详情
  DatasourcesPage.tsx
  AlertsPage.tsx
  EvidencePage.tsx
  AiDiagnosisPage.tsx
  ReportsPage.tsx
  NotFoundPage.tsx
  audit/, modules/, platform/ 按子域组织
```

每个页面文件默认导出 React 组件 (PascalCase), 内部可拆分子文件但不强制。

### 9.2 页面骨架建议

```text
1. 调用 TanStack Query 取数
2. 顶部: PageHeader (h1 + actions), 用 Card / 业务组件
3. 主体: Tabs / Table / Empty / Skeleton
4. 旁路: Sheet (详情侧拉) / Dialog (表单)
5. 加载态: Skeleton, 不用自造 spinner div
6. 空态: Empty + 引导操作
7. 错误态: Alert variant="destructive" + 重试按钮
```

### 9.3 路由懒加载

MVP 阶段可全部同步导入。 Phase 后期按需引入 `React.lazy`:

```tsx
const AiDiagnosisPage = React.lazy(() => import("@/pages/AiDiagnosisPage"));
```

---

## 10. 测试与质量门槛

提交前在 `web/console/` 必须运行:

```bash
pnpm run lint
pnpm run typecheck
pnpm run test
pnpm run build
```

测试要求:

```text
领域组件 (components/console/) 必须配套 *.test.tsx
工具函数 (lib/utils, lib/theme) 必须配套 *.test.ts
API hooks 必须有 mock + 失败路径测试
critical page (Login / Dashboard / Incident Detail / Approval) 至少 1 个 smoke 测试
```

Playwright E2E 当前不强制, Phase 2 引入 Incident Detail 后追加。

---

## 11. 反模式黑名单 (Code Review 必查)

```text
✗ <button className="bg-blue-500 ...">          改用 <Button variant="default">
✗ <div className="animate-pulse bg-muted" />     改用 <Skeleton>
✗ <div className="border-t" />                   改用 <Separator>
✗ <span className="bg-green-100 text-green-700"> 改用 <Badge variant="success">
✗ <div className="space-y-4">                    改用 <div className="flex flex-col gap-4">
✗ <Avatar className="h-10 w-10">                 改用 <Avatar className="size-10">
✗ <div onClick={...}>                            改用 <button> 或 Button
✗ <a onClick={...}>                              改用 <Link> 或 NavLink
✗ 页面里 fetch().then(setState)                  改用 TanStack Query
✗ 任何 cn 项目无关的 css 变量在组件内 :root 重定义     改写到 styles.css
✗ 在 dark 模式下用 dark:bg-xxx 替代语义色           必须用 bg-background 等
```

---

## 12. 跨文档协作

- 后端 API 契约变更: 同步更新 `docs/api/` 与 `web/console/src/api/`
- 数据模型变更: 先 Flyway migration (SKILL.md §17), 再前端类型同步
- 主题或色板新增: 必须在本文件 + `web/console/src/styles.css` 双更新, 并在 PR 描述里说明
- shadcn 升级: 用 `pnpm dlx shadcn@latest add <name> --diff`, 不可 `--overwrite`
- AI 组件新增: 必须读 `references/ai-agent-frontend-stack.md` 后再 `add`, PR 必须说明 registry 来源
- 新增前端模块依赖: 按 §2.2 决策矩阵, 默默改 `package.json` 一律视为评审 fail

## 13. AI 工作台页面骨架 (与 SKILL §4.1 联动)

Incident 详情页必须三栏布局, 组件映射:

```text
PageHeader: <Incident 标题> <StatusBadge> <SeverityBadge> 顶部 actions
顶部 Tabs: 概览 / 时间线 / AI 诊断 / Runbook / 自动化日志 / 复盘
左侧:    事件信息 / 关联资产 / 关联告警 / 状态机 Stepper / 影响范围
中间:    Conversation + MessageScroller + Message + Reasoning + Tool + Confirmation
右侧:    证据链 Tabs (指标 / 日志 / 变更 / RCA)
```

详见 `references/ai-agent-frontend-stack.md` §2 §3。

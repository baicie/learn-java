---
name: portal
description: 在 web/portal (shadcn-admin 模板) 二次开发时使用本 Skill。它指导 Agent 按 pages/api/auth/components/hooks/lib 分层接入 AegisOps / FaultLens 能力，遵守 Radix / Tailwind v4 / TanStack Router / TanStack Query / Zustand 的现有约定。适用于新增页面、表格、表单、Provider、Store、测试与依赖增删。
---

# web/portal Skill (shadcn-admin 二次开发)

`web/portal` 是从 shadcn-admin (Vite + React 19 + TanStack Router + TanStack Query + Radix UI + Tailwind v4 + shadcn/ui + Zustand + Clerk) 派生出来的**后台壳子**。它与 `web/console` 是两个独立应用，不要混用约定。

## 0. 项目定位

```txt
web/portal = shadcn-admin 模板血统的运营 / 控制台应用
  - 主要技术栈由模板决定, 不与 web/console 共享主题变量与代码
  - 保留 Clerk 作为登录壳, 但 AegisOps 真正的权限是后端 Spring Security
  - AegisOps 真实业务代码按 pages/api/auth/components/hooks/lib 职责归位
  - `src/features` 已由 ADR 0007 删除, CI 禁止重新创建或引用
```

冲突优先级（覆盖本 Skill 时）:

```txt
1. .agents/skills/portal/SKILL.md (本文件)
2. references/portal-frontend-conventions.md
3. .agents/skills/aegisops/SKILL.md (后端事实)
4. .agents/skills/vercel-react-best-practices/AGENTS.md（可选参考）
5. web/portal/components.json
6. web/portal/eslint.config.js
7. AGENTS.md (只用于入口与执行提醒)
```

## 1. 技术栈与版本锁定

来源：`web/portal/package.json`。

| 库                                  | 版本（节选） | 角色                                                      |
| ----------------------------------- | ------------ | --------------------------------------------------------- |
| react / react-dom                   | ^19.2.7      | UI 框架                                                   |
| vite                                | ^8.1.3       | 构建工具                                                  |
| typescript                          | ~6.0.3       | TS                                                        |
| tailwindcss                         | ^4.3.2       | 样式                                                      |
| @tailwindcss/vite                   | ^4.3.2       | Vite 插件                                                 |
| @tanstack/react-router              | ^1.170.17    | 文件路由 + URL search 状态                                |
| @tanstack/router-plugin             | ^1.168.19    | Vite 路由代码生成                                         |
| @tanstack/react-query               | ^5.101.2     | 服务端状态                                                |
| @tanstack/react-table               | ^8.21.3      | 表格                                                      |
| @radix-ui/react-*                   | ^1.x         | UI 原语（**注意：portal 用 Radix，不是 base-ui**）        |
| shadcn (radix 风格)                 | new-york     | 30 个已装组件                                             |
| @clerk/react                        | ^6.11.4      | 登录壳                                                    |
| zustand                             | ^5.0.14      | 客户端 Store                                              |
| zod                                 | ^4.4.3       | 表单 / search schema 校验                                 |
| react-hook-form                     | ^7.81.0      | 表单                                                      |
| @hookform/resolvers                 | ^5.4.0       | zod ↔ rhf                                                 |
| axios                               | ^1.18.1      | HTTP                                                      |
| sonner                              | ^2.0.7       | Toast                                                     |
| lucide-react                        | ^1.23.0      | 图标（**注意：portal lucide 与 console 不是同一大版本**） |
| date-fns                            | ^4.4.0       | 日期                                                      |
| cmdk                                | 1.1.1        | Command 面板                                              |
| recharts                            | ^3.9.2       | 图表（如果新增图表先看 dashboard/analytics-chart）        |
| tw-animate-css                      | ^1.4.0       | Tailwind 动画                                             |
| input-otp                           | ^1.4.2       | OTP 输入                                                  |
| react-day-picker                    | 10.0.1       | 日期选择                                                  |
| class-variance-authority            | ^0.7.1       | 变体                                                      |
| clsx / tailwind-merge               | —            | `cn()` 工具                                               |
| @faker-js/faker                     | ^10.5.0      | 模板示例数据                                              |
| knip                                | ^6.25.0      | 未使用代码检测                                            |
| vitest / @vitest/browser-playwright | ^4.1.10      | 测试                                                      |

**禁止的写法**:

```text
- 升级 Radix → base-ui（与 console 路线相反）
- 替换 Clerk 为自写登录（除非已与 AegisOps 后端登录打通, 且通过 ADR）
- 用 tanstack/react-router 之外的路由库（next.js 等）
- 用 SWR 替代 TanStack Query
- 引入 console 的 @/components/ui 别名引用方式（portal 自己的 components.json 已定义）
- 用 Tailwind 原色 (bg-blue-500) 替代语义色 token, 除非 portal 已存在的语义变体不够用
- 新增 `src/features` 或引用 `@/features/*`
- 引入 langchain-ui / assistant-ui / prompt-kit（portal 是后台壳, 不直接拼 AI Chat）
```

## 2. 目录结构（事实快照）

```text
web/portal/src/
├── main.tsx                        # QueryClient + Router 入口, 处理 401/500
├── routeTree.gen.ts                # 自动生成, 不要手改
├── styles/
│   ├── index.css                   # Tailwind v4 + 全局 @layer base
│   └── theme.css                   # 主题 token (--background, --primary ...)
├── components/
│   ├── ui/                         # shadcn 安装产物, 30 个, 禁手改
│   ├── layout/                     # AuthenticatedLayout + AppSidebar + Header + Main + Nav*
│   │   └── data/sidebar-data.ts    # SidebarData, 模板示例数据
│   ├── data-table/                 # 表格原语: toolbar / pagination / column-header / faceted-filter / bulk-actions / view-options
│   ├── command-menu.tsx
│   ├── confirm-dialog.tsx
│   ├── date-picker.tsx
│   ├── long-text.tsx
│   ├── search.tsx
│   ├── theme-switch.tsx
│   ├── sign-out-dialog.tsx
│   └── skip-to-main.tsx
├── api/                            # 按资源组织的带类型 HTTP 客户端
├── auth/                           # 登录、授权缓存、PermissionGate、路由守卫
├── pages/                          # 路由页面, 按业务资源分目录
├── components/                     # shadcn 原语、布局与可复用业务组件
├── hooks/                          # 跨组件查询与交互 Hook
├── lib/                            # schema、纯函数与通用基础设施
├── routes/                         # TanStack 文件路由
│   ├── __root.tsx
│   ├── (auth)/                     # 未登录分组
│   ├── (errors)/                   # 401/403/404/500/503
│   ├── clerk/                      # Clerk 模板路由
│   └── _authenticated/             # 已登录分组, 内含 users / tasks / chats / settings / errors / apps / help-center
├── hooks/
│   ├── use-dialog-state.tsx        # 通用 dialog open hook
│   ├── use-mobile.tsx              # 768 断点
│   └── use-table-url-state.ts      # 把表格状态同步到 URL search
├── context/
│   ├── theme-provider.tsx
│   ├── layout-provider.tsx
│   ├── search-provider.tsx
│   ├── direction-provider.tsx
│   └── font-provider.tsx
├── stores/
│   └── auth-store.ts               # Zustand, 真实接入 AegisOps 后端后改写
├── lib/
│   ├── utils.ts                    # cn / sleep / getPageNumbers / getDisplayNameInitials
│   ├── cookies.ts
│   ├── handle-server-error.ts
│   └── show-submitted-data.tsx
├── config/
│   └── fonts.ts
├── test-utils/
│   ├── cookies.ts                  # clearCookies()
│   └── tanstack-table.ts
├── assets/                         # 图片 / logo
└── tanstack-table.d.ts
```

新增业务能力按职责放置:

```text
src/pages/<resource>/               # 页面拼装与路由级状态
src/api/<resource>/                 # HTTP 请求与响应 zod schema
src/components/<resource>/          # 可复用业务组件
src/hooks/<resource>/               # TanStack Query/Mutation Hook
src/lib/<resource>/                 # 纯类型、schema 与转换
```

新增路由（仿 `routes/_authenticated/users/index.tsx`）:

```text
routes/_authenticated/<feature>/index.tsx
  - zod schema 定义 URL search (page/pageSize/facets/column filters)
  - validateSearch: <feature>SearchSchema
  - component: 引入 @/pages/<resource> 页面
```

## 3. 路由约定

```text
- 路由由 vite-plugin 自动生成 routeTree.gen.ts, 永远不要手改
- _authenticated 之下必须用 AuthenticatedLayout, 默认顶部 Header + Sidebar 已就绪
- 未登录页放 (auth) 分组, 错误页放 (errors) 分组
- URL search 状态用 zod schema 校验, 见 routes/_authenticated/users/index.tsx
- 表头列筛选 / 分页 / 全局过滤都用 useTableUrlState, 不允许 useState 管这些字段
- 跳页 / 跳路由必须用 router.navigate, 不要 a 标签直接拼字符串
- autoCodeSplitting 已开启, 路由级 lazy 不需要手动 React.lazy
```

## 4. 状态管理分层

```text
1. URL search (zod schema)                -> 分页/筛选/排序
2. TanStack Query                         -> 服务端数据缓存
3. Zustand store                          -> 跨页面持久状态 (auth / sidebar prefs)
4. React Context (xxx-provider)           -> 单 feature 内部跨组件共享 (open dialog / currentRow)
5. useState                               -> 组件本地 UI 状态 (rowSelection / 临时输入)
6. cookie                                -> sidebar_state / theme / layout_collapsible / layout_variant

禁止:
- 用 Redux / Recoil / MobX
- 把服务端数据塞进 Zustand (违反 TanStack Query 单一数据源)
- 在 Provider 里直接调 axios, 必须走 Query/Mutation
```

## 5. 表格约定（核心约定，复用率最高）

模板自带的 `components/data-table/` 已封装:

```text
DataTableToolbar          顶部搜索 + facets
DataTablePagination       底部分页
DataTableColumnHeader     可排序列头
DataTableBulkActions      多选批量操作
faceted-filter            复选 facet
view-options              列显隐切换
```

每个 feature 的 table 文件必须满足:

```text
1. 用 useReactTable + getCoreRowModel + getFilteredRowModel + getSortedRowModel + getFacetedRowModel
2. pagination / columnFilters 走 useTableUrlState, 不要 useState
3. sorting / rowSelection / columnVisibility 用 useState (本地 UI)
4. ensurePageInRange 在 useEffect 里调用, 防止 filter 后 page 越界
5. 列定义文件 <feature>-columns.tsx, 不写在 table.tsx 里
6. cell 渲染不允许写 inline JSON, 必须抽出 <feature>-columns 里的函数
7. meta.className / meta.thClassName / meta.tdClassName 用于样式覆盖, 不要直接 className 覆盖列
8. 所有过滤列必须 enableSorting / enableHiding / filterFn 显式声明
```

禁止的反模式:

```text
- 把 500 行 table.tsx 写在一个文件里
- useState({ page: 1, pageSize: 10 }) 而不用 useTableUrlState
- 在 cell 里直接做 fetch
- 在 <Table> 上手动写 className="bg-white" 覆盖主题
```

## 6. 样式与主题

`styles/index.css` 与 `styles/theme.css` 是唯一允许的全局样式入口。

```text
- 颜色: 必须用 --background / --foreground / --primary / --muted / --destructive / --border / --ring 等语义 token
- 暗色模式: 不写 dark:bg-xxx, 用语义色自动适配
- 间距: gap-2 / gap-4, 禁用 space-x-* / space-y-*
- 圆角: 项目用 --radius, 不要 rounded-[10px] 这类魔术值
- 动画: 用 tw-animate-css 提供的类, 不要再手写 @keyframes
- 焦点: 不要在组件里覆盖 focus ring, 全局已统一
- 滚动条: 全局 @apply 已统一, 不要在组件里覆盖 scrollbar
```

`cn()` 是 `clsx + tailwind-merge`, 任何条件 className 必须用它, 不要写模板字符串三元。

```tsx
// 正确
<div className={cn('flex flex-col gap-2', isActive && 'bg-muted')} />

// 错误
<div className={`flex flex-col gap-2 ${isActive ? 'bg-muted' : ''}`} />
```

## 7. 组件使用速查

```text
Button    variant="default | outline | ghost | destructive | secondary | link"
Input/Textarea:  必须 <Field> + <FieldLabel> + <FieldDescription> 包起来
Dialog/Drawer/Sheet:  必须有 Title + Description, 否则 a11y 不通过
Table:     自带 data-table/* 组件, 不要自己拼
Tabs:      TabsList + TabsTrigger + TabsContent
Toast:     import { toast } from 'sonner'
Empty:     用 <Empty> + <EmptyMedia> + <EmptyTitle> + <EmptyDescription> + <EmptyAction>
Loading:   <Skeleton />  或 配合 sonner
Tooltip:   Radix Tooltip, 必须有 Provider 包裹
Form:      react-hook-form + zodResolver
```

shadcn 安装流程（强制）:

```bash
# 1. 查已装
ls web/portal/src/components/ui

# 2. 看 components.json, base = Radix, style = new-york, iconLibrary = lucide
cat web/portal/components.json

# 3. 安装
pnpm dlx shadcn@latest add <name>

# 4. 升级用 --diff
pnpm dlx shadcn@latest add <name> --diff web/portal/src/components/ui/<name>.tsx

# 5. 安装后必须读 .tsx, 替换为本项目 @ 别名
# 6. 校验
pnpm run lint && pnpm run typecheck && pnpm run build
```

## 8. 错误处理

`main.tsx` 已统一接管 401/500/304，参考实现:

```text
- 401 -> toast.error('Session expired!') + authStore.reset() + 跳 /sign-in
- 500 -> toast.error('Internal Server Error!') + PROD 环境跳 /500
- 403 -> 暂不跳, 占位
- 304 -> toast.error('Content not modified!')
- handleServerError(error) -> AxiosError 统一处理
```

新 feature 加错误处理时:

```text
1. 服务端错误一律抛到 QueryCache.onError 或 mutation.onError
2. 不要在每个组件内 try/catch + toast
3. 业务校验错误由后端 4xx + message 表达, 前端直接 toast.error
4. 表单字段级错误走 react-hook-form setError, 不要 toast
```

## 9. 测试（vitest + playwright 浏览器）

`pnpm run test` 已在浏览器里跑 vitest（`@vitest/browser-playwright`）。规则:

```text
- 测试放与被测文件同目录: xxx.test.tsx
- 不要把 e2e 慢测试放进 `src/`; 使用 `web/portal/e2e/`
- Provider 必须用 renderHook + 包裹 <UsersProvider> 等真实 Provider
- cookie 必须 clearCookies() + vi.resetModules(), 见 auth-store.test.ts
- mock axios: 用 MSW 或 respx 都可以, 不要 fetch().catch
- 覆盖率 exclude: src/components/ui/, src/assets/, src/routeTree.gen.ts, src/test-utils/, src/routes/
- 写新 Provider 时配套写 .test.tsx (init / set / reset 三个 case)
```

## 10. AegisOps 业务接入约束

portal 已接入 AegisOps 登录、IAM、平台能力和工作记录。新增真实后端能力时:

```text
- HTTP 客户端放 `src/api/<resource>/`, 响应必须经 zod 校验
- API 客户端用 axios 实例, baseURL 走 import.meta.env.VITE_API_BASE_URL, 不要硬编码
- 鉴权 token 走 axios interceptor, 从 useAuthStore.auth.accessToken 取
- AegisOps 后端的 tenant / JWT 替换 Clerk 后, 移除 @clerk/react 依赖要单独 PR, 默认保留
- 流式接口用 EventSource, 不要在组件内手写 fetch
- 真实数据回包用 zod schema 校验, 见 `src/api/iam` 与 `src/api/work-records`
```

## 11. 提交与代码评审规则

```text
- 提交前必跑:
    pnpm run lint
    pnpm run typecheck
    pnpm run test
    pnpm run knip
    pnpm run build
- 修改 shadcn 组件必须用 --diff, 不允许 --overwrite
- 新增依赖必须写 ADR (docs/adr/) 引用本 Skill §1
- 路由文件改完后 commit routeTree.gen.ts, 否则他人本地 dev 会断
- Portal 与 console 的代码不互相 import, 任何共享类型放 packages/shared/ (待新建)
```

## 12. 决策记录

```text
2026-07-07: web/portal 独立 Skill, 不与 web/console 共享主题/约定
2026-07-07: Radix UI 路线 (与 console 的 base-ui 路线相反), 不要再讨论迁移
2026-07-07: AegisOps 后端接入后, Clerk 保留但实际鉴权走后端, 通过 ADR 决策是否下线 Clerk
2026-07-14: ADR 0007 删除 src/features, 改为 pages/api/auth/components/hooks/lib 分层
```

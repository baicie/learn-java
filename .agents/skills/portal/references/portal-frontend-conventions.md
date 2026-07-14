---
title: web/portal 样式与组件约定
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - .agents/skills/portal/SKILL.md
---

# web/portal 样式与组件约定

本文件是 SKILL 的实操补充。所有规则与 `web/portal/components.json`、`styles/index.css`、`styles/theme.css`、`eslint.config.js` 同步生效。

## 1. 主题与语义色 token

`styles/theme.css` 已暴露的语义变量（节选）:

```text
--background / --foreground
--card / --card-foreground
--popover / --popover-foreground
--primary / --primary-foreground
--secondary / --secondary-foreground
--muted / --muted-foreground
--accent / --accent-foreground
--destructive / --destructive-foreground
--border / --input / --ring
--sidebar / --sidebar-foreground / --sidebar-primary / --sidebar-accent ...
--chart-1 .. --chart-5
--radius / --radius-sm / --radius-md / --radius-lg / --radius-xl
```

规则:

```text
- 任何 className 颜色必须用语义 token, 禁 bg-blue-500 / text-red-600 / border-zinc-300
- 状态徽标 (active / inactive / invited / suspended) 使用业务 `lib/<resource>` 中的集中映射
  新增状态在资源 schema/映射中维护, 不在组件里散落硬编码
- 暗色模式: 永远不要写 dark:bg-xxx, 语义色会自动切换
- 焦点: 全局 :focus-visible 已统一, 不要在组件里覆盖 focus ring, 除非必须自定义
```

## 2. 间距 / 尺寸 / 排版

```tsx
// 正确
<div className="flex flex-col gap-4" />
<div className="flex items-center gap-2" />
<Avatar className="size-10" />
<span className="truncate" />

// 错误
<div className="space-y-4" />             // space-* 禁用
<Avatar className="h-10 w-10" />          // size-* 优先
<span className="overflow-hidden text-ellipsis whitespace-nowrap" />  // 用 truncate
```

容器查询:

```text
- AuthenticatedLayout 已用 @container/content, 内层用 @md/content / @4xl/content
- 列显隐切换经常用 @4xl/content:table-cell 这种基于容器宽度的响应式
```

## 3. Radix 写法（本项目 base）

本项目 `components.json` 的 `style = new-york`, 基础库是 **Radix**, 不是 base-ui。

```tsx
// 正确 (Radix 风格)
<DialogTrigger asChild>
  <Button>Open</Button>
</DialogTrigger>

// 错误 (base-ui 写法, 与本项目不符)
<DialogTrigger render={<Button>Open</Button>} />
```

引用 `@radix-ui/react-icons` 也允许（见 `components/data-table-row-actions.tsx`），但默认图标走 lucide-react。

## 4. Button + Icon 规范

```tsx
import { MailPlus, UserPlus } from 'lucide-react'

;<Button variant="outline" className="space-x-1" onClick={() => setOpen('invite')}>
  <span>Invite User</span> <MailPlus size={18} />
</Button>
```

要点:

```text
- 图标用 lucide-react, 默认 size={16} 或 size={18}
- 不要给 Icon 加 className="text-primary", 由父组件控制
- Button 不要写 type="button" 显式声明, 除非在 form 内必须避免默认 submit
```

## 5. 数据表头 / 分页 / 筛选

```tsx
const { columnFilters, onColumnFiltersChange, pagination, onPaginationChange, ensurePageInRange } =
  useTableUrlState({
    search,
    navigate,
    pagination: { defaultPage: 1, defaultPageSize: 10 },
    globalFilter: { enabled: false },
    columnFilters: [
      { columnId: 'username', searchKey: 'username', type: 'string' },
      { columnId: 'status', searchKey: 'status', type: 'array' },
      { columnId: 'role', searchKey: 'role', type: 'array' },
    ],
  })
```

URL search 必须配套 zod schema（路由文件）:

```tsx
// routes/_authenticated/users/index.tsx
const usersSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(10),
  status: z.array(z.union([...])).optional().catch([]),
  role: z.array(z.enum(roles.map(r => r.value))).optional().catch([]),
  username: z.string().optional().catch(''),
})

export const Route = createFileRoute('/_authenticated/users/')({
  validateSearch: usersSearchSchema,
  component: Users,
})
```

URL search 命名必须与 useTableUrlState.columnFilters 中的 searchKey 完全一致，否则 facet 不会同步。

## 6. Provider / Hook 模式

每个 feature 的 `<feature>-provider.tsx` 写法统一:

```tsx
import React, { useState } from 'react'
import useDialogState from '@/hooks/use-dialog-state'
import { type User } from '../data/schema'

type UsersDialogType = 'invite' | 'add' | 'edit' | 'delete'

type UsersContextType = {
  open: UsersDialogType | null
  setOpen: (str: UsersDialogType | null) => void
  currentRow: User | null
  setCurrentRow: React.Dispatch<React.SetStateAction<User | null>>
}

const UsersContext = React.createContext<UsersContextType | null>(null)

export function UsersProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useDialogState<UsersDialogType>(null)
  const [currentRow, setCurrentRow] = useState<User | null>(null)

  return (
    <UsersContext value={{ open, setOpen, currentRow, setCurrentRow }}>{children}</UsersContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useUsers = () => {
  const usersContext = React.useContext(UsersContext)
  if (!usersContext) {
    throw new Error('useUsers has to be used within <UsersContext>')
  }
  return usersContext
}
```

要点:

```text
- useDialogState 必须是 toggle 语义 (再点同一个, set 到 null)
- useUsers 抛错信息固定 "useX has to be used within <XContext>"
- eslint-disable-next-line react-refresh/only-export-components 是允许的, 但 useX hook 必须紧跟 Provider 之后
```

## 7. 错误页与状态页

```tsx
// 空态
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

// Toast
import { toast } from 'sonner'
toast.error('Session expired!')
```

## 8. 不允许的写法（黑名单）

```text
✗ <button className="bg-blue-500 ...">          改用 <Button variant="default">
✗ <div className="animate-pulse bg-muted" />     改用 <Skeleton>
✗ <div className="border-t" />                   改用 <Separator>
✗ <span className="bg-green-100 text-green-700"> 改用 <Badge variant="success">
✗ <div className="space-y-4">                    改用 <div className="flex flex-col gap-4">
✗ <Avatar className="h-10 w-10">                 改用 <Avatar className="size-10">
✗ <div onClick={...}>                            改用 <button> 或 <Button>
✗ useState({ page: 1, pageSize: 10 })            改用 useTableUrlState
✗ axios.defaults.headers.common['Auth'] = ...    改用 axios.interceptors.request
✗ 任何 cn 项目无关的 css 变量在组件内 :root 重定义  改写到 styles/theme.css
✗ 直接修改 src/components/ui/                    改用 shadcn add --diff
✗ 在 portal 引用 web/console 的任何模块           两个项目独立, 不共享
```

## 9. 与 AegisOps 后端的约定（占位）

接入 AegisOps 后端时, HTTP 客户端放在 `src/api/<resource>/`:

```text
// src/api/users.ts
import axios from 'axios'
import { z } from 'zod'
import { type User, userSchema } from './schema'

export async function listUsers(params: ListParams): Promise<User[]> {
  const { data } = await axios.get('/api/users', { params })
  return z.array(userSchema).parse(data)
}
```

```text
- 鉴权: axios interceptor 从 useAuthStore.auth.accessToken 取
- 错误: 不在 client.ts 里 toast, 抛到 QueryCache.onError
- 校验: 回包必须经 zod schema parse, parse 失败抛 z.ZodError
- baseURL: import.meta.env.VITE_API_BASE_URL, 走 .env
```

## 10. 参考

```text
- .agents/skills/portal/SKILL.md
- web/portal/components.json
- web/portal/styles/index.css + theme.css
- web/portal/eslint.config.js
- web/portal/src/components/data-table/*  (表格原语模板)
- web/portal/src/api/iam/*               (带类型 API 范例)
- web/portal/src/components/iam/*        (业务组件范例)
- web/portal/src/pages/work-records/*    (页面范例)
```

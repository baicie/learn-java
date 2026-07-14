---
title: Portal 清理、路由与导航完整代码
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Portal 清理、路由与导航完整代码

## 1. package.json 调整

删除：

```json
"@clerk/react": "^6.12.2"
```

删除开发依赖：

```json
"@faker-js/faker": "^10.5.0"
```

增加：

```json
"@dnd-kit/core": "^6.3.1",
"@dnd-kit/sortable": "^10.0.0",
"@dnd-kit/utilities": "^3.2.2"
```

修改包名：

```json
{
  "name": "aegisops-portal",
  "private": true
}
```

保留现有 Formily、TanStack Query/Router、Zustand、Shadcn/Radix 依赖。

## 2. 删除目录

```text
web/portal/src/features/users/data/users.ts
web/portal/src/features/users/components/users-dialogs.tsx
web/portal/src/features/users/components/users-provider.tsx
web/portal/src/features/users/components/users-primary-buttons.tsx
web/portal/src/features/users/components/users-table.tsx

所有 Clerk 专用页面与路由
所有 Tasks/Apps/Chats 演示页面
```

旧 `/users` 路由仅保留一个版本的重定向：

```tsx
import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/users/')({
  beforeLoad: () => {
    throw redirect({ to: '/platform/users' })
  },
})
```

## 3. 新导航模型

文件：

```text
web/portal/src/components/layout/navigation.ts
```

完整代码：

```ts
import {
  CalendarDays,
  ClipboardList,
  FileSpreadsheet,
  LayoutDashboard,
  Library,
  ListChecks,
  ScrollText,
  Settings2,
  ShieldCheck,
  Users,
} from 'lucide-react'

export type NavigationItem = {
  titleKey: string
  to?: string
  icon?: React.ComponentType<{ className?: string }>
  anyPermissions?: string[]
  children?: NavigationItem[]
}

export const navigation: NavigationItem[] = [
  {
    titleKey: 'nav.dashboard',
    to: '/',
    icon: LayoutDashboard,
  },
  {
    titleKey: 'nav.workRecords.group',
    icon: ClipboardList,
    children: [
      {
        titleKey: 'nav.workRecords.records',
        to: '/work-records',
        icon: ListChecks,
        anyPermissions: ['work-record:read:self', 'work-record:read:all'],
      },
      {
        titleKey: 'nav.workRecords.designer',
        to: '/work-records/designer',
        icon: Library,
        anyPermissions: ['work-record:template:read'],
      },
      {
        titleKey: 'nav.workRecords.tasks',
        to: '/work-records/tasks',
        icon: FileSpreadsheet,
        anyPermissions: ['work-record:import', 'work-record:export'],
      },
    ],
  },
  {
    titleKey: 'nav.platform.group',
    icon: Settings2,
    children: [
      {
        titleKey: 'nav.platform.users',
        to: '/platform/users',
        icon: Users,
        anyPermissions: ['platform:user:read'],
      },
      {
        titleKey: 'nav.platform.roles',
        to: '/platform/roles',
        icon: ShieldCheck,
        anyPermissions: ['platform:role:read'],
      },
      {
        titleKey: 'nav.platform.dictionaries',
        to: '/platform/dictionaries',
        icon: Library,
        anyPermissions: ['platform:dict:read'],
      },
      {
        titleKey: 'nav.platform.calendars',
        to: '/platform/calendars',
        icon: CalendarDays,
        anyPermissions: ['platform:calendar:read'],
      },
      {
        titleKey: 'nav.platform.audit',
        to: '/platform/audit-logs',
        icon: ScrollText,
        anyPermissions: ['platform:audit:read'],
      },
    ],
  },
]
```

权限过滤：

```ts
import type { NavigationItem } from './navigation'
import type { AuthorizationSnapshot } from '@/features/auth/authorization-api'

export function filterNavigation(
  items: NavigationItem[],
  authorization: AuthorizationSnapshot
): NavigationItem[] {
  const permissions = new Set(authorization.permissions)

  return items.flatMap((item) => {
    const allowed =
      !item.anyPermissions?.length ||
      item.anyPermissions.some((permission) => permissions.has(permission))

    const children = item.children ? filterNavigation(item.children, authorization) : undefined

    if (!allowed && !children?.length) return []

    return [{ ...item, children }]
  })
}
```

## 4. Route Guard

文件：

```text
web/portal/src/features/auth/require-permission.ts
```

```ts
import { redirect } from '@tanstack/react-router'
import { authStore } from '@/stores/auth-store'

export function requireAnyPermission(required: string[]) {
  const snapshot = authStore.getState().authorization

  if (!snapshot) {
    throw redirect({
      to: '/sign-in',
      search: { redirect: window.location.pathname },
    })
  }

  if (!required.some((code) => snapshot.permissions.includes(code))) {
    throw redirect({ to: '/403' })
  }

  return snapshot
}
```

用户路由：

```tsx
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/require-permission'
import { PlatformUsersPage } from '@/features/platform-users'

export const Route = createFileRoute('/_authenticated/platform/users/')({
  beforeLoad: () => requireAnyPermission(['platform:user:read']),
  validateSearch: platformUserSearchSchema,
  component: PlatformUsersPage,
})
```

角色路由同理。

## 5. PermissionGate

```tsx
import type { PropsWithChildren, ReactNode } from 'react'
import { useAuthorization } from '@/features/auth/use-authorization'

type Props = PropsWithChildren<{
  anyOf?: string[]
  allOf?: string[]
  fallback?: ReactNode
}>

export function PermissionGate({ anyOf = [], allOf = [], fallback = null, children }: Props) {
  const authorization = useAuthorization()
  const owned = new Set(authorization.permissions)

  const anyAllowed = anyOf.length === 0 || anyOf.some((code) => owned.has(code))
  const allAllowed = allOf.every((code) => owned.has(code))

  return anyAllowed && allAllowed ? children : fallback
}
```

## 6. i18n

中文：

```ts
export const navZhCN = {
  'nav.dashboard': '工作台',
  'nav.workRecords.group': '工作记录',
  'nav.workRecords.records': '记录列表',
  'nav.workRecords.designer': '表单设计',
  'nav.workRecords.tasks': '导入导出任务',
  'nav.platform.group': '平台管理',
  'nav.platform.users': '用户管理',
  'nav.platform.roles': '角色权限',
  'nav.platform.dictionaries': '字典管理',
  'nav.platform.calendars': '工作日历',
  'nav.platform.audit': '审计日志',
}
```

英文对应补齐。

## 7. 测试

```ts
import { describe, expect, it } from 'vitest'
import { filterNavigation } from './filter-navigation'
import { navigation } from './navigation'

describe('filterNavigation', () => {
  it('removes platform items without platform permissions', () => {
    const result = filterNavigation(navigation, {
      userId: 'u1',
      tenantId: 't1',
      roles: ['normal_user'],
      permissions: ['work-record:read:self'],
      dataScopes: { 'work-record': 'SELF' },
    })

    const serialized = JSON.stringify(result)

    expect(serialized).toContain('/work-records')
    expect(serialized).not.toContain('/platform/users')
    expect(serialized).not.toContain('/platform/roles')
  })

  it('keeps role management for authorized admin', () => {
    const result = filterNavigation(navigation, {
      userId: 'admin',
      tenantId: 't1',
      roles: ['system_admin'],
      permissions: ['platform:role:read'],
      dataScopes: {},
    })

    expect(JSON.stringify(result)).toContain('/platform/roles')
  })
})
```

Route Guard 测试需覆盖未登录跳登录、无权限跳 403、已授权正常进入。

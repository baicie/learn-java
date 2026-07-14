---
title: 用户管理与角色权限 Portal 完整代码
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# 用户管理与角色权限 Portal 完整代码

## 1. API Schema

```ts
import { z } from 'zod'

export const roleRefSchema = z.object({
  code: z.string(),
  name: z.string(),
})

export const platformUserSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  username: z.string(),
  displayName: z.string(),
  email: z.string().nullable(),
  status: z.enum(['active', 'disabled', 'locked', 'pending']),
  roles: z.array(roleRefSchema),
  dataScopes: z.record(z.string(), z.string()),
  lastLoginAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  rowVersion: z.number().int(),
})

export const platformUserPageSchema = z.object({
  items: z.array(platformUserSchema),
  page: z.number().int(),
  pageSize: z.number().int(),
  total: z.number().int(),
})
```

## 2. User API

```ts
import { apiClient } from '@/lib/api-client'
import { unwrapApiResponse } from '@/lib/api-response'

export type PlatformUserQuery = {
  page: number
  pageSize: number
  keyword?: string
  statuses?: string[]
  roleCodes?: string[]
  dataScope?: string
  sortBy?: string
  sortDir?: 'asc' | 'desc'
}

export async function fetchPlatformUsers(query: PlatformUserQuery) {
  const response = await apiClient.get('/api/platform/users', {
    params: {
      ...query,
      statuses: query.statuses?.join(','),
      roleCodes: query.roleCodes?.join(','),
    },
  })

  return platformUserPageSchema.parse(unwrapApiResponse(response.data))
}

export async function createPlatformUser(input: CreatePlatformUserInput) {
  const response = await apiClient.post('/api/platform/users', input)
  return platformUserSchema.parse(unwrapApiResponse(response.data))
}

export async function replacePlatformUserRoles(userId: string, input: ReplaceUserRolesInput) {
  const response = await apiClient.put(`/api/platform/users/${userId}/roles`, input)
  return platformUserSchema.parse(unwrapApiResponse(response.data))
}
```

## 3. Query Keys

```ts
export const platformUserKeys = {
  all: ['platform-users'] as const,
  lists: () => [...platformUserKeys.all, 'list'] as const,
  list: (query: PlatformUserQuery) => [...platformUserKeys.lists(), query] as const,
  detail: (id: string) => [...platformUserKeys.all, 'detail', id] as const,
}
```

## 4. 页面

```tsx
export function PlatformUsersPage() {
  const route = getRouteApi('/_authenticated/platform/users/')
  const search = route.useSearch()
  const navigate = route.useNavigate()
  const query = usePlatformUsers(search)
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <>
      <Header fixed>
        <Search className="me-auto" />
        <ThemeSwitch />
        <ProfileDropdown />
      </Header>

      <Main className="flex flex-1 flex-col gap-4">
        <PageHeader
          title="用户管理"
          description="管理租户用户、状态、角色与数据范围。"
          actions={
            <PermissionGate anyOf={['platform:user:write']}>
              <Button onClick={() => setCreateOpen(true)}>
                <Plus className="mr-2 size-4" />
                新建用户
              </Button>
            </PermissionGate>
          }
        />

        <PlatformUserToolbar
          value={search}
          onChange={(next) =>
            navigate({ search: (previous) => ({ ...previous, ...next, page: 1 }) })
          }
        />

        {query.isPending ? <PageLoadingState /> : null}
        {query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} /> : null}

        {query.data?.total === 0 ? (
          <EmptyState title="暂无用户" description="创建第一个真实平台用户。" />
        ) : null}

        {query.data?.total ? (
          <PlatformUserTable
            page={query.data}
            query={search}
            onQueryChange={(next) => navigate({ search: next })}
          />
        ) : null}
      </Main>

      <PlatformUserCreateDialog open={createOpen} onOpenChange={setCreateOpen} />
    </>
  )
}
```

## 5. User Table

列：

```text
username
displayName
email
status
roles
dataScopes
lastLoginAt
createdAt
actions
```

不要保留电话列和模板角色。

角色渲染：

```tsx
function RoleBadges({ roles }: { roles: RoleRef[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <Badge key={role.code} variant="outline">
          {role.name}
        </Badge>
      ))}
    </div>
  )
}
```

状态操作必须使用 `useConfirm`：

```tsx
const confirm = useConfirm()

async function disableUser(user: PlatformUser) {
  const accepted = await confirm({
    title: `禁用用户 ${user.displayName}`,
    description: '禁用后该用户将无法登录。',
    confirmationText: user.username,
    confirmLabel: '确认禁用',
    destructive: true,
  })

  if (!accepted) return

  await mutation.mutateAsync({
    userId: user.id,
    status: 'disabled',
    reason: '管理员操作',
    rowVersion: user.rowVersion,
  })
}
```

## 6. User Mutation

```ts
export function useCreatePlatformUser() {
  const client = useQueryClient()

  return useMutation({
    mutationFn: createPlatformUser,
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: platformUserKeys.lists() })
      notify.success('用户已创建')
    },
    onError: notifyApiError,
  })
}
```

## 7. Role API

```ts
export const permissionDefinitionSchema = z.object({
  code: z.string(),
  moduleCode: z.string(),
  moduleName: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  riskLevel: z.enum(['normal', 'sensitive', 'high', 'critical']),
  dependencies: z.array(z.string()),
})

export const platformRoleSchema = z.object({
  roleCode: z.string(),
  roleName: z.string(),
  description: z.string().nullable(),
  enabled: z.boolean(),
  system: z.boolean(),
  userCount: z.number().int(),
  permissions: z.array(z.string()),
  dataScopes: z.record(
    z.string(),
    z.object({
      scopeType: z.enum(['ALL', 'SELF', 'DEPARTMENT', 'CUSTOM']),
      scope: z.record(z.string(), z.unknown()),
    })
  ),
  rowVersion: z.number().int(),
})
```

## 8. 角色页面

```tsx
export function PlatformRolesPage() {
  const roles = usePlatformRoles()
  const permissions = usePermissionTree()
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const editor = useRoleEditor(selectedCode)

  useEffect(() => {
    if (!selectedCode && roles.data?.length) {
      setSelectedCode(roles.data[0].roleCode)
    }
  }, [roles.data, selectedCode])

  return (
    <>
      <Header fixed>
        <Search className="me-auto" />
        <ThemeSwitch />
        <ProfileDropdown />
      </Header>

      <Main className="min-h-0 flex-1 overflow-hidden p-0">
        <div className="grid h-full min-h-0 grid-cols-[280px_minmax(0,1fr)]">
          <RoleListPanel
            roles={roles.data ?? []}
            selectedCode={selectedCode}
            onSelect={async (next) => {
              if (!(await editor.confirmLeaveIfDirty())) return
              setSelectedCode(next)
            }}
          />

          <RoleEditor editor={editor} permissionTree={permissions.data ?? []} />
        </div>
      </Main>
    </>
  )
}
```

## 9. Permission Matrix

```tsx
export function PermissionModule({ module, selected, onChange }: PermissionModuleProps) {
  const childCodes = module.children.map((item) => item.code)
  const checkedCount = childCodes.filter((code) => selected.has(code)).length
  const allChecked = checkedCount === childCodes.length
  const indeterminate = checkedCount > 0 && !allChecked

  return (
    <section className="rounded-lg border">
      <div className="flex items-center gap-3 border-b p-3">
        <Checkbox
          checked={allChecked ? true : indeterminate ? 'indeterminate' : false}
          onCheckedChange={(checked) => onChange(childCodes, checked === true)}
        />
        <div>
          <h3 className="font-medium">{module.moduleName}</h3>
          <p className="text-xs text-muted-foreground">
            {checkedCount}/{childCodes.length} 项
          </p>
        </div>
      </div>

      <div className="grid gap-2 p-3 md:grid-cols-2">
        {module.children.map((permission) => (
          <PermissionItem
            key={permission.code}
            permission={permission}
            checked={selected.has(permission.code)}
            onCheckedChange={(value) => onChange([permission.code], value)}
          />
        ))}
      </div>
    </section>
  )
}
```

危险权限使用 Badge，保存时展示差异确认。

## 10. 前端测试

```tsx
describe('PlatformUsersPage', () => {
  it('renders server users and never renders fake template users', async () => {
    server.use(
      http.get('/api/platform/users', () =>
        HttpResponse.json(
          ok({
            items: [platformUser({ username: 'alice' })],
            page: 1,
            pageSize: 20,
            total: 1,
          })
        )
      )
    )

    const screen = await renderApp('/platform/users')

    await expect.element(screen.getByText('alice')).toBeVisible()
    await expect.element(screen.getByText('cashier')).not.toBeInTheDocument()
  })
})
```

```tsx
describe('PermissionMatrix', () => {
  it('supports indeterminate module state', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <PermissionModule
        module={moduleWithTwoPermissions()}
        selected={new Set(['work-record:read:self'])}
        onChange={onChange}
      />
    )

    await expect
      .element(screen.getByRole('checkbox').first())
      .toHaveAttribute('data-state', 'indeterminate')
  })
})
```

还需覆盖：

```text
用户创建表单
角色分配
用户禁用确认
系统角色删除按钮禁用
切换角色未保存提示
危险权限确认
API 409 rowVersion 冲突提示
移动端角色详情页
```

import { useEffect, useState } from 'react'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { PermissionGate } from '@/features/auth/permission-gate'
import { usePlatformRoles, usePermissionTree } from '../hooks/use-platform-roles'
import { RoleEditor, useRoleEditor } from '../components/role-editor'

export function PlatformRolesPage() {
  const rolesQuery = usePlatformRoles()
  const permissionsQuery = usePermissionTree()
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const editor = useRoleEditor(selectedCode ?? undefined)

  const firstRole = rolesQuery.data?.[0]
  const effectiveCode = selectedCode ?? firstRole?.roleCode ?? null

  useEffect(() => {
    if (!selectedCode && firstRole) {
      editor.apply(firstRole)
    }
  }, [selectedCode, firstRole, editor])

  const onSelect = async (next: string) => {
    const ok = await editor.confirmLeaveIfDirty()
    if (!ok) return
    const role = rolesQuery.data?.find((r) => r.roleCode === next)
    if (role) {
      editor.apply(role)
    }
    setSelectedCode(next)
  }

  return (
    <>
      <Header fixed>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ProfileDropdown />
      </Header>

      <Main className='min-h-0 flex-1 overflow-hidden p-0'>
        <div className='grid h-full min-h-0 grid-cols-[280px_minmax(0,1fr)]'>
          <aside className='flex flex-col overflow-y-auto border-r bg-muted/20'>
            <div className='flex items-center justify-between border-b px-4 py-3'>
              <h2 className='text-sm font-semibold'>角色</h2>
              <PermissionGate anyOf={['platform:role:write']}>
                <Button size='sm' variant='outline' disabled>
                  新建
                </Button>
              </PermissionGate>
            </div>
            <ul className='flex flex-col'>
              {rolesQuery.data?.map((role) => (
                <li key={role.roleCode}>
                  <button
                    type='button'
                    onClick={() => onSelect(role.roleCode)}
                    className={`flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm hover:bg-muted ${
                      effectiveCode === role.roleCode ? 'bg-muted/60' : ''
                    }`}
                    data-testid={`role-list-item-${role.roleCode}`}
                  >
                    <span className='flex flex-col'>
                      <span className='font-medium'>{role.roleName}</span>
                      <span className='font-mono text-xs text-muted-foreground'>
                        {role.roleCode}
                      </span>
                    </span>
                    <span className='flex items-center gap-1'>
                      {role.system ? (
                        <Badge variant='secondary'>系统</Badge>
                      ) : null}
                      <Badge variant='outline'>{role.userCount} 用户</Badge>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </aside>

          <RoleEditor
            editor={editor}
            permissionTree={permissionsQuery.data ?? []}
          />
        </div>
      </Main>
    </>
  )
}
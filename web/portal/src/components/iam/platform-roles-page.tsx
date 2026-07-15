import { useState } from 'react'
import { PermissionGate } from '@/auth/permission-gate'
import { useTranslation } from 'react-i18next'
import {
  usePlatformRoles,
  usePermissionTree,
} from '@/hooks/iam/use-platform-roles'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PlatformRoleCreateDialog } from './platform-role-create-dialog'
import { RoleEditor, useRoleEditor } from './role-editor'

export function PlatformRolesPage() {
  const { t } = useTranslation()
  const rolesQuery = usePlatformRoles()
  const permissionsQuery = usePermissionTree()
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const firstRole = rolesQuery.data?.[0]
  const effectiveCode = selectedCode ?? firstRole?.roleCode ?? null
  const effectiveRole = rolesQuery.data?.find(
    (role) => role.roleCode === effectiveCode
  )
  const editor = useRoleEditor(effectiveRole)

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
      <main className='flex min-h-0 flex-1 flex-col gap-4 p-4 md:gap-6 md:p-6'>
        <header className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
          <div>
            <h1 className='text-xl font-semibold md:text-2xl'>
              {t('platform.roles.title')}
            </h1>
            <p className='text-sm text-muted-foreground'>
              {t('platform.roles.description')}
            </p>
          </div>
          <PermissionGate anyOf={['platform:role:write']}>
            <Button onClick={() => setCreateOpen(true)}>
              {t('platform.roles.create')}
            </Button>
          </PermissionGate>
        </header>

        <div className='grid min-h-0 flex-1 grid-cols-[280px_minmax(0,1fr)] overflow-hidden rounded-lg border'>
          <aside className='flex flex-col overflow-y-auto border-r bg-muted/20'>
            <div className='border-b px-4 py-3'>
              <h2 className='text-sm font-semibold'>角色</h2>
            </div>
            <ul className='flex flex-col'>
              {rolesQuery.data?.map((role) => (
                <li key={role.roleCode}>
                  <Button
                    type='button'
                    onClick={() => onSelect(role.roleCode)}
                    variant={
                      effectiveCode === role.roleCode ? 'secondary' : 'ghost'
                    }
                    className='h-auto w-full justify-between rounded-none px-4 py-2 text-left'
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
                  </Button>
                </li>
              ))}
            </ul>
          </aside>

          <RoleEditor
            editor={editor}
            permissionTree={permissionsQuery.data ?? []}
            onDeleted={() => setSelectedCode(null)}
          />
        </div>
      </main>
      <PlatformRoleCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
    </>
  )
}

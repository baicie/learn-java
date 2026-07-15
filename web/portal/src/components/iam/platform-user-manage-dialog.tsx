import { useState } from 'react'
import { PermissionGate } from '@/auth/permission-gate'
import type { PlatformUser } from '@/lib/iam/platform-user'
import { usePlatformRoles } from '@/hooks/iam/use-platform-roles'
import {
  useReplacePlatformUserRoles,
  useResetPlatformUserPassword,
  useUpdatePlatformUser,
} from '@/hooks/iam/use-platform-users'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

export function PlatformUserManageDialog({
  user,
  open,
  onOpenChange,
}: {
  user: PlatformUser
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const roles = usePlatformRoles()
  const update = useUpdatePlatformUser(user.id)
  const replaceRoles = useReplacePlatformUserRoles(user.id)
  const resetPassword = useResetPlatformUserPassword(user.id)
  const [displayName, setDisplayName] = useState(user.displayName)
  const [email, setEmail] = useState(user.email ?? '')
  const [roleCodes, setRoleCodes] = useState(
    user.roles.map((role) => role.code)
  )
  const [newPassword, setNewPassword] = useState('')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[85vh] overflow-y-auto'>
        <DialogHeader>
          <DialogTitle>管理用户：{user.username}</DialogTitle>
          <DialogDescription>
            编辑资料、分配角色或重置登录密码。
          </DialogDescription>
        </DialogHeader>

        <PermissionGate anyOf={['platform:user:write']}>
          <section className='grid gap-3 rounded-md border p-4'>
            <h3 className='font-medium'>基本资料</h3>
            <Input
              aria-label='显示名'
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
            <Input
              aria-label='邮箱'
              type='email'
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <Button
              disabled={update.isPending || !displayName.trim()}
              onClick={() =>
                update.mutate({
                  displayName: displayName.trim(),
                  email: email || null,
                })
              }
            >
              保存资料
            </Button>
          </section>
        </PermissionGate>

        <PermissionGate anyOf={['platform:user:assign-role']}>
          <section className='grid gap-3 rounded-md border p-4'>
            <h3 className='font-medium'>角色分配</h3>
            {roles.data?.map((role) => (
              <label
                key={role.roleCode}
                className='flex items-center gap-2 text-sm'
              >
                <Checkbox
                  checked={roleCodes.includes(role.roleCode)}
                  onCheckedChange={(checked) =>
                    setRoleCodes((current) =>
                      checked
                        ? [...current, role.roleCode]
                        : current.filter((code) => code !== role.roleCode)
                    )
                  }
                />
                <span>{role.roleName}</span>
              </label>
            ))}
            <Button
              disabled={replaceRoles.isPending}
              onClick={() =>
                replaceRoles.mutate({
                  roleCodes,
                  reason: '管理员在 Portal 调整角色',
                  rowVersion: user.rowVersion,
                })
              }
            >
              保存角色
            </Button>
          </section>
        </PermissionGate>

        <PermissionGate anyOf={['platform:user:reset-password']}>
          <section className='grid gap-3 rounded-md border p-4'>
            <h3 className='font-medium'>重置密码</h3>
            <Input
              aria-label='新密码'
              type='password'
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              placeholder='至少 8 个字符'
            />
            <Button
              variant='destructive'
              disabled={resetPassword.isPending || newPassword.length < 8}
              onClick={() =>
                resetPassword.mutate(
                  { newPassword },
                  { onSuccess: () => setNewPassword('') }
                )
              }
            >
              重置密码
            </Button>
          </section>
        </PermissionGate>
      </DialogContent>
    </Dialog>
  )
}

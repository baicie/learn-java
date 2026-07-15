import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toIamRequestError } from '@/lib/iam/errors/iam-api-error'
import { usePlatformRoles } from '@/hooks/iam/use-platform-roles'
import { useCreatePlatformUser } from '@/hooks/iam/use-platform-users'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

export type PlatformUserCreateDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
}

const EMPTY = {
  username: '',
  displayName: '',
  email: '',
  initialPassword: '',
  roleCodes: [] as string[],
}

export function PlatformUserCreateDialog({
  open,
  onOpenChange,
}: PlatformUserCreateDialogProps) {
  const { t } = useTranslation()
  const mutation = useCreatePlatformUser()
  const roles = usePlatformRoles()
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<string | null>(null)

  const onOpenChangeWrapped = (next: boolean) => {
    if (!next) {
      setForm(EMPTY)
      setError(null)
    }
    onOpenChange(next)
  }

  const onSubmit = async () => {
    setError(null)
    try {
      await mutation.mutateAsync({
        username: form.username,
        displayName: form.displayName,
        email: form.email || null,
        initialPassword: form.initialPassword,
        status: 'active',
        roleCodes: form.roleCodes,
      })
      onOpenChange(false)
    } catch (raw) {
      const err = toIamRequestError(raw)
      setError(err.message)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChangeWrapped}>
      <DialogContent data-testid='user-create-dialog'>
        <DialogHeader>
          <DialogTitle>{t('platform.users.createDialog.title')}</DialogTitle>
          <DialogDescription>
            新用户默认为 active 状态；初始密码至少 8 个字符。
          </DialogDescription>
        </DialogHeader>

        <div className='grid gap-3'>
          <label className='flex flex-col gap-1 text-sm'>
            <span>用户名</span>
            <Input
              value={form.username}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, username: event.target.value }))
              }
              autoFocus
            />
          </label>
          <fieldset className='grid gap-2 rounded-md border p-3'>
            <legend className='px-1 text-sm font-medium'>初始角色</legend>
            {roles.data?.map((role) => (
              <label
                key={role.roleCode}
                className='flex items-center gap-2 text-sm'
              >
                <Checkbox
                  checked={form.roleCodes.includes(role.roleCode)}
                  onCheckedChange={(checked) =>
                    setForm((previous) => ({
                      ...previous,
                      roleCodes: checked
                        ? [...previous.roleCodes, role.roleCode]
                        : previous.roleCodes.filter(
                            (code) => code !== role.roleCode
                          ),
                    }))
                  }
                />
                <span>{role.roleName}</span>
              </label>
            ))}
          </fieldset>
          <label className='flex flex-col gap-1 text-sm'>
            <span>显示名</span>
            <Input
              value={form.displayName}
              onChange={(event) =>
                setForm((prev) => ({
                  ...prev,
                  displayName: event.target.value,
                }))
              }
            />
          </label>
          <label className='flex flex-col gap-1 text-sm'>
            <span>邮箱</span>
            <Input
              type='email'
              value={form.email}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, email: event.target.value }))
              }
            />
          </label>
          <label className='flex flex-col gap-1 text-sm'>
            <span>初始密码</span>
            <Input
              type='password'
              value={form.initialPassword}
              onChange={(event) =>
                setForm((prev) => ({
                  ...prev,
                  initialPassword: event.target.value,
                }))
              }
            />
          </label>
          {error ? <p className='text-sm text-destructive'>{error}</p> : null}
        </div>

        <DialogFooter>
          <Button variant='outline' onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={onSubmit} disabled={mutation.isPending}>
            {t('platform.users.createDialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useCreatePlatformUser } from '../hooks/use-platform-users'
import { toIamRequestError } from '../errors/iam-api-error'

export type PlatformUserCreateDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
}

const EMPTY = {
  username: '',
  displayName: '',
  email: '',
  initialPassword: '',
}

export function PlatformUserCreateDialog({
  open,
  onOpenChange,
}: PlatformUserCreateDialogProps) {
  const { t } = useTranslation('platform')
  const mutation = useCreatePlatformUser()
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
        roleCodes: [],
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
          {error ? (
            <p className='text-sm text-destructive'>{error}</p>
          ) : null}
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
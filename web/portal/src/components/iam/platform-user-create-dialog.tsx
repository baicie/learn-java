import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toIamRequestError } from '@/lib/iam/errors/iam-api-error'
import { createPlatformUserSchema } from '@/lib/iam/platform-user'
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
import { FormFieldShell } from '@/components/form/form-field-shell'

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

type TextFormField = Exclude<keyof typeof EMPTY, 'roleCodes'>
type FormErrors = Partial<Record<TextFormField, string>>

export function PlatformUserCreateDialog({
  open,
  onOpenChange,
}: PlatformUserCreateDialogProps) {
  const { t } = useTranslation()
  const mutation = useCreatePlatformUser()
  const roles = usePlatformRoles()
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({})

  const onOpenChangeWrapped = (next: boolean) => {
    if (!next) {
      setForm(EMPTY)
      setError(null)
      setFieldErrors({})
    }
    onOpenChange(next)
  }

  const updateField = (field: TextFormField, value: string) => {
    setForm((previous) => ({ ...previous, [field]: value }))
    setFieldErrors((previous) => ({ ...previous, [field]: undefined }))
  }

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    const input = {
      username: form.username,
      displayName: form.displayName,
      email: form.email || null,
      initialPassword: form.initialPassword,
      status: 'active' as const,
      roleCodes: form.roleCodes,
    }
    const parsed = createPlatformUserSchema.safeParse(input)
    if (!parsed.success) {
      const invalid = new Set(parsed.error.issues.map((issue) => issue.path[0]))
      setFieldErrors({
        username: invalid.has('username')
          ? t('platform.users.createDialog.validation.username')
          : undefined,
        displayName: invalid.has('displayName')
          ? t('platform.users.createDialog.validation.displayName')
          : undefined,
        email: invalid.has('email')
          ? t('platform.users.createDialog.validation.email')
          : undefined,
        initialPassword: invalid.has('initialPassword')
          ? t('platform.users.createDialog.validation.password')
          : undefined,
      })
      return
    }

    setFieldErrors({})
    try {
      await mutation.mutateAsync(parsed.data)
      onOpenChangeWrapped(false)
    } catch (raw) {
      const err = toIamRequestError(raw)
      setError(
        err.code === 'platform.user.username_conflict'
          ? t('platform.users.error.username_conflict')
          : err.message
      )
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChangeWrapped}>
      <DialogContent data-testid='user-create-dialog'>
        <DialogHeader>
          <DialogTitle>{t('platform.users.createDialog.title')}</DialogTitle>
          <DialogDescription>
            {t('platform.users.createDialog.description')}
          </DialogDescription>
        </DialogHeader>

        <form className='grid gap-4' onSubmit={onSubmit} noValidate>
          <FormFieldShell
            id='platform-user-username'
            label={t('platform.users.createDialog.username')}
            required
            error={fieldErrors.username}
          >
            {(props) => (
              <Input
                {...props}
                value={form.username}
                onChange={(event) =>
                  updateField('username', event.target.value)
                }
                autoComplete='username'
                autoFocus
              />
            )}
          </FormFieldShell>
          <fieldset className='grid gap-2 rounded-md border p-3'>
            <legend className='px-1 text-sm font-medium'>
              {t('platform.users.createDialog.roles')}
            </legend>
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
          <FormFieldShell
            id='platform-user-display-name'
            label={t('platform.users.createDialog.displayName')}
            required
            error={fieldErrors.displayName}
          >
            {(props) => (
              <Input
                {...props}
                value={form.displayName}
                onChange={(event) =>
                  updateField('displayName', event.target.value)
                }
                autoComplete='name'
              />
            )}
          </FormFieldShell>
          <FormFieldShell
            id='platform-user-email'
            label={t('platform.users.createDialog.email')}
            hint={t('platform.users.createDialog.emailHint')}
            error={fieldErrors.email}
          >
            {(props) => (
              <Input
                {...props}
                type='email'
                value={form.email}
                onChange={(event) => updateField('email', event.target.value)}
                autoComplete='email'
              />
            )}
          </FormFieldShell>
          <FormFieldShell
            id='platform-user-initial-password'
            label={t('platform.users.createDialog.password')}
            required
            error={fieldErrors.initialPassword}
          >
            {(props) => (
              <Input
                {...props}
                type='password'
                value={form.initialPassword}
                onChange={(event) =>
                  updateField('initialPassword', event.target.value)
                }
                autoComplete='new-password'
              />
            )}
          </FormFieldShell>
          {error ? <p className='text-sm text-destructive'>{error}</p> : null}
          <DialogFooter>
            <Button
              type='button'
              variant='outline'
              onClick={() => onOpenChangeWrapped(false)}
            >
              {t('platform.users.createDialog.cancel')}
            </Button>
            <Button type='submit' disabled={mutation.isPending}>
              {t('platform.users.createDialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

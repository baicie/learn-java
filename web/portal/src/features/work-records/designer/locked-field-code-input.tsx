import { useI18n } from '@/i18n/provider'
import { Lock } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { FormFieldShell } from '@/components/form/form-field-shell'

export function LockedFieldCodeInput({
  value,
  locked,
  error,
  onChange,
}: {
  value: string
  locked: boolean
  error?: string
  onChange: (value: string) => void
}) {
  const { t } = useI18n()

  return (
    <FormFieldShell
      id='fieldCode'
      label={t('template.field.code')}
      required
      error={error}
      hint={
        locked
          ? t('template.field.code.lockedHint')
          : '仅支持英文字母开头，后续可包含字母、数字和下划线。'
      }
    >
      <div className='relative'>
        <Input
          id='fieldCode'
          value={value}
          readOnly={locked}
          aria-readonly={locked}
          aria-invalid={Boolean(error)}
          className={locked ? 'bg-muted/50 pr-10' : undefined}
          onChange={(event) => onChange(event.target.value)}
        />

        {locked ? (
          <Lock
            className='absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground'
            aria-label={t('template.field.code.locked')}
          />
        ) : null}
      </div>
    </FormFieldShell>
  )
}

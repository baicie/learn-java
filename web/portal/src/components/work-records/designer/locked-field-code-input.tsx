import { Lock } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Input } from '@/components/ui/input'
import { FormFieldShell } from '@/components/form/form-field-shell'

export function LockedFieldCodeInput({
  value,
  locked,
  error,
  hint,
  onChange,
}: {
  value: string
  locked: boolean
  error?: string
  hint?: string
  onChange: (value: string) => void
}) {
  const { t } = useTranslation()

  return (
    <FormFieldShell
      id='fieldCode'
      label={t('workRecords.designer.property.fieldCode')}
      required
      error={error}
      hint={hint ?? t('workRecords.designer.fieldCodeLockedHint')}
    >
      {(controlProps) => (
        <div className='relative'>
          <Input
            {...controlProps}
            value={value}
            readOnly={locked}
            aria-readonly={locked}
            className={locked ? 'bg-muted/50 pr-10' : undefined}
            onChange={(event) => onChange(event.target.value)}
          />

          {locked ? (
            <Lock
              className='absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground'
              aria-label={t('workRecords.designer.fieldCodeLocked')}
            />
          ) : null}
        </div>
      )}
    </FormFieldShell>
  )
}

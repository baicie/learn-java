import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LockedFieldCodeInput } from './locked-field-code-input'
import {
  type DictTypeOption,
  type DesignerField,
  WORK_RECORD_FIELD_TYPES,
  type WorkRecordFieldType,
} from './types'

type PropertyPanelProps = {
  field?: DesignerField
  dictTypes: DictTypeOption[]
  onChange: (fieldId: string, patch: Partial<DesignerField>) => void
}

export function PropertyPanel({
  field,
  dictTypes,
  onChange,
}: PropertyPanelProps) {
  const { t } = useTranslation()
  if (!field) {
    return (
      <Card className='h-full'>
        <CardHeader>
          <CardTitle>{t('workRecords.designer.property.region')}</CardTitle>
        </CardHeader>
        <CardContent className='text-sm text-muted-foreground'>
          {t('workRecords.designer.property.empty')}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className='h-full'>
      <CardHeader>
        <CardTitle>{t('workRecords.designer.property.region')}</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-4 text-sm'>
        <FieldInput
          label={t('workRecords.designer.property.title')}
          value={field.fieldName}
          onChange={(value) => onChange(field.id, { fieldName: value })}
        />

        <LockedFieldCodeInput
          value={field.fieldCode}
          locked={field.locked}
          error={
            field.locked ? undefined : `规则：^[a-zA-Z][a-zA-Z0-9_]{0,63}$`
          }
          onChange={(value) => onChange(field.id, { fieldCode: value })}
        />

        <label className='grid gap-1'>
          <span className='text-xs text-muted-foreground'>
            {t('workRecords.designer.property.fieldType')}
          </span>
          <select
            className='rounded-md border bg-background px-3 py-2'
            value={field.fieldType}
            disabled={field.locked}
            onChange={(event) =>
              onChange(field.id, {
                fieldType: event.target.value as WorkRecordFieldType,
              })
            }
          >
            {WORK_RECORD_FIELD_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          {field.locked ? (
            <span className='text-xs text-muted-foreground'>
              {t('workRecords.designer.property.fieldCodeLockedHint')}
            </span>
          ) : null}
        </label>

        <label className='flex items-center gap-2'>
          <input
            type='checkbox'
            checked={field.required}
            onChange={(event) =>
              onChange(field.id, { required: event.target.checked })
            }
          />
          {t('workRecords.designer.property.required')}
        </label>

        <label className='grid gap-1'>
          <span className='text-xs text-muted-foreground'>
            {t('workRecords.designer.property.optionSource')}
          </span>
          <select
            className='rounded-md border bg-background px-3 py-2'
            value={field.optionSource}
            onChange={(event) =>
              onChange(field.id, {
                optionSource: event.target.value === 'dict' ? 'dict' : 'static',
              })
            }
          >
            <option value='static'>
              {t('workRecords.designer.property.optionSourceStatic')}
            </option>
            <option value='dict'>
              {t('workRecords.designer.property.optionSourceDict')}
            </option>
          </select>
        </label>

        {field.optionSource === 'dict' ? (
          <label className='grid gap-1'>
            <span className='text-xs text-muted-foreground'>
              {t('workRecords.designer.property.dictCode')}
            </span>
            <select
              className='rounded-md border bg-background px-3 py-2'
              value={field.dictCode}
              onChange={(event) =>
                onChange(field.id, { dictCode: event.target.value })
              }
            >
              <option value=''>{t('workRecords.form.select')}</option>
              {dictTypes
                .filter((item) => item.enabled)
                .map((dict) => (
                  <option key={dict.id} value={dict.dictCode}>
                    {dict.dictName} / {dict.dictCode}
                  </option>
                ))}
            </select>
          </label>
        ) : null}

        <div className='grid gap-2 rounded-md border p-3'>
          <span className='text-xs font-medium text-muted-foreground'>
            {t('workRecords.designer.property.indexes')}
          </span>
          <Flag
            label={t('workRecords.designer.property.listVisible')}
            checked={field.listVisible}
            onChange={(value) => onChange(field.id, { listVisible: value })}
          />
          <Flag
            label={t('workRecords.designer.property.filterable')}
            checked={field.filterable}
            onChange={(value) => onChange(field.id, { filterable: value })}
          />
          <Flag
            label={t('workRecords.designer.property.exportable')}
            checked={field.exportable}
            onChange={(value) => onChange(field.id, { exportable: value })}
          />
          <Flag
            label={t('workRecords.designer.property.statistical')}
            checked={field.statistical}
            onChange={(value) => onChange(field.id, { statistical: value })}
          />
        </div>
      </CardContent>
    </Card>
  )
}

function FieldInput({
  label,
  value,
  disabled,
  help,
  onChange,
}: {
  label: string
  value: string
  disabled?: boolean
  help?: string
  onChange: (value: string) => void
}) {
  return (
    <label className='grid gap-1'>
      <span className='text-xs text-muted-foreground'>{label}</span>
      <input
        className='rounded-md border bg-background px-3 py-2'
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
      {help ? (
        <span className='text-xs text-muted-foreground'>{help}</span>
      ) : null}
    </label>
  )
}

function Flag({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (value: boolean) => void
}) {
  return (
    <label className='flex items-center gap-2'>
      <input
        type='checkbox'
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  )
}

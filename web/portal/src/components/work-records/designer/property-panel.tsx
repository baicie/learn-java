import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
          <Select
            value={field.fieldType}
            disabled={field.locked}
            onValueChange={(value) =>
              onChange(field.id, { fieldType: value as WorkRecordFieldType })
            }
          >
            <SelectTrigger className='w-full'>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {WORK_RECORD_FIELD_TYPES.map((type) => (
                  <SelectItem key={type} value={type}>
                    {type}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          {field.locked ? (
            <span className='text-xs text-muted-foreground'>
              {t('workRecords.designer.property.fieldCodeLockedHint')}
            </span>
          ) : null}
        </label>

        <label className='flex items-center gap-2'>
          <Checkbox
            checked={field.required}
            onCheckedChange={(checked) =>
              onChange(field.id, { required: checked === true })
            }
          />
          {t('workRecords.designer.property.required')}
        </label>

        <label className='grid gap-1'>
          <span className='text-xs text-muted-foreground'>
            {t('workRecords.designer.property.optionSource')}
          </span>
          <Select
            value={field.optionSource}
            onValueChange={(value) =>
              onChange(field.id, {
                optionSource: value === 'dict' ? 'dict' : 'static',
              })
            }
          >
            <SelectTrigger className='w-full'>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value='static'>
                  {t('workRecords.designer.property.optionSourceStatic')}
                </SelectItem>
                <SelectItem value='dict'>
                  {t('workRecords.designer.property.optionSourceDict')}
                </SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </label>

        {field.optionSource === 'dict' ? (
          <label className='grid gap-1'>
            <span className='text-xs text-muted-foreground'>
              {t('workRecords.designer.property.dictCode')}
            </span>
            <Select
              value={field.dictCode || 'none'}
              onValueChange={(value) =>
                onChange(field.id, { dictCode: value === 'none' ? '' : value })
              }
            >
              <SelectTrigger className='w-full'>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value='none'>
                    {t('workRecords.form.select')}
                  </SelectItem>
                  {dictTypes
                    .filter((item) => item.enabled)
                    .map((dict) => (
                      <SelectItem key={dict.id} value={dict.dictCode}>
                        {dict.dictName} / {dict.dictCode}
                      </SelectItem>
                    ))}
                </SelectGroup>
              </SelectContent>
            </Select>
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
      <Input
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
      <Checkbox
        checked={checked}
        onCheckedChange={(value) => onChange(value === true)}
      />
      {label}
    </label>
  )
}

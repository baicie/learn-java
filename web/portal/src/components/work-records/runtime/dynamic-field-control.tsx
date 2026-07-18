import { useTranslation } from 'react-i18next'
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
import { Textarea } from '@/components/ui/textarea'
import type { RuntimeDictOptions, WorkRecordField } from './types'

type ControlProps = {
  id?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}

type DynamicFieldControlProps = {
  field: WorkRecordField
  value: unknown
  dictOptions: RuntimeDictOptions
  mode?: 'create' | 'edit'
  readonly?: boolean
  controlProps?: ControlProps
  onChange: (value: unknown) => void
}

export function DynamicFieldControl({
  field,
  value,
  dictOptions,
  mode,
  readonly,
  controlProps,
  onChange,
}: DynamicFieldControlProps) {
  const { t } = useTranslation()
  const disabledSuffix = t('workRecords.detail.disabledOption')

  if (readonly) {
    return (
      <ReadonlyValue
        field={field}
        value={value}
        dictOptions={dictOptions}
        disabledSuffix={disabledSuffix}
      />
    )
  }

  if (field.fieldType === 'textarea') {
    return (
      <Textarea
        {...controlProps}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  if (field.fieldType === 'number') {
    return (
      <Input
        {...controlProps}
        type='number'
        value={typeof value === 'number' ? value : ''}
        onChange={(event) =>
          onChange(
            event.target.value === '' ? undefined : Number(event.target.value)
          )
        }
      />
    )
  }

  if (field.fieldType === 'date') {
    return (
      <Input
        {...controlProps}
        type='date'
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  if (field.fieldType === 'datetime') {
    return (
      <Input
        {...controlProps}
        type='datetime-local'
        value={toDatetimeLocalValue(value)}
        onChange={(event) => {
          const raw = event.target.value
          if (!raw) {
            onChange(undefined)
            return
          }
          const date = new Date(raw)
          onChange(
            Number.isNaN(date.getTime()) ? undefined : date.toISOString()
          )
        }}
      />
    )
  }

  if (field.fieldType === 'boolean') {
    return (
      <label className='flex items-center gap-2 text-sm'>
        <Checkbox
          {...controlProps}
          checked={value === true}
          onCheckedChange={(checked) => onChange(checked === true)}
        />
        {t('workRecords.form.yes')}
      </label>
    )
  }

  if (field.fieldType === 'select') {
    const selectOptions = editableOptions(field, dictOptions, value, mode)
    return (
      <Select
        value={typeof value === 'string' && value ? value : undefined}
        onValueChange={onChange}
      >
        <SelectTrigger {...controlProps} className='w-full'>
          <SelectValue placeholder={t('workRecords.form.select')} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {selectOptions.map((item) => (
              <SelectItem
                key={item.itemValue}
                value={item.itemValue}
                disabled={!item.enabled}
              >
                {item.enabled
                  ? item.itemLabel
                  : `${item.itemLabel}${disabledSuffix}`}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    )
  }

  if (field.fieldType === 'multi_select') {
    const values = Array.isArray(value) ? value.map(String) : []
    const selectOptions = editableOptions(field, dictOptions, values, mode)
    return (
      <div {...controlProps} className='grid gap-2 rounded-md border p-3'>
        {selectOptions.map((item) => (
          <label
            key={item.itemValue}
            className='flex items-center gap-2 text-sm'
          >
            <Checkbox
              checked={values.includes(item.itemValue)}
              disabled={!item.enabled}
              onCheckedChange={(checked) =>
                onChange(
                  checked === true
                    ? [...values, item.itemValue]
                    : values.filter((current) => current !== item.itemValue)
                )
              }
            />
            {item.enabled
              ? item.itemLabel
              : `${item.itemLabel}${disabledSuffix}`}
          </label>
        ))}
      </div>
    )
  }

  return (
    <Input
      {...controlProps}
      value={typeof value === 'string' ? value : ''}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

function ReadonlyValue({
  field,
  value,
  dictOptions,
  disabledSuffix,
}: {
  field: WorkRecordField
  value: unknown
  dictOptions: RuntimeDictOptions
  disabledSuffix: string
}) {
  const { t } = useTranslation()
  let text = '-'

  if (field.optionSource === 'dict' && field.dictCode) {
    const opts = dictOptions[field.dictCode] ?? []
    if (Array.isArray(value)) {
      text = value
        .map((item) => label(opts, String(item), disabledSuffix))
        .join('、')
    } else if (typeof value === 'string') {
      text = label(opts, value, disabledSuffix)
    }
  } else if (field.fieldType === 'boolean') {
    text =
      value === true
        ? t('workRecords.form.booleanYes')
        : t('workRecords.form.booleanNo')
  } else if (Array.isArray(value)) {
    text = value.join('、')
  } else if (value !== undefined && value !== null && String(value) !== '') {
    text = String(value)
  }

  return (
    <div className='rounded-md border bg-muted px-3 py-2 text-sm'>{text}</div>
  )
}

function options(field: WorkRecordField, dictOptions: RuntimeDictOptions) {
  if (field.optionSource === 'dict' && field.dictCode) {
    return dictOptions[field.dictCode] ?? []
  }

  try {
    const parsed = JSON.parse(field.optionsJson || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.map((item) => ({
      id: String(item.value ?? item.label),
      itemLabel: String(item.label ?? item.value),
      itemValue: String(item.value ?? item.label),
      color: null,
      enabled: true,
    }))
  } catch {
    return []
  }
}

function editableOptions(
  field: WorkRecordField,
  dictOptions: RuntimeDictOptions,
  value: unknown,
  mode?: 'create' | 'edit'
) {
  const current = new Set(
    Array.isArray(value) ? value.map(String) : [String(value ?? '')]
  )
  return options(field, dictOptions).filter(
    (item) => item.enabled || (mode === 'edit' && current.has(item.itemValue))
  )
}

function label(
  options: { itemLabel: string; itemValue: string; enabled: boolean }[],
  value: string,
  disabledSuffix: string
) {
  const option = options.find((item) => item.itemValue === value)
  if (!option) return value
  return option.enabled
    ? option.itemLabel
    : `${option.itemLabel}${disabledSuffix}`
}

function toDatetimeLocalValue(value: unknown) {
  if (typeof value !== 'string' || !value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

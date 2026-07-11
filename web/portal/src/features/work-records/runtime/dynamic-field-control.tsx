import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
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
  readonly?: boolean
  controlProps?: ControlProps
  onChange: (value: unknown) => void
}

export function DynamicFieldControl({
  field,
  value,
  dictOptions,
  readonly,
  controlProps,
  onChange,
}: DynamicFieldControlProps) {
  const className = cn('rounded-md border bg-background px-3 py-2 text-sm')
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
      <textarea
        {...controlProps}
        className={className}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  if (field.fieldType === 'number') {
    return (
      <input
        {...controlProps}
        className={className}
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
      <input
        {...controlProps}
        className={className}
        type='date'
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  if (field.fieldType === 'datetime') {
    return (
      <input
        {...controlProps}
        className={className}
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
        <input
          {...controlProps}
          type='checkbox'
          checked={value === true}
          onChange={(event) => onChange(event.target.checked)}
        />
        {t('workRecords.form.yes')}
      </label>
    )
  }

  if (field.fieldType === 'select') {
    return (
      <select
        {...controlProps}
        className={className}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value=''>{t('workRecords.form.select')}</option>
        {options(field, dictOptions).map((item) => (
          <option
            key={item.itemValue}
            value={item.itemValue}
            disabled={!item.enabled}
          >
            {item.enabled
              ? item.itemLabel
              : `${item.itemLabel}${disabledSuffix}`}
          </option>
        ))}
      </select>
    )
  }

  if (field.fieldType === 'multi_select') {
    const values = Array.isArray(value) ? value.map(String) : []
    return (
      <select
        {...controlProps}
        className={className}
        multiple
        value={values}
        onChange={(event) =>
          onChange(
            Array.from(event.currentTarget.selectedOptions).map(
              (item) => item.value
            )
          )
        }
      >
        {options(field, dictOptions).map((item) => (
          <option
            key={item.itemValue}
            value={item.itemValue}
            disabled={!item.enabled}
          >
            {item.enabled
              ? item.itemLabel
              : `${item.itemLabel}${disabledSuffix}`}
          </option>
        ))}
      </select>
    )
  }

  return (
    <input
      {...controlProps}
      className={className}
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

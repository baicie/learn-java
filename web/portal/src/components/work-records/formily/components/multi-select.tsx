import { useField } from '@formily/react'
import { Checkbox } from '@/components/ui/checkbox'

type MultiSelectOption = { label: string; value: string; disabled?: boolean }

type MultiSelectField = {
  address: { toString(): string }
  value?: unknown
  dataSource?: {
    list?: Array<{ label: unknown; value: unknown; disabled?: boolean }>
  }
  componentProps?: {
    options?: MultiSelectOption[]
    enum?: MultiSelectOption[]
  }
  disabled?: boolean
  readOnly?: boolean
  onInput: (value: unknown) => void
}

function readOptions(field: MultiSelectField): MultiSelectOption[] {
  const props = field.componentProps ?? {}
  if (props.options?.length) return props.options
  if (props.enum?.length) return props.enum
  const dataList = field.dataSource?.list ?? []
  return dataList.map((item, idx) => ({
    label: String(item.label ?? idx),
    value: String(item.value ?? idx),
    disabled: item.disabled,
  }))
}

export function FormilyMultiSelect() {
  const field = useField() as unknown as MultiSelectField
  const options = readOptions(field)
  const value = Array.isArray(field.value) ? (field.value as unknown[]) : []

  const toggle = (next: string, checked: boolean) => {
    const set = new Set(value.map((item) => String(item)))
    if (checked) set.add(next)
    else set.delete(next)
    field.onInput(Array.from(set))
  }

  return (
    <div className='grid gap-2' data-testid='formily-multi-select'>
      {options.map((option) => {
        const v = option.value
        const isChecked = value.some((existing) => String(existing) === v)
        return (
          <label
            key={v}
            className='flex items-center gap-2 text-sm'
            data-testid={`formily-multi-select-${v}`}
          >
            <Checkbox
              checked={isChecked}
              disabled={field.disabled || field.readOnly || option.disabled}
              onCheckedChange={(checked) => toggle(v, Boolean(checked))}
            />
            <span>{option.label}</span>
          </label>
        )
      })}
    </div>
  )
}

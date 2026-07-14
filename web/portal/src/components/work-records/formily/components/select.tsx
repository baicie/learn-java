import { useField } from '@formily/react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export type FormilySelectOption = {
  label: string
  value: string
  disabled?: boolean
}

type SelectField = {
  value?: unknown
  disabled?: boolean
  readOnly?: boolean
  componentProps?: { options?: FormilySelectOption[]; placeholder?: string }
  onInput: (value: unknown) => void
}

export function FormilySelectImpl({
  value,
  onChange,
  options = [],
  placeholder,
  disabled,
}: {
  value?: string
  onChange?: (value: string) => void
  options?: FormilySelectOption[]
  placeholder?: string
  disabled?: boolean
}) {
  return (
    <Select value={value ?? ''} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger>
        <SelectValue placeholder={placeholder ?? '请选择'} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem
            key={option.value}
            value={option.value}
            disabled={option.disabled}
          >
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export function FormilySelect() {
  const field = useField() as unknown as SelectField
  const options = field.componentProps?.options ?? []
  const placeholder = field.componentProps?.placeholder
  const value = field.value == null ? '' : String(field.value)

  return (
    <FormilySelectImpl
      value={value}
      options={options}
      placeholder={placeholder}
      disabled={field.disabled || field.readOnly}
      onChange={(next) => field.onInput(next)}
    />
  )
}

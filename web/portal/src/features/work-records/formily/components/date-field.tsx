import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { Input } from '@/components/ui/input'

export function FormilyDatePicker() {
  const field = useField<Field>()
  const value = field.value ? String(field.value).slice(0, 10) : ''
  const testid = `formily-date-${field.address.toString().split('.').join('-')}`
  return (
    <Input
      data-testid={testid}
      type='date'
      value={value}
      disabled={field.disabled || field.readOnly}
      onChange={(event) =>
        field.onInput(event.target.value ? event.target.value : undefined)
      }
    />
  )
}

export function FormilyDateTimePicker() {
  const field = useField<Field>()
  const value = field.value ? String(field.value).slice(0, 16) : ''
  const testid = `formily-datetime-${field.address.toString().split('.').join('-')}`
  return (
    <Input
      data-testid={testid}
      type='datetime-local'
      value={value}
      disabled={field.disabled || field.readOnly}
      onChange={(event) =>
        field.onInput(event.target.value ? event.target.value : undefined)
      }
    />
  )
}
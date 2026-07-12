import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { Input } from '@/components/ui/input'

export function FormilyNumberInput() {
  const field = useField<Field>()
  const value = field.value == null ? '' : String(field.value)
  const testid = `formily-input-${field.address.toString().split('.').join('-')}`
  return (
    <Input
      data-testid={testid}
      type='number'
      value={value}
      disabled={field.disabled || field.readOnly}
      aria-invalid={field.selfErrors.length > 0}
      onChange={(event) => {
        const raw = event.target.value
        field.onInput(raw === '' ? undefined : Number(raw))
      }}
    />
  )
}
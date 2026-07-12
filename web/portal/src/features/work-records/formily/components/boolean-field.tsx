import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { Checkbox } from '@/components/ui/checkbox'

export function FormilyBoolean() {
  const field = useField<Field>()
  const testid = `formily-boolean-${field.address.toString().split('.').join('-')}`
  return (
    <Checkbox
      data-testid={testid}
      checked={Boolean(field.value)}
      disabled={field.disabled || field.readOnly}
      onCheckedChange={(checked) => field.onInput(checked === true)}
    />
  )
}
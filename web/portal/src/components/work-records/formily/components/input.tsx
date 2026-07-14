import { useField } from '@formily/react'
import { Input } from '@/components/ui/input'

type InputField = {
  id?: string
  address: { toString(): string }
  value?: unknown
  disabled?: boolean
  readOnly?: boolean
  selfErrors: string[]
  componentProps?: { placeholder?: string }
  onInput: (value: unknown) => void
  onBlur: () => void
}

export function FormilyInput() {
  const field = useField() as unknown as InputField
  const testid = `formily-input-${field.address.toString().split('.').join('-')}`
  return (
    <Input
      id={field.id}
      data-testid={testid}
      value={(field.value as string | undefined) ?? ''}
      placeholder={field.componentProps?.placeholder}
      disabled={field.disabled}
      readOnly={field.readOnly}
      aria-invalid={field.selfErrors.length > 0}
      onChange={(event) => field.onInput(event.target.value)}
      onBlur={() => field.onBlur()}
    />
  )
}

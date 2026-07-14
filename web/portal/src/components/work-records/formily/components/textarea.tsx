import { useField } from '@formily/react'
import { Textarea } from '@/components/ui/textarea'

type TextareaField = {
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

export function FormilyTextarea() {
  const field = useField() as unknown as TextareaField
  const testid = `formily-textarea-${field.address.toString().split('.').join('-')}`
  return (
    <Textarea
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

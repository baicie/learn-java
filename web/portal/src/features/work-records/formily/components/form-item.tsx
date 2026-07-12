import type { PropsWithChildren } from 'react'
import { useField, observer } from '@formily/react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

type AnyField = {
  title?: unknown
  required?: boolean
  description?: unknown
  selfErrors: Array<unknown>
  address: { toString(): string }
}

function flattenMessages(messages: Array<unknown>): string[] {
  const flat: string[] = []
  for (const m of messages) {
    if (Array.isArray(m)) {
      flat.push(...flattenMessages(m))
    } else if (m == null) {
      // skip
    } else {
      flat.push(String(m))
    }
  }
  return flat
}

const FormilyFormItemInner = ({
  children,
  className,
}: PropsWithChildren<{ className?: string }>) => {
  const field = useField() as unknown as AnyField
  const fieldId = `workrecord-${field.address.toString().split('.').join('-')}`
  const messages = flattenMessages(field.selfErrors)

  return (
    <div className={cn('grid gap-2', className)} data-testid='form-item'>
      <Label htmlFor={fieldId}>
        {String(field.title ?? '')}
        {field.required ? (
          <span className='ml-1 text-destructive' aria-hidden='true'>
            *
          </span>
        ) : null}
      </Label>

      <div id={fieldId}>{children}</div>

      {field.description ? (
        <p className='text-xs text-muted-foreground'>
          {String(field.description)}
        </p>
      ) : null}

      {messages.map((error) => (
        <p
          key={error}
          role='alert'
          className='text-xs text-destructive'
        >
          {error}
        </p>
      ))}
    </div>
  )
}

export const FormilyFormItem = observer(FormilyFormItemInner)
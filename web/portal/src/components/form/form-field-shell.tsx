// 同一文件同时导出组件（FormFieldShell / FormErrorSummary）和工具
// （focusFirstInvalidField），遵循 Phase 16 设计稿约定。
/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from 'react'
import { AlertCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

export function FormFieldShell({
  id,
  label,
  required = false,
  hint,
  error,
  children,
  className,
}: {
  id: string
  label: ReactNode
  required?: boolean
  hint?: ReactNode
  error?: string
  children: ReactNode
  className?: string
}) {
  const errorId = `${id}-error`
  const hintId = `${id}-hint`

  return (
    <div className={cn('grid gap-1.5', className)}>
      <label htmlFor={id} className='text-sm font-medium'>
        {label}
        {required ? (
          <span className='ml-1 text-destructive' aria-hidden='true'>
            *
          </span>
        ) : null}
      </label>

      <div data-field-error={Boolean(error) || undefined} data-field-id={id}>
        {children}
      </div>

      {error ? (
        <p
          id={errorId}
          className='flex items-start gap-1 text-sm text-destructive'
          role='alert'
        >
          <AlertCircle className='mt-0.5 size-4 shrink-0' />
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className='text-xs text-muted-foreground'>
          {hint}
        </p>
      ) : null}
    </div>
  )
}

export function FormErrorSummary({
  errors,
}: {
  errors: Record<string, string>
}) {
  const entries = Object.entries(errors)
  if (!entries.length) return null

  return (
    <div
      className='rounded-md border border-destructive/30 bg-destructive/5 p-3'
      role='alert'
    >
      <div className='font-medium text-destructive'>请修正以下内容</div>

      <ul className='mt-2 list-disc space-y-1 pl-5 text-sm text-destructive'>
        {entries.map(([field, message]) => (
          <li key={field}>{message}</li>
        ))}
      </ul>
    </div>
  )
}

export function focusFirstInvalidField() {
  const target = document.querySelector<HTMLElement>(
    '[data-field-error="true"] input, ' +
      '[data-field-error="true"] textarea, ' +
      '[data-field-error="true"] select, ' +
      '[data-field-error="true"] button'
  )

  target?.focus()
  target?.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
  })
}

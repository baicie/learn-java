import { useId } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { SetterProps } from './types'

export function TextSetter({
  value,
  onChange,
  label,
  description,
  disabled,
  required,
}: SetterProps<string>) {
  const id = useId()
  return (
    <div className='grid gap-1.5'>
      {label ? <Label htmlFor={id}>{label}</Label> : null}
      <Input
        id={id}
        value={value ?? ''}
        disabled={disabled}
        aria-required={required || undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {description ? (
        <p className='text-xs text-muted-foreground'>{description}</p>
      ) : null}
    </div>
  )
}

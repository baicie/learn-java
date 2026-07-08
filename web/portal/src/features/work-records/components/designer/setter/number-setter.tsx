import { useId } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { SetterProps } from './types'

type NumericLike = number | null

function parse(input: string): NumericLike {
  if (input.trim() === '') return null
  const parsed = Number(input)
  return Number.isFinite(parsed) ? parsed : null
}

export function NumberSetter({
  value,
  onChange,
  label,
  description,
  disabled,
  required,
}: SetterProps<NumericLike>) {
  const id = useId()
  return (
    <div className='grid gap-1.5'>
      {label ? <Label htmlFor={id}>{label}</Label> : null}
      <Input
        id={id}
        type='number'
        inputMode='numeric'
        value={value === null || value === undefined ? '' : String(value)}
        disabled={disabled}
        aria-required={required || undefined}
        onChange={(event) => onChange(parse(event.target.value))}
      />
      {description ? (
        <p className='text-xs text-muted-foreground'>{description}</p>
      ) : null}
    </div>
  )
}

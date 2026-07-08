import { useId } from 'react'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { SetterProps } from './types'

export type SelectOption = { label: string; value: string }

export function SelectSetter({
  value,
  onChange,
  label,
  description,
  disabled,
  required,
  options,
  placeholder = '—',
}: SetterProps<string | null> & {
  options: ReadonlyArray<SelectOption>
  placeholder?: string
}) {
  const id = useId()
  return (
    <div className='grid gap-1.5'>
      {label ? <Label htmlFor={id}>{label}</Label> : null}
      <Select
        value={value ?? ''}
        disabled={disabled}
        onValueChange={(next) => onChange(next || null)}
      >
        <SelectTrigger id={id} aria-required={required || undefined}>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {description ? (
        <p className='text-xs text-muted-foreground'>{description}</p>
      ) : null}
    </div>
  )
}

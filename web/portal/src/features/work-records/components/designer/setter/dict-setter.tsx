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

export type DictSetterProps = SetterProps<string | null> & {
  dictCodes: ReadonlyArray<string>
  disabled?: boolean
  required?: boolean
  placeholder?: string
}

export function DictSetter({
  value,
  onChange,
  dictCodes,
  label,
  description,
  disabled,
  required,
  placeholder = '—',
}: DictSetterProps) {
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
          {dictCodes.length === 0 ? (
            <SelectItem value='__empty__' disabled>
              暂无可用字典
            </SelectItem>
          ) : (
            dictCodes.map((code) => (
              <SelectItem key={code} value={code}>
                {code}
              </SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
      {description ? (
        <p className='text-xs text-muted-foreground'>{description}</p>
      ) : null}
    </div>
  )
}

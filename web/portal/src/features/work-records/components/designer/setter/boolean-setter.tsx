import { useId } from 'react'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import type { SetterProps } from './types'

export function BooleanSetter({
  value,
  onChange,
  label,
  description,
  disabled,
}: SetterProps<boolean>) {
  const id = useId()
  return (
    <div className='flex items-center justify-between gap-3'>
      <div className='grid gap-1.5'>
        {label ? <Label htmlFor={id}>{label}</Label> : null}
        {description ? (
          <p className='text-xs text-muted-foreground'>{description}</p>
        ) : null}
      </div>
      <Switch
        id={id}
        checked={Boolean(value)}
        disabled={disabled}
        onCheckedChange={(checked) => onChange(Boolean(checked))}
      />
    </div>
  )
}

import { useField } from '@formily/react'
import type { Field } from '@formily/core'
import { useWorkRecordFormRuntime } from '../context'
import { FormilySelectImpl } from './select'

export function FormilyUserSelect() {
  const field = useField<Field>()
  const runtime = useWorkRecordFormRuntime()
  const value = field.value == null ? '' : String(field.value)
  const options = runtime.userOptions.map((option) => ({
    value: option.value,
    label: option.label,
    disabled: !option.enabled,
  }))

  return (
    <div className='grid gap-1'>
      <FormilySelectImpl
        value={value}
        options={options}
        placeholder='请选择用户'
        disabled={field.disabled || field.readOnly}
        onChange={(next) => field.onInput(next)}
      />
      <p
        className='text-xs text-muted-foreground'
        data-testid='formily-user-select-options'
      >
        {runtime.userOptions.map((option) => option.label).join('、')}
      </p>
    </div>
  )
}
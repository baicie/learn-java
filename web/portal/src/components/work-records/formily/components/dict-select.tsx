import type { Field } from '@formily/core'
import { useField } from '@formily/react'
import { useWorkRecordFormRuntime } from '../context'
import { FormilySelectImpl } from './select'

export function FormilyDictSelect({
  dictCode,
  placeholder,
}: {
  dictCode: string
  placeholder?: string
}) {
  const field = useField<Field>()
  const runtime = useWorkRecordFormRuntime()
  const source = runtime.dictionaryOptions[dictCode] ?? []
  const currentValue = field.value == null ? '' : String(field.value)

  const options = source
    .filter(
      (item) =>
        item.enabled ||
        runtime.mode === 'readonly' ||
        (runtime.mode === 'edit' && item.value === currentValue)
    )
    .map((item) => {
      const isCurrent = item.value === currentValue
      const enabledInMode =
        item.enabled ||
        (runtime.mode === 'edit' && isCurrent) ||
        runtime.mode === 'readonly'

      return {
        value: item.value,
        label: item.enabled
          ? item.label
          : `${item.label}${runtime.disabledSuffix}`,
        disabled: !enabledInMode,
      }
    })

  return (
    <FormilySelectImpl
      value={currentValue}
      options={options}
      placeholder={placeholder}
      disabled={field.disabled || field.readOnly}
      onChange={(value) => field.onInput(value)}
    />
  )
}

import { useCallback } from 'react'

export type SetterProps<TValue> = {
  value: TValue
  onChange: (next: TValue) => void
  label?: string
  description?: string
  disabled?: boolean
  required?: boolean
}

export type SetterComponent<TValue> = (
  props: SetterProps<TValue>
) => React.ReactNode

export function useCommit<TValue>(
  onChange: (next: TValue) => void,
  transform?: (next: TValue) => TValue
) {
  return useCallback(
    (next: TValue) => {
      onChange(transform ? transform(next) : next)
    },
    [onChange, transform]
  )
}

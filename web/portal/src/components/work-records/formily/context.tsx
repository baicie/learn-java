import { createContext, useContext } from 'react'

export type RuntimeOption = {
  value: string
  label: string
  enabled: boolean
}

export type WorkRecordFormRuntimeMode =
  'create' | 'edit' | 'readonly' | 'designer'

export type WorkRecordFormRuntimeContextValue = {
  mode: WorkRecordFormRuntimeMode
  dictionaryOptions: Record<string, RuntimeOption[]>
  userOptions: RuntimeOption[]
  disabledSuffix: string
}

const Context = createContext<WorkRecordFormRuntimeContextValue | null>(null)

export const WorkRecordFormRuntimeProvider = Context.Provider

export function useWorkRecordFormRuntime(): WorkRecordFormRuntimeContextValue {
  const value = useContext(Context)

  if (!value) {
    throw new Error('WorkRecordFormRuntimeProvider is required')
  }

  return value
}

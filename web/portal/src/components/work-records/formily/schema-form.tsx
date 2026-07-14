import { useMemo } from 'react'
import type { ISchema } from '@formily/json-schema'
import { FormProvider } from '@formily/react'
import { Button } from '@/components/ui/button'
import {
  type RuntimeOption,
  type WorkRecordFormRuntimeMode,
  WorkRecordFormRuntimeProvider,
} from './context'
import { createWorkRecordForm } from './create-form'
import { WorkRecordSchemaField } from './schema-field'

export type WorkRecordSchemaFormProps = {
  schema: ISchema
  mode: WorkRecordFormRuntimeMode
  initialValues?: Record<string, unknown>
  dictionaryOptions: Record<string, RuntimeOption[]>
  userOptions?: RuntimeOption[]
  onSubmit?: (values: Record<string, unknown>) => Promise<void> | void
  footer?: React.ReactNode
}

export function WorkRecordSchemaForm({
  schema,
  mode,
  initialValues,
  dictionaryOptions,
  userOptions = [],
  onSubmit,
  footer,
}: WorkRecordSchemaFormProps) {
  const initialSignature = JSON.stringify(initialValues ?? {})

  const form = useMemo(
    () => createWorkRecordForm({ mode, initialValues }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [mode, initialSignature]
  )

  return (
    <WorkRecordFormRuntimeProvider
      value={{
        mode,
        dictionaryOptions,
        userOptions,
        disabledSuffix: '（已禁用）',
      }}
    >
      <FormProvider form={form}>
        <WorkRecordSchemaField schema={schema} />

        {footer ??
          (mode !== 'readonly' && mode !== 'designer' ? (
            <Button
              type='button'
              onClick={() => {
                void form.submit(onSubmit).catch(() => undefined)
              }}
              data-testid='formily-submit'
            >
              提交
            </Button>
          ) : null)}
      </FormProvider>
    </WorkRecordFormRuntimeProvider>
  )
}

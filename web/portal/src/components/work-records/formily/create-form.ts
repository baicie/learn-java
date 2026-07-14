import { createForm } from '@formily/core'

type WorkRecordFormMode = 'create' | 'edit' | 'readonly' | 'designer'

export type CreateWorkRecordFormOptions = {
  mode: WorkRecordFormMode
  initialValues?: Record<string, unknown>
}

/**
 * Build a Formily form tuned for the work-record runtime. `readPretty` is
 * enabled in readonly mode so the schema renders labels instead of inputs.
 */
export function createWorkRecordForm(options: CreateWorkRecordFormOptions) {
  return createForm({
    readPretty: options.mode === 'readonly',
    initialValues: options.initialValues ?? {},
    validateFirst: true,
  })
}

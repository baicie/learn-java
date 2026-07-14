import type { ISchema } from '@formily/json-schema'
import { FormProvider } from '@formily/react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordFormRuntimeProvider } from '../context'
import { createWorkRecordForm } from '../create-form'
import { WorkRecordSchemaField } from '../schema-field'

const wrap = (ui: React.ReactNode, mode: 'create' | 'readonly' = 'create') => (
  <WorkRecordFormRuntimeProvider
    value={{
      mode,
      dictionaryOptions: {},
      userOptions: [
        { value: 'u1', label: 'Alice', enabled: true },
        { value: 'u2', label: 'Bob (离职)', enabled: false },
      ],
      disabledSuffix: '（已禁用）',
    }}
  >
    <FormProvider form={createWorkRecordForm({ mode })}>{ui}</FormProvider>
  </WorkRecordFormRuntimeProvider>
)

describe('FormilyUserSelect', () => {
  const schema: ISchema = {
    type: 'object',
    properties: {
      owner: {
        type: 'string',
        title: '负责人',
        'x-decorator': 'FormItem',
        'x-component': 'UserSelect',
      },
    },
  }

  it('renders user options from runtime context', async () => {
    const screen = await render(wrap(<WorkRecordSchemaField schema={schema} />))
    await expect.element(screen.getByText('Alice')).toBeVisible()
  })
})

import type { ISchema } from '@formily/json-schema'
import { FormProvider } from '@formily/react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordFormRuntimeProvider } from '../context'
import { createWorkRecordForm } from '../create-form'
import { WorkRecordSchemaField } from '../schema-field'

const wrap = (
  ui: React.ReactNode,
  options?: {
    mode?: 'create' | 'edit' | 'readonly'
    dictOptions?: Record<
      string,
      { value: string; label: string; enabled: boolean }[]
    >
    initialValues?: Record<string, unknown>
  }
) => (
  <WorkRecordFormRuntimeProvider
    value={{
      mode: options?.mode ?? 'create',
      dictionaryOptions: options?.dictOptions ?? {},
      userOptions: [],
      disabledSuffix: '（已禁用）',
    }}
  >
    <FormProvider
      form={createWorkRecordForm({
        mode: options?.mode ?? 'create',
        initialValues: options?.initialValues,
      })}
    >
      {ui}
    </FormProvider>
  </WorkRecordFormRuntimeProvider>
)

describe('FormilyDictSelect', () => {
  const schema: ISchema = {
    type: 'object',
    properties: {
      priority: {
        type: 'string',
        title: '优先级',
        'x-decorator': 'FormItem',
        'x-component': 'DictSelect',
        'x-component-props': { dictCode: 'priority' },
      },
    },
  }

  it('shows disabled label with suffix when value is disabled', async () => {
    const screen = await render(
      wrap(<WorkRecordSchemaField schema={schema} />, {
        mode: 'readonly',
        initialValues: { priority: 'P2' },
        dictOptions: {
          priority: [{ value: 'P2', label: '中', enabled: false }],
        },
      })
    )
    await expect.element(screen.getByText('中（已禁用）')).toBeVisible()
  })

  it('preserves historical disabled value in edit mode', async () => {
    const screen = await render(
      wrap(<WorkRecordSchemaField schema={schema} />, {
        mode: 'edit',
        initialValues: { priority: 'P2' },
        dictOptions: {
          priority: [
            { value: 'P1', label: '高', enabled: true },
            { value: 'P2', label: '中', enabled: false },
          ],
        },
      })
    )
    await expect.element(screen.getByText('中（已禁用）')).toBeVisible()
  })
})

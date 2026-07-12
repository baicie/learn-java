import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { FormProvider } from '@formily/react'
import { createWorkRecordForm } from '../create-form'
import { WorkRecordFormRuntimeProvider } from '../context'
import { WorkRecordSchemaField } from '../schema-field'
import type { ISchema } from '@formily/json-schema'

const withProvider = (ui: React.ReactNode, mode: 'create' | 'readonly' = 'create') => (
  <WorkRecordFormRuntimeProvider
    value={{
      mode,
      dictionaryOptions: {},
      userOptions: [],
      disabledSuffix: '（已禁用）',
    }}
  >
    <FormProvider form={createWorkRecordForm({ mode })}>{ui}</FormProvider>
  </WorkRecordFormRuntimeProvider>
)

describe('Formily boolean / date / multi-select / section / grid', () => {
  it('renders a boolean field as a Checkbox', async () => {
    const schema: ISchema = {
      type: 'object',
      properties: {
        flag: {
          type: 'boolean',
          title: '启用',
          'x-decorator': 'FormItem',
          'x-component': 'Boolean',
          required: true,
        },
      },
    }
    const screen = await render(withProvider(<WorkRecordSchemaField schema={schema} />))
    await expect.element(screen.getByTestId('form-item')).toBeVisible()
    await expect
      .element(screen.getByTestId('formily-boolean-flag'))
      .toBeVisible()
  })

  it('renders a date field as an HTML date input', async () => {
    const schema: ISchema = {
      type: 'object',
      properties: {
        day: {
          type: 'string',
          format: 'date',
          title: '日期',
          'x-decorator': 'FormItem',
          'x-component': 'DatePicker',
          required: true,
        },
      },
    }
    const screen = await render(withProvider(<WorkRecordSchemaField schema={schema} />))
    await expect
      .element(screen.getByTestId('formily-date-day'))
      .toBeVisible()
    const input = screen.getByTestId('formily-date-day').element() as HTMLInputElement
    expect(input.type).toBe('date')
  })

  it('renders a multi-select field with options', async () => {
    const schema: ISchema = {
      type: 'object',
      properties: {
        tags: {
          type: 'array',
          title: '标签',
          'x-decorator': 'FormItem',
          'x-component': 'MultiSelect',
          'x-component-props': {
            options: [
              { label: '运维', value: 'ops' },
              { label: '开发', value: 'dev' },
            ],
          },
        },
      },
    }
    const screen = await render(withProvider(<WorkRecordSchemaField schema={schema} />))
    await expect
      .element(screen.getByTestId('formily-multi-select-ops'))
      .toBeVisible()
  })

  it('renders a Section + Grid layout', async () => {
    const schema: ISchema = {
      type: 'object',
      properties: {
        wrapper: {
          type: 'void',
          'x-component': 'Section',
          'x-component-props': { title: '基本信息' },
          properties: {
            grid: {
              type: 'void',
              'x-component': 'Grid',
              'x-component-props': { columns: 2 },
              properties: {
                name: {
                  type: 'string',
                  title: '姓名',
                  'x-decorator': 'FormItem',
                  'x-component': 'Input',
                  required: true,
                },
              },
            },
          },
        },
      },
    }
    const screen = await render(withProvider(<WorkRecordSchemaField schema={schema} />))
    await expect.element(screen.getByText('基本信息')).toBeVisible()
    await expect.element(screen.getByTestId('form-item')).toBeVisible()
  })
})
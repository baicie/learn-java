import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordSchemaForm } from './schema-form'
import type { ISchema } from '@formily/json-schema'
import type { RuntimeOption } from './context'

const requiredTextSchema: ISchema = {
  type: 'object',
  properties: {
    summary: {
      type: 'string',
      title: '工作总结',
      'x-decorator': 'FormItem',
      'x-component': 'Textarea',
      'x-validator': [{ required: true, message: '请输入工作总结' }],
    },
    hours: {
      type: 'number',
      title: '工作时长',
      'x-decorator': 'FormItem',
      'x-component': 'NumberInput',
    },
  },
}

const setNativeValue = (element: HTMLInputElement | HTMLTextAreaElement, value: string) => {
  const proto = element instanceof HTMLTextAreaElement
    ? window.HTMLTextAreaElement.prototype
    : window.HTMLInputElement.prototype
  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set
  setter?.call(element, value)
  element.dispatchEvent(new Event('input', { bubbles: true }))
  element.dispatchEvent(new Event('change', { bubbles: true }))
}

describe('WorkRecordSchemaForm', () => {
  it('renders Shadcn Formily controls and submits typed values', async () => {
    const onSubmit = vi.fn()
    const screen = await render(
      <WorkRecordSchemaForm
        schema={requiredTextSchema}
        mode='create'
        dictionaryOptions={{}}
        onSubmit={onSubmit}
      />
    )

    const textarea = screen
      .getByTestId('formily-textarea-summary')
      .element() as HTMLTextAreaElement
    setNativeValue(textarea, '完成重构')

    const hours = screen
      .getByTestId('formily-input-hours')
      .element() as HTMLInputElement
    setNativeValue(hours, '8')

    ;(screen.getByTestId('formily-submit').element() as HTMLButtonElement).click()

    await expect.poll(() => onSubmit.mock.calls.length).toBe(1)
    expect(onSubmit).toHaveBeenCalledWith({
      summary: '完成重构',
      hours: 8,
    })
  })

  it('shows required error when submitting empty form', async () => {
    const screen = await render(
      <WorkRecordSchemaForm
        schema={requiredTextSchema}
        mode='create'
        dictionaryOptions={{}}
        onSubmit={vi.fn()}
      />
    )
    await (screen.getByTestId('formily-submit').element() as HTMLButtonElement).click()
    await expect
      .element(screen.getByText('请输入工作总结'))
      .toBeVisible()
  })

  it('renders disabled historical dictionary label in readonly mode', async () => {
    const dictSchema: ISchema = {
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
    const dictionaryOptions: Record<string, RuntimeOption[]> = {
      priority: [{ value: 'P2', label: '中', enabled: false }],
    }
    const screen = await render(
      <WorkRecordSchemaForm
        schema={dictSchema}
        mode='readonly'
        initialValues={{ priority: 'P2' }}
        dictionaryOptions={dictionaryOptions}
      />
    )
    await expect.element(screen.getByText('中（已禁用）')).toBeVisible()
  })
})
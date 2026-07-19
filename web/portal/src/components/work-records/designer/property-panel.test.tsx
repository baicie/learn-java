import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { PropertyPanel } from './property-panel'
import type { DesignerField } from './types'

const field: DesignerField = {
  id: 'field-1',
  fieldName: '优先级',
  fieldCode: 'priority',
  fieldType: 'select',
  required: false,
  optionSource: 'dict',
  dictCode: 'priority',
  listVisible: true,
  filterable: true,
  exportable: true,
  statistical: false,
  sortOrder: 0,
  enabled: true,
  locked: true,
  referenced: true,
}

describe('PropertyPanel', () => {
  it('locks published field code and type', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel field={field} dictTypes={[]} onChange={vi.fn()} />
      </I18nextProvider>
    )

    await expect
      .element(screen.getByRole('textbox', { name: '字段编码' }))
      .toHaveAttribute('readonly')

    await expect
      .element(screen.getByRole('combobox', { name: '字段类型' }))
      .toBeDisabled()
  })

  it('updates exportable flag', async () => {
    const onChange = vi.fn()

    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel field={field} dictTypes={[]} onChange={onChange} />
      </I18nextProvider>
    )

    await screen.getByRole('checkbox', { name: '可导出' }).click()

    expect(onChange).toHaveBeenCalledWith('field-1', {
      exportable: false,
    })
  })

  it('does not show a validation warning for a valid editable field code', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel
          field={{ ...field, locked: false, referenced: false }}
          dictTypes={[]}
          onChange={vi.fn()}
        />
      </I18nextProvider>
    )

    await expect.element(screen.getByRole('alert')).not.toBeInTheDocument()
    await expect
      .element(
        screen.getByText('规则：字母开头，仅支持字母、数字和下划线，最长 64 位')
      )
      .toBeVisible()
  })

  it('only shows option source for select fields', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel
          field={{
            ...field,
            fieldType: 'text',
            optionSource: 'static',
            dictCode: '',
          }}
          dictTypes={[]}
          onChange={vi.fn()}
        />
      </I18nextProvider>
    )

    await expect.element(screen.getByText('选项来源')).not.toBeInTheDocument()
  })

  it('updates field width and text validation rules', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel
          field={{
            ...field,
            locked: false,
            fieldType: 'text',
            optionSource: 'static',
            dictCode: '',
            columnSpan: 2,
            validation: {},
          }}
          dictTypes={[]}
          onChange={onChange}
        />
      </I18nextProvider>
    )

    await screen.getByRole('combobox', { name: '字段宽度' }).click()
    await screen.getByRole('option', { name: '半宽（一行两个）' }).click()
    expect(onChange).toHaveBeenCalledWith('field-1', { columnSpan: 1 })

    await screen.getByRole('spinbutton', { name: '最小长度' }).fill('3')
    expect(onChange).toHaveBeenCalledWith('field-1', {
      validation: { minLength: 3 },
    })
  })

  it('edits static options for select fields', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel
          field={{
            ...field,
            locked: false,
            optionSource: 'static',
            dictCode: '',
            staticOptions: ['P0'],
          }}
          dictTypes={[]}
          onChange={onChange}
        />
      </I18nextProvider>
    )

    await screen.getByRole('textbox', { name: '静态选项' }).fill('P0\nP1')
    expect(onChange).toHaveBeenCalledWith('field-1', {
      staticOptions: ['P0', 'P1'],
    })
  })

  it('shows select as a placeholder instead of a dictionary option', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <PropertyPanel
          field={{ ...field, dictCode: '' }}
          dictTypes={[
            {
              id: 'dict-1',
              dictCode: 'record_priority',
              dictName: '处理优先级',
              enabled: true,
            },
          ]}
          onChange={vi.fn()}
        />
      </I18nextProvider>
    )

    const dictionary = screen
      .getByText('字典编码')
      .element()
      .closest('label')
      ?.querySelector<HTMLElement>('[role="combobox"]')
    expect(dictionary?.textContent).toContain('请选择')
    await userEvent.click(dictionary!)
    await expect
      .element(screen.getByRole('option', { name: '请选择' }))
      .not.toBeInTheDocument()
    await expect
      .element(
        screen.getByRole('option', {
          name: '处理优先级 / record_priority',
        })
      )
      .toBeVisible()
  })
})

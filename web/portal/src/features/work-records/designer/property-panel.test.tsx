import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { i18n } from '@/i18n'
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
})

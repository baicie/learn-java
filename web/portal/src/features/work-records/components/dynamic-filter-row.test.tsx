import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import type { DynamicFilter, RecordListFilterField } from '../data/schema'
import { DynamicFilterRow } from './dynamic-filter-row'

const fields: RecordListFilterField[] = [
  {
    fieldCode: 'memo',
    label: '备注',
    fieldType: 'text',
    operators: ['eq', 'contains', 'exists'],
    dictionaryCode: null,
    options: [],
  },
  {
    fieldCode: 'env',
    label: '环境',
    fieldType: 'select',
    operators: ['eq', 'in', 'exists'],
    dictionaryCode: 'env',
    options: [
      { value: 'prod', label: '生产' },
      { value: 'test', label: '测试' },
    ],
  },
]

describe('DynamicFilterRow', () => {
  const renderRow = (
    filter: DynamicFilter,
    onChange = vi.fn(),
    onRemove = vi.fn()
  ) =>
    render(
      <I18nextProvider i18n={i18n}>
        <DynamicFilterRow
          filter={filter}
          filterFields={fields}
          onChange={onChange}
          onRemove={onRemove}
        />
      </I18nextProvider>
    )

  it('renders field label', async () => {
    const filter: DynamicFilter = {
      fieldCode: 'memo',
      operator: 'contains',
      value: 'hello',
      values: null,
    }
    const { getByText } = await renderRow(filter)

    await expect.element(getByText('备注')).toBeInTheDocument()
  })

  it('calls onRemove when remove button clicked', async () => {
    const filter: DynamicFilter = {
      fieldCode: 'memo',
      operator: 'contains',
      value: 'hello',
      values: null,
    }
    const onRemove = vi.fn()
    const { getByRole } = await renderRow(filter, vi.fn(), onRemove)

    const removeBtn = getByRole('button', { name: '' })
    await userEvent.click(removeBtn)

    expect(onRemove).toHaveBeenCalled()
  })

  it('renders text input for contains operator', async () => {
    const filter: DynamicFilter = {
      fieldCode: 'memo',
      operator: 'contains',
      value: 'hello',
      values: null,
    }
    const { getByRole } = await renderRow(filter)

    await expect.element(getByRole('textbox')).toBeInTheDocument()
  })
})

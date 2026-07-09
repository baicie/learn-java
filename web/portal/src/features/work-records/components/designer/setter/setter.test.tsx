import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { BooleanSetter } from './boolean-setter'
import { DictSetter } from './dict-setter'
import { NumberSetter } from './number-setter'
import { SelectSetter } from './select-setter'
import { TextSetter } from './text-setter'

function withI18n(node: React.ReactNode) {
  return <I18nextProvider i18n={i18n}>{node}</I18nextProvider>
}

describe('TextSetter', () => {
  it('renders the current value and emits onChange', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(
        <TextSetter
          label='Field Name'
          value='process_result'
          onChange={onChange}
        />
      )
    )

    const input = screen.getByLabelText('Field Name')
    await expect.element(input).toHaveValue('process_result')
    await userEvent.fill(input, 'new_value')
    expect(onChange).toHaveBeenCalledWith('new_value')
  })
})

describe('NumberSetter', () => {
  it('parses numeric input and emits onChange', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(<NumberSetter label='Latency' value={120} onChange={onChange} />)
    )

    const input = screen.getByRole('spinbutton')
    await expect.element(input).toHaveValue(120)
    await userEvent.fill(input, '42')
    expect(onChange).toHaveBeenCalledWith(42)
  })

  it('emits null when clearing the input', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(<NumberSetter label='Latency' value={42} onChange={onChange} />)
    )
    const input = screen.getByRole('spinbutton')
    await userEvent.clear(input)
    expect(onChange).toHaveBeenLastCalledWith(null)
  })

  it('returns the input as-is for typed', () => {
    expect(Number('120')).toBe(120)
    // We do not exercise non-numeric fill because <input type="number"> disallows it.
  })
})

describe('BooleanSetter', () => {
  it('renders and reflects value', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(<BooleanSetter label='Resolved' value onChange={onChange} />)
    )
    const toggle = screen.getByRole('switch')
    await expect.element(toggle).toBeChecked()
  })
})

describe('SelectSetter', () => {
  it('emits the picked option value', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(
        <SelectSetter
          label='Priority'
          value={null}
          onChange={onChange}
          options={[
            { label: 'P0', value: 'P0' },
            { label: 'P1', value: 'P1' },
          ]}
        />
      )
    )
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByText('P1'))
    expect(onChange).toHaveBeenCalledWith('P1')
  })

  it('emits null when the placeholder is picked', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(
        <SelectSetter
          label='Priority'
          value='P0'
          onChange={onChange}
          options={[
            { label: '—', value: '' },
            { label: 'P0', value: 'P0' },
            { label: 'P1', value: 'P1' },
          ]}
        />
      )
    )
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByText('—'))
    expect(onChange).toHaveBeenLastCalledWith(null)
  })
})

describe('DictSetter', () => {
  it('renders options and emits dict code', async () => {
    const onChange = vi.fn()
    const screen = await render(
      withI18n(
        <DictSetter
          label='Dictionary'
          value={null}
          onChange={onChange}
          dictCodes={['record_priority', 'record_env']}
        />
      )
    )
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByText('record_env'))
    expect(onChange).toHaveBeenCalledWith('record_env')
  })

  // designer-design §14.4: previously hardcoded "暂无可用字典";
  // now driven by i18n key workRecords.designer.dict.empty.
  it('shows the i18n-driven empty placeholder when no dict codes are provided', async () => {
    const screen = await render(
      withI18n(
        <DictSetter
          label='Dictionary'
          value={null}
          onChange={vi.fn()}
          dictCodes={[]}
        />
      )
    )
    await userEvent.click(screen.getByRole('combobox'))
    const empty = screen.getByText(i18n.t('workRecords.designer.dict.empty'))
    await expect.element(empty).toBeInTheDocument()
  })
})

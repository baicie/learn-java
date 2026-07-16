import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { TemplateSwitchSelect } from './template-switch-select'

function withProviders(node: React.ReactNode) {
  return render(
    <I18nextProvider i18n={i18n} defaultNS='translation'>
      <ConfirmProvider>{node}</ConfirmProvider>
    </I18nextProvider>
  )
}

describe('TemplateSwitchSelect', () => {
  it('asks for confirmation when dynamic fields contain values', async () => {
    const onChange = vi.fn()

    const screen = await withProviders(
      <TemplateSwitchSelect
        value='tpl-1'
        hasDynamicValues
        templates={[
          { id: 'tpl-1', name: '日报' },
          { id: 'tpl-2', name: '周报' },
        ]}
        onChange={onChange}
      />
    )

    await screen.getByRole('combobox').click()
    await screen.getByRole('option', { name: '周报' }).click()

    await expect.element(screen.getByText('切换记录模板')).toBeVisible()

    expect(onChange).not.toHaveBeenCalled()

    await screen.getByRole('button', { name: '清空并切换' }).click()

    expect(onChange).toHaveBeenCalledWith('tpl-2')
  })

  it('switches immediately when dynamic fields are empty', async () => {
    const onChange = vi.fn()

    const screen = await withProviders(
      <TemplateSwitchSelect
        value='tpl-1'
        hasDynamicValues={false}
        templates={[
          { id: 'tpl-1', name: '日报' },
          { id: 'tpl-2', name: '周报' },
        ]}
        onChange={onChange}
      />
    )

    await screen.getByRole('combobox').click()
    await screen.getByRole('option', { name: '周报' }).click()

    expect(onChange).toHaveBeenCalledWith('tpl-2')
  })
})

import { I18nProvider } from '@/i18n/provider'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { TemplateSwitchSelect } from './template-switch-select'

describe('TemplateSwitchSelect', () => {
  it('asks for confirmation when dirty', async () => {
    const onChange = vi.fn()

    const screen = await render(
      <I18nProvider>
        <ConfirmProvider>
          <TemplateSwitchSelect
            value='tpl-1'
            dirty
            templates={[
              { id: 'tpl-1', name: '日报' },
              { id: 'tpl-2', name: '周报' },
            ]}
            onChange={onChange}
          />
        </ConfirmProvider>
      </I18nProvider>
    )

    await screen.getByRole('combobox').selectOptions('tpl-2')

    await expect.element(screen.getByText('切换记录模板')).toBeVisible()

    expect(onChange).not.toHaveBeenCalled()

    await screen.getByRole('button', { name: '清空并切换' }).click()

    expect(onChange).toHaveBeenCalledWith('tpl-2')
  })
})

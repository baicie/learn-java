import { I18nProvider } from '@/i18n/provider'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { LockedFieldCodeInput } from './locked-field-code-input'

describe('LockedFieldCodeInput', () => {
  it('renders readonly field after lock', async () => {
    const screen = await render(
      <I18nProvider>
        <LockedFieldCodeInput value='summary' locked onChange={vi.fn()} />
      </I18nProvider>
    )

    await expect
      .element(screen.getByRole('textbox'))
      .toHaveAttribute('readonly')

    await expect.element(screen.getByLabelText('字段编码已锁定')).toBeVisible()
  })
})

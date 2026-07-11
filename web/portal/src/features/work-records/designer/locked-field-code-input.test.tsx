import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { LockedFieldCodeInput } from './locked-field-code-input'

describe('LockedFieldCodeInput', () => {
  it('renders readonly field after lock', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <LockedFieldCodeInput value='summary' locked onChange={vi.fn()} />
      </I18nextProvider>
    )

    await expect
      .element(screen.getByRole('textbox'))
      .toHaveAttribute('readonly')

    await expect.element(screen.getByLabelText('字段编码已锁定')).toBeVisible()
  })

  it('injects aria attributes from FormFieldShell', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <LockedFieldCodeInput
          value='summary'
          locked={false}
          error='字段编码已被占用'
          onChange={vi.fn()}
        />
      </I18nextProvider>
    )

    const input = screen.getByRole('textbox').element() as HTMLInputElement
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(input.getAttribute('aria-describedby')).toContain('-error')
  })
})

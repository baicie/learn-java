import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { PlatformUserToolbar } from './platform-user-toolbar'

describe('PlatformUserToolbar', () => {
  it('emits keyword change on Enter', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <PlatformUserToolbar
        value={{ page: 1, pageSize: 20 }}
        onChange={onChange}
      />
    )
    const input = screen
      .getByTestId('user-keyword-input')
      .element() as HTMLInputElement
    input.focus()
    input.value = 'alice'
    input.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })
    )
    expect(onChange).toHaveBeenCalled()
  })

  it('emits status change via select', async () => {
    const onChange = vi.fn()
    const screen = await render(
      <PlatformUserToolbar
        value={{ page: 1, pageSize: 20, status: 'active' }}
        onChange={onChange}
      />
    )
    const select = screen.getByTestId('user-status-select')
    await expect.element(select).toBeVisible()
    await expect.element(select).toHaveTextContent('正常')
  })
})

import { render } from 'vitest-browser-react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmProvider, useConfirm } from '@/components/feedback/confirm-provider'

function Consumer({ onResult }: { onResult: (result: boolean) => void }) {
  const confirm = useConfirm()
  return (
    <button
      type='button'
      onClick={async () => {
        const r = await confirm({
          title: 't',
          description: 'd',
          confirmationText: 'YES',
        })
        onResult(r)
      }}
    >
      open
    </button>
  )
}

describe('useConfirm with confirmationText', () => {
  it('keeps confirm button disabled until the user types the right value', async () => {
    const onResult = vi.fn()
    const screen = await render(
      <ConfirmProvider>
        <Consumer onResult={onResult} />
      </ConfirmProvider>
    )
    await screen.getByText('open').click()
    const confirmButton = screen
      .getByRole('button', { name: '确认' })
      .element() as HTMLButtonElement
    expect(confirmButton.disabled).toBe(true)

    const input = screen.getByTestId('confirm-input').element() as HTMLInputElement
    const nativeInputValue = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype,
      'value'
    )?.set
    nativeInputValue?.call(input, 'NO')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    expect(confirmButton.disabled).toBe(true)

    nativeInputValue?.call(input, 'YES')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    expect(confirmButton.disabled).toBe(false)
  })
})
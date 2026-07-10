import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider, useConfirm } from './confirm-provider'

function Harness() {
  const confirm = useConfirm()
  const [result, setResult] = useState('pending')

  return (
    <>
      <button
        type='button'
        onClick={async () => {
          const accepted = await confirm({
            title: '删除记录',
            description: '删除后不可恢复',
            confirmText: '确认删除',
            variant: 'destructive',
          })

          setResult(accepted ? 'confirmed' : 'cancelled')
        }}
      >
        打开
      </button>

      <span>{result}</span>
    </>
  )
}

describe('ConfirmProvider', () => {
  it('resolves true after confirm', async () => {
    const screen = await render(
      <ConfirmProvider>
        <Harness />
      </ConfirmProvider>
    )

    await screen.getByRole('button', { name: '打开' }).click()

    await expect.element(screen.getByText('删除记录')).toBeVisible()

    await screen.getByRole('button', { name: '确认删除' }).click()

    await expect.element(screen.getByText('confirmed')).toBeVisible()
  })

  it('resolves false after cancel', async () => {
    const screen = await render(
      <ConfirmProvider>
        <Harness />
      </ConfirmProvider>
    )

    await screen.getByRole('button', { name: '打开' }).click()

    await screen.getByRole('button', { name: '取消' }).click()

    await expect.element(screen.getByText('cancelled')).toBeVisible()
  })
})

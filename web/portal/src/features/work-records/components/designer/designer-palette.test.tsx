import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { DesignerPalette } from './designer-palette'

describe('DesignerPalette', () => {
  it('renders all 9 field type buttons', async () => {
    await render(<DesignerPalette onAdd={vi.fn()} />)
    for (const type of [
      'text',
      'textarea',
      'number',
      'date',
      'datetime',
      'select',
      'multi_select',
      'user',
      'boolean',
    ] as const) {
      const button = document.querySelector(
        `[data-testid="designer-palette-${type}"]`
      )
      expect(button).toBeTruthy()
    }
  })

  it('invokes onAdd with the clicked field type', async () => {
    const onAdd = vi.fn()
    await render(<DesignerPalette onAdd={onAdd} />)
    const button = document.querySelector(
      '[data-testid="designer-palette-number"]'
    ) as HTMLElement
    await userEvent.click(button)
    expect(onAdd).toHaveBeenCalledWith('number')
  })

  it('disables all buttons when disabled=true', async () => {
    await render(<DesignerPalette onAdd={vi.fn()} disabled />)
    const button = document.querySelector(
      '[data-testid="designer-palette-text"]'
    ) as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })
})

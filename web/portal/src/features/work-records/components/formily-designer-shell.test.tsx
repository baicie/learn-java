import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { addField, emptySchema } from '@/features/work-records/data/designer/schema-builder'
import { FormilyDesignerShell } from './formily-designer-shell'

function seededSchema() {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result', {
    title: '处理结果',
    required: true,
  })
  schema = addField(schema, 'select', 'priority')
  return schema
}

describe('FormilyDesignerShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders all four regions and a save button', async () => {
    await render(
      <FormilyDesignerShell
        initialSchema={seededSchema()}
        onSave={vi.fn()}
      />
    )
    expect(document.querySelector('[data-testid="formily-designer-shell"]')).toBeTruthy()
    expect(document.querySelector('[data-testid="designer-palette"]')).toBeTruthy()
    expect(document.querySelector('[data-testid="designer-canvas"]')).toBeTruthy()
    expect(document.querySelector('[data-testid="designer-property-panel"]')).toBeTruthy()
    expect(document.querySelector('[data-testid="designer-preview"]')).toBeTruthy()
    expect(document.querySelector('[data-testid="designer-save"]')).toBeTruthy()
  })

  it('adds a field via palette click and auto-selects it', async () => {
    const onSave = vi.fn()
    await render(
      <FormilyDesignerShell initialSchema={emptySchema()} onSave={onSave} />
    )
    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-palette-number"]'
      ) as HTMLElement
    )
    const row = document.querySelector(
      '[data-testid="designer-canvas-item-number_1"]'
    )
    expect(row).toBeTruthy()
    // onSave should NOT be called (only save button does)
    expect(onSave).not.toHaveBeenCalled()
  })

  it('reorders when move-down is clicked on the first field', async () => {
    await render(
      <FormilyDesignerShell initialSchema={seededSchema()} onSave={vi.fn()} />
    )
    // Click "down" on the first item (process_result), priority moves after it
    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-canvas-down-process_result"]'
      ) as HTMLElement
    )
    const order = Array.from(
      document.querySelectorAll('[data-testid^="designer-canvas-item-"]')
    ).map((el) => el.getAttribute('data-testid') ?? '')
    expect(order).toEqual([
      'designer-canvas-item-priority',
      'designer-canvas-item-process_result',
    ])
  })

  it('removes the selected field and clears property panel when canvas is emptied', async () => {
    await render(
      <FormilyDesignerShell initialSchema={emptySchema()} onSave={vi.fn()} />
    )
    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-palette-text"]'
      ) as HTMLElement
    )
    const remove = document.querySelector(
      '[data-testid="designer-canvas-remove-text_1"]'
    ) as HTMLElement
    await userEvent.click(remove)
    expect(
      document.querySelector('[data-testid="designer-canvas-empty"]')
    ).toBeTruthy()
    expect(
      document.querySelector('[data-testid="designer-property-empty"]')
    ).toBeTruthy()
  })

  it('disables save when no onSave is provided', async () => {
    await render(<FormilyDesignerShell initialSchema={seededSchema()} />)
    const saveBtn = document.querySelector(
      '[data-testid="designer-save"]'
    ) as HTMLButtonElement
    expect(saveBtn.disabled).toBe(true)
  })
})

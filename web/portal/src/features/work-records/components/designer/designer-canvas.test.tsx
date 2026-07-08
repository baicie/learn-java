import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import {
  addField,
  emptySchema,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'
import { DesignerCanvas } from './designer-canvas'

function buildSchema(): DesignerSchema {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result')
  schema = addField(schema, 'number', 'severity')
  return schema
}

describe('DesignerCanvas', () => {
  it('renders the empty hint when schema is empty', async () => {
    await render(
      <DesignerCanvas
        schema={emptySchema()}
        selectedFieldCode={null}
        onSelect={vi.fn()}
        onMove={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    const empty = document.querySelector(
      '[data-testid="designer-canvas-empty"]'
    )
    expect(empty).toBeTruthy()
  })

  it('renders one row per field with code, type and required marker', async () => {
    const schema = buildSchema()
    await render(
      <DesignerCanvas
        schema={schema}
        selectedFieldCode={null}
        onSelect={vi.fn()}
        onMove={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    for (const code of Object.keys(schema.properties)) {
      const row = document.querySelector(
        `[data-testid="designer-canvas-item-${code}"]`
      )
      expect(row).toBeTruthy()
    }
  })

  it('fires onSelect when a row is clicked', async () => {
    const onSelect = vi.fn()
    const schema = buildSchema()
    await render(
      <DesignerCanvas
        schema={schema}
        selectedFieldCode={null}
        onSelect={onSelect}
        onMove={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    const row = document.querySelector(
      '[data-testid="designer-canvas-item-severity"]'
    ) as HTMLElement
    await userEvent.click(row)
    expect(onSelect).toHaveBeenCalledWith('severity')
  })

  it('fires onMove with direction up/down', async () => {
    const onMove = vi.fn()
    const schema = buildSchema()
    await render(
      <DesignerCanvas
        schema={schema}
        selectedFieldCode={null}
        onSelect={vi.fn()}
        onMove={onMove}
        onRemove={vi.fn()}
      />
    )
    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-canvas-down-process_result"]'
      ) as HTMLElement
    )
    expect(onMove).toHaveBeenCalledWith('process_result', 'down')

    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-canvas-up-severity"]'
      ) as HTMLElement
    )
    expect(onMove).toHaveBeenCalledWith('severity', 'up')
  })

  it('fires onRemove', async () => {
    const onRemove = vi.fn()
    const schema = buildSchema()
    await render(
      <DesignerCanvas
        schema={schema}
        selectedFieldCode={null}
        onSelect={vi.fn()}
        onMove={vi.fn()}
        onRemove={onRemove}
      />
    )
    await userEvent.click(
      document.querySelector(
        '[data-testid="designer-canvas-remove-severity"]'
      ) as HTMLElement
    )
    expect(onRemove).toHaveBeenCalledWith('severity')
  })

  it('disables up on the first row and down on the last row', async () => {
    const schema = buildSchema()
    await render(
      <DesignerCanvas
        schema={schema}
        selectedFieldCode={null}
        onSelect={vi.fn()}
        onMove={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    const up = document.querySelector(
      '[data-testid="designer-canvas-up-process_result"]'
    ) as HTMLButtonElement
    const down = document.querySelector(
      '[data-testid="designer-canvas-down-severity"]'
    ) as HTMLButtonElement
    expect(up.disabled).toBe(true)
    expect(down.disabled).toBe(true)
  })
})

import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  addField,
  emptySchema,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'
import { DesignerPreview } from './designer-preview'

function build(): DesignerSchema {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result', { title: '处理结果' })
  schema = addField(schema, 'select', 'priority', {
    options: [{ label: 'P0', value: 'P0' }],
  })
  return schema
}

describe('DesignerPreview', () => {
  it('shows the empty hint when there are no fields', async () => {
    await render(<DesignerPreview schema={emptySchema()} />)
    const empty = document.querySelector(
      '[data-testid="designer-preview-empty"]'
    )
    expect(empty).toBeTruthy()
  })

  it('renders an input/select per configured field', async () => {
    const container = document.createElement('div')
    document.body.appendChild(container)
    await render(<DesignerPreview schema={build()} />, { container })
    const preview = document.querySelector(
      '[data-testid="designer-preview"]'
    )
    expect(preview).toBeTruthy()
    expect(
      preview?.querySelectorAll('input, button[role="combobox"]').length ?? 0
    ).toBeGreaterThan(0)
  })
})
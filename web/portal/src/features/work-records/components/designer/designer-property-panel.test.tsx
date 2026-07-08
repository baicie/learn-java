import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import {
  addField,
  emptySchema,
  setDictionaryCode,
  type DesignerSchema,
  type UpdateFieldPatch,
} from '@/features/work-records/data/designer/schema-builder'
import { DesignerPropertyPanel } from './designer-property-panel'

function buildSchema(): DesignerSchema {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result', { listVisible: true })
  schema = addField(schema, 'select', 'priority')
  schema = setDictionaryCode(schema, 'priority', 'record_priority')
  return schema
}

describe('DesignerPropertyPanel', () => {
  it('renders empty state when nothing is selected', async () => {
    await render(
      <DesignerPropertyPanel
        schema={buildSchema()}
        selectedFieldCode={null}
        onUpdate={vi.fn()}
        dictCodes={['record_priority', 'record_env']}
      />
    )
    const empty = document.querySelector(
      '[data-testid="designer-property-empty"]'
    )
    expect(empty).toBeTruthy()
  })

  it('emits a title patch when title input changes', async () => {
    const onUpdate = vi.fn<(current: string, patch: UpdateFieldPatch) => void>()
    const screen = await render(
      <DesignerPropertyPanel
        schema={buildSchema()}
        selectedFieldCode='process_result'
        onUpdate={onUpdate}
        dictCodes={[]}
      />
    )
    const titleInput = screen.getByLabelText('标题')
    await userEvent.fill(titleInput, '处理结果')
    expect(onUpdate).toHaveBeenCalledWith('process_result', {
      title: '处理结果',
    })
  })

  it('reflects listVisible / filterable / statistical and toggles them', async () => {
    const onUpdate = vi.fn<(current: string, patch: UpdateFieldPatch) => void>()
    await render(
      <DesignerPropertyPanel
        schema={buildSchema()}
        selectedFieldCode='process_result'
        onUpdate={onUpdate}
        dictCodes={[]}
      />
    )
    const filterable = document.querySelector(
      '[data-testid="designer-property-filterable"]'
    ) as HTMLElement
    await userEvent.click(filterable)
    expect(onUpdate).toHaveBeenCalledWith('process_result', {
      filterable: true,
    })
  })

  it('renders dict setter for select fields bound to a dictionary', async () => {
    await render(
      <DesignerPropertyPanel
        schema={buildSchema()}
        selectedFieldCode='priority'
        onUpdate={vi.fn()}
        dictCodes={['record_priority', 'record_env']}
      />
    )
    // combobox appears for the dict setter
    const combos = document.querySelectorAll('[role="combobox"]')
    expect(combos.length).toBeGreaterThan(0)
  })

  it('blocks dictCode switch on non-select fields', async () => {
    const schema = emptySchema()
    const filled = addField(schema, 'text', 'note')
    expect(() =>
      setDictionaryCode(filled, 'note', 'record_note')
    ).toThrow(/select or multi_select/)
  })
})

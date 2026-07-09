import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { FieldLibrary } from './field-library'
import { FormCanvas } from './form-canvas'
import { PropertyPanel } from './property-panel'
import { newDesignerField } from './schema'
import type { DesignerField } from './types'

describe('work record designer components', () => {
  it('renders field library', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <FieldLibrary onAdd={vi.fn()} />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('字段库')).toBeVisible()
    await expect.element(screen.getByText('单行文本')).toBeVisible()
  })

  it('renders canvas field', async () => {
    const field: DesignerField = {
      ...newDesignerField('text', 0),
      fieldName: '标题',
      fieldCode: 'title_text',
    }

    const screen = await render(
      <FormCanvas
        fields={[field]}
        selectedFieldId={field.id}
        onSelect={vi.fn()}
        onMove={vi.fn()}
        onDuplicate={vi.fn()}
        onRemoveOrDisable={vi.fn()}
      />
    )

    await expect.element(screen.getByText('标题')).toBeVisible()
    await expect.element(screen.getByText('title_text')).toBeVisible()
  })

  it('locks field code input for published fields', async () => {
    const field: DesignerField = {
      ...newDesignerField('text', 0),
      fieldName: '标题',
      fieldCode: 'title_text',
      locked: true,
    }

    const screen = await render(
      <PropertyPanel field={field} dictTypes={[]} onChange={vi.fn()} />
    )

    await expect
      .element(screen.getByText('该字段已发布，字段编码不可修改'))
      .toBeVisible()
  })
})

import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordDesigner } from './designer'

vi.mock('@/components/work-records/designer/work-record-designer-page', () => ({
  WorkRecordDesignerPage: ({ templateId }: { templateId: string }) => (
    <div>designer:{templateId}</div>
  ),
}))

describe('WorkRecordDesigner', () => {
  it('passes the route template id to the designer', async () => {
    const screen = await render(<WorkRecordDesigner templateId='tpl-21' />)
    await expect.element(screen.getByText('designer:tpl-21')).toBeVisible()
  })
})

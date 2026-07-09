import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordFeatureFrozen } from './work-record-feature-frozen'

describe('WorkRecordFeatureFrozen', () => {
  it('renders frozen baseline message for list page', async () => {
    render(<WorkRecordFeatureFrozen surface='list' />)

    const title = document.querySelector('h2, [class*="CardTitle"]')
    expect(title?.textContent).toContain('工作记录模块重做中')
    expect(document.body.textContent).toMatch(/记录列表/)
    expect(document.body.textContent).toMatch(/Phase 0/)
  })

  it('renders frozen baseline message for designer page', async () => {
    render(<WorkRecordFeatureFrozen surface='designer' />)

    expect(document.body.textContent).toMatch(/工作记录模块重做中/)
    expect(document.body.textContent).toMatch(/表单设计/)
  })
})

import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordFeatureFrozen } from './work-record-feature-frozen'

describe('WorkRecordFeatureFrozen', () => {
  it('renders frozen baseline message for list page', async () => {
    await render(<WorkRecordFeatureFrozen surface='list' />)

    expect(document.body.textContent).toContain('工作记录模块重做中')
    expect(document.body.textContent).toContain('记录列表')
    expect(document.body.textContent).toContain('Phase 0')
    expect(document.body.textContent).toContain('不可用的 record 实现')
  })

  it('renders frozen baseline message for designer page', async () => {
    await render(<WorkRecordFeatureFrozen surface='designer' />)

    expect(document.body.textContent).toContain('工作记录模块重做中')
    expect(document.body.textContent).toContain('表单设计')
    expect(document.body.textContent).toContain('enterprise roadmap')
  })
})

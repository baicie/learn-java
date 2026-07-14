import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { RecordHistoryCard } from './record-history-card'

describe('RecordHistoryCard', () => {
  it('renders record changes with before/after values', async () => {
    const screen = await render(
      <RecordHistoryCard
        events={[
          {
            id: 'a1',
            tenantId: 't1',
            actorId: 'u1',
            action: 'work_record.record.update',
            resourceType: 'work_record',
            resourceId: 'r1',
            beforeJson: '{"title":"旧标题"}',
            afterJson: '{"title":"新标题"}',
            detailJson: JSON.stringify({
              changes: [
                {
                  path: '/title',
                  beforeValue: '旧标题',
                  afterValue: '新标题',
                },
              ],
              changesTruncated: false,
            }),
            createdAt: '2026-07-10T10:00:00+08:00',
          },
        ]}
      />
    )

    await expect.element(screen.getByText('变更历史')).toBeVisible()
    await expect.element(screen.getByText('编辑记录')).toBeVisible()
    await expect.element(screen.getByText('旧标题')).toBeVisible()
    await expect.element(screen.getByText('新标题')).toBeVisible()
    await expect.element(screen.getByText('操作人：u1')).toBeVisible()
  })

  it('renders empty state when no events', async () => {
    const screen = await render(<RecordHistoryCard events={[]} />)

    await expect.element(screen.getByText('暂无变更记录')).toBeVisible()
  })

  it('renders loading state', async () => {
    const screen = await render(<RecordHistoryCard events={[]} loading />)

    await expect.element(screen.getByText('正在加载变更历史...')).toBeVisible()
  })

  it('surfaces changesTruncated warning when diff exceeds limit', async () => {
    const screen = await render(
      <RecordHistoryCard
        events={[
          {
            id: 'a1',
            tenantId: 't1',
            actorId: 'u1',
            action: 'work_record.record.update',
            resourceType: 'work_record',
            resourceId: 'r1',
            beforeJson: '{}',
            afterJson: '{}',
            detailJson: JSON.stringify({
              changes: [{ path: '/title', beforeValue: 'a', afterValue: 'b' }],
              changesTruncated: true,
            }),
            createdAt: '2026-07-10T10:00:00+08:00',
          },
        ]}
      />
    )

    await expect
      .element(screen.getByText('变更项过多，仅展示前 200 项。'))
      .toBeVisible()
  })
})

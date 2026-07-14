import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkdaySummaryCard } from './workday-summary-card'

describe('WorkdaySummaryCard', () => {
  it('renders monthly workday count', async () => {
    const screen = await render(
      <WorkdaySummaryCard
        loading={false}
        summary={{
          calendarId: 'cal1',
          calendarName: '中国大陆 2026 工作日历',
          timeZone: 'Asia/Shanghai',
          month: '2026-07',
          periodStart: '2026-07-01',
          periodEnd: '2026-07-31',
          workdayCount: 23,
          firstWorkday: '2026-07-01',
          lastWorkday: '2026-07-31',
        }}
      />
    )

    await expect.element(screen.getByText('2026-07 工作月')).toBeVisible()

    await expect.element(screen.getByText('23 天')).toBeVisible()

    await expect
      .element(screen.getByText('中国大陆 2026 工作日历'))
      .toBeVisible()
  })

  it('renders calendar configuration error', async () => {
    const screen = await render(
      <WorkdaySummaryCard
        loading={false}
        error={new Error('default work calendar is not configured')}
      />
    )

    await expect
      .element(screen.getByText(/default work calendar is not configured/))
      .toBeVisible()
  })
})

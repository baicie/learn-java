import { describe, expect, it } from 'vitest'
import { buildDashboardMetrics } from './dashboard-metrics'

describe('buildDashboardMetrics', () => {
  it('turns live API data into useful workbench metrics', () => {
    expect(
      buildDashboardMetrics({
        recordTotal: 18,
        recentStatuses: ['draft', 'done', 'draft'],
        templateStatuses: ['published', 'draft', 'disabled'],
        enabledCalendarCount: 2,
        workdayCount: 23,
      })
    ).toEqual({
      recordTotal: 18,
      draftCount: 2,
      publishedTemplateCount: 1,
      enabledCalendarCount: 2,
      workdayCount: 23,
    })
  })
})

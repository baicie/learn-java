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
      alertTotal: 0,
      openAlertCount: 0,
      incidentTotal: 0,
      openIncidentCount: 0,
    })
  })

  it('summarizes open alerts and incidents for the AIOps workbench', () => {
    expect(
      buildDashboardMetrics({
        recordTotal: 0,
        recentStatuses: [],
        templateStatuses: [],
        enabledCalendarCount: 0,
        workdayCount: 0,
        alertTotal: 12,
        openAlertCount: 4,
        incidentTotal: 3,
        openIncidentCount: 1,
      })
    ).toMatchObject({
      alertTotal: 12,
      openAlertCount: 4,
      incidentTotal: 3,
      openIncidentCount: 1,
    })
  })
})

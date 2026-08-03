export function buildDashboardMetrics(input: {
  recordTotal: number
  recentStatuses: string[]
  templateStatuses: string[]
  enabledCalendarCount: number
  workdayCount: number
  alertTotal?: number
  openAlertCount?: number
  incidentTotal?: number
  openIncidentCount?: number
}) {
  return {
    recordTotal: input.recordTotal,
    draftCount: input.recentStatuses.filter((status) => status === 'draft')
      .length,
    publishedTemplateCount: input.templateStatuses.filter(
      (status) => status === 'published'
    ).length,
    enabledCalendarCount: input.enabledCalendarCount,
    workdayCount: input.workdayCount,
    alertTotal: input.alertTotal ?? 0,
    openAlertCount: input.openAlertCount ?? 0,
    incidentTotal: input.incidentTotal ?? 0,
    openIncidentCount: input.openIncidentCount ?? 0,
  }
}

export function buildDashboardMetrics(input: {
  recordTotal: number
  recentStatuses: string[]
  templateStatuses: string[]
  enabledCalendarCount: number
  workdayCount: number
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
  }
}

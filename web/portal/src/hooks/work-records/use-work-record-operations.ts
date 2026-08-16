import { queryOptions, useQueries } from '@tanstack/react-query'
import {
  canGenerateWorkRecordPeriodReports,
  canReadWorkRecordPeriodReports,
} from '@/auth/work-record-access'
import {
  getStatistics,
  getWorkload,
  listHandovers,
  listMonthlyAiGenerations,
  listWeeklyAiGenerations,
  listPendingApprovalTasks,
  listMarketPackages,
} from '@/api/work-records/extensions'
import { useAuthStore } from '@/stores/auth-store'
import { startOfIsoWeek, toCalendarDate } from '@/lib/date-format'
import { getAiGenerationRefetchInterval } from './ai-generation-polling'

export function useWorkRecordOperations(from: string, to: string) {
  const weekStart = startOfIsoWeek(from)
  const month = toCalendarDate(from).slice(0, 7)
  const principal = useAuthStore((state) => state.auth.principal)
  const permissions = principal?.permissions ?? []
  const tenantId = principal?.tenantId ?? null
  const canAnalyze = permissions.includes('work-record:analytics')
  const canHandover = permissions.includes('work-record:handover')
  const canGeneratePeriodReports = canGenerateWorkRecordPeriodReports(principal)
  const canReadPeriodReports = canReadWorkRecordPeriodReports(principal)
  const canApprove = permissions.includes('work-record:approval:act')
  const [
    statistics,
    workload,
    handovers,
    market,
    weeklyReports,
    monthlyReports,
    approvals,
  ] = useQueries({
    queries: [
      {
        queryKey: ['work-record-statistics', tenantId, from, to],
        queryFn: () => getStatistics(from, to),
        enabled: canAnalyze,
      },
      {
        queryKey: ['work-record-workload', tenantId, from, to],
        queryFn: () => getWorkload(from, to),
        enabled: canAnalyze,
      },
      {
        queryKey: ['work-record-handovers', tenantId],
        queryFn: listHandovers,
        enabled: canHandover,
      },
      {
        queryKey: ['work-record-market', tenantId],
        queryFn: listMarketPackages,
        enabled: tenantId !== null,
      },
      queryOptions({
        queryKey: ['work-record-weekly-ai', tenantId, weekStart],
        queryFn: () => listWeeklyAiGenerations(weekStart),
        enabled: canReadPeriodReports,
        refetchInterval: (query) =>
          getAiGenerationRefetchInterval(query.state.data),
      }),
      queryOptions({
        queryKey: ['work-record-monthly-ai', tenantId, month],
        queryFn: () => listMonthlyAiGenerations(month),
        enabled: canReadPeriodReports,
        refetchInterval: (query) =>
          getAiGenerationRefetchInterval(query.state.data),
      }),
      {
        queryKey: ['work-record-approval-tasks', tenantId],
        queryFn: listPendingApprovalTasks,
        enabled: canApprove,
      },
    ],
  })
  return {
    statistics,
    workload,
    handovers,
    market,
    weeklyReports,
    monthlyReports,
    approvals,
    canReadPeriodReports,
    canGeneratePeriodReports,
  }
}

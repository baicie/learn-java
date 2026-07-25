import { queryOptions, useQueries } from '@tanstack/react-query'
import {
  getStatistics,
  getWorkload,
  listHandovers,
  listMonthlyAiGenerations,
  listPendingApprovalTasks,
  listMarketPackages,
} from '@/api/work-records/extensions'
import { useAuthStore } from '@/stores/auth-store'
import { getAiGenerationRefetchInterval } from './ai-generation-polling'

const EMPTY_PERMISSIONS: string[] = []

export function useWorkRecordOperations(from: string, to: string) {
  const permissions = useAuthStore(
    (state) => state.auth.principal?.permissions ?? EMPTY_PERMISSIONS
  )
  const canAnalyze = permissions.includes('work-record:analytics')
  const canHandover = permissions.includes('work-record:handover')
  const canGenerate = permissions.includes('work-record:ai:generate')
  const canGenerateMonthly =
    canGenerate && permissions.includes('work-record:read:all')
  const canApprove = permissions.includes('work-record:approval:act')
  const [statistics, workload, handovers, market, monthlyReports, approvals] =
    useQueries({
      queries: [
        {
          queryKey: ['work-record-statistics', from, to],
          queryFn: () => getStatistics(from, to),
          enabled: canAnalyze,
        },
        {
          queryKey: ['work-record-workload', from, to],
          queryFn: () => getWorkload(from, to),
          enabled: canAnalyze,
        },
        {
          queryKey: ['work-record-handovers'],
          queryFn: listHandovers,
          enabled: canHandover,
        },
        { queryKey: ['work-record-market'], queryFn: listMarketPackages },
        queryOptions({
          queryKey: ['work-record-monthly-ai', from.slice(0, 7)],
          queryFn: () => listMonthlyAiGenerations(from),
          enabled: canGenerateMonthly,
          refetchInterval: (query) =>
            getAiGenerationRefetchInterval(query.state.data),
        }),
        {
          queryKey: ['work-record-approval-tasks'],
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
    monthlyReports,
    approvals,
  }
}

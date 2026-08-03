import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  analyzeIncidentRca,
  closeIncident,
  diagnoseIncident,
  generateIncidentReport,
  getIncident,
  getLatestAiDiagnosis,
  getLatestIncidentReport,
  getLatestRca,
  listAlerts,
  listIncidentEvidence,
  listIncidents,
  resolveIncident,
  type AnalyzeIncidentRcaInput,
  type DiagnoseIncidentInput,
  type GenerateIncidentReportInput,
} from '@/api/operations/operations-api'
import { operationsKeys } from '@/api/operations/query-keys'
import { useAuthStore } from '@/stores/auth-store'

function useOperationsTenantId() {
  return useAuthStore((state) => state.auth.principal?.tenantId ?? null)
}

function alertsQueryOptions(tenantId: string | null, enabled = true) {
  return queryOptions({
    queryKey: operationsKeys.alerts(tenantId),
    queryFn: listAlerts,
    enabled: Boolean(tenantId) && enabled,
  })
}

function incidentsQueryOptions(tenantId: string | null, enabled = true) {
  return queryOptions({
    queryKey: operationsKeys.incidents(tenantId),
    queryFn: listIncidents,
    enabled: Boolean(tenantId) && enabled,
  })
}

function incidentQueryOptions(tenantId: string | null, id: string) {
  return queryOptions({
    queryKey: operationsKeys.incident(tenantId, id),
    queryFn: () => getIncident(id),
    enabled: Boolean(tenantId) && Boolean(id),
  })
}

function incidentEvidenceQueryOptions(tenantId: string | null, id: string) {
  return queryOptions({
    queryKey: operationsKeys.incidentEvidence(tenantId, id),
    queryFn: () => listIncidentEvidence(id),
    enabled: Boolean(tenantId) && Boolean(id),
  })
}

function incidentRcaQueryOptions(tenantId: string | null, id: string) {
  return queryOptions({
    queryKey: operationsKeys.incidentRca(tenantId, id),
    queryFn: () => getLatestRca(id),
    enabled: Boolean(tenantId) && Boolean(id),
  })
}

function incidentAiDiagnosisQueryOptions(tenantId: string | null, id: string) {
  return queryOptions({
    queryKey: operationsKeys.incidentAiDiagnosis(tenantId, id),
    queryFn: () => getLatestAiDiagnosis(id),
    enabled: Boolean(tenantId) && Boolean(id),
  })
}

function incidentReportQueryOptions(tenantId: string | null, id: string) {
  return queryOptions({
    queryKey: operationsKeys.incidentReport(tenantId, id),
    queryFn: () => getLatestIncidentReport(id),
    enabled: Boolean(tenantId) && Boolean(id),
  })
}

export function useAlerts(enabled = true) {
  const tenantId = useOperationsTenantId()
  return useQuery(alertsQueryOptions(tenantId, enabled))
}

export function useIncidents(enabled = true) {
  const tenantId = useOperationsTenantId()
  return useQuery(incidentsQueryOptions(tenantId, enabled))
}

function useIncident(tenantId: string | null, id: string) {
  return useQuery(incidentQueryOptions(tenantId, id))
}

function useIncidentEvidence(tenantId: string | null, id: string) {
  return useQuery(incidentEvidenceQueryOptions(tenantId, id))
}

function useLatestIncidentRca(tenantId: string | null, id: string) {
  return useQuery(incidentRcaQueryOptions(tenantId, id))
}

function useLatestIncidentAiDiagnosis(tenantId: string | null, id: string) {
  return useQuery(incidentAiDiagnosisQueryOptions(tenantId, id))
}

function useLatestIncidentReport(tenantId: string | null, id: string) {
  return useQuery(incidentReportQueryOptions(tenantId, id))
}

export function useIncidentOperationsDetail(id: string) {
  const tenantId = useOperationsTenantId()
  const detail = useIncident(tenantId, id)
  const evidence = useIncidentEvidence(tenantId, id)
  const rca = useLatestIncidentRca(tenantId, id)
  const aiDiagnosis = useLatestIncidentAiDiagnosis(tenantId, id)
  const report = useLatestIncidentReport(tenantId, id)
  const queries = [detail, evidence, rca, aiDiagnosis, report]

  return {
    detail,
    evidence,
    rca,
    aiDiagnosis,
    report,
    isPending: queries.some((query) => query.isPending),
    isFetching: queries.some((query) => query.isFetching),
  }
}

function useInvalidateIncident(id: string) {
  const queryClient = useQueryClient()
  const tenantId = useOperationsTenantId()
  return (includeList = false) => {
    const invalidations = [
      queryClient.invalidateQueries({
        queryKey: operationsKeys.incident(tenantId, id),
      }),
    ]
    if (includeList) {
      invalidations.push(
        queryClient.invalidateQueries({
          exact: true,
          queryKey: operationsKeys.incidents(tenantId),
        })
      )
    }
    return Promise.all(invalidations)
  }
}

export function useAnalyzeIncidentRca(id: string) {
  const invalidate = useInvalidateIncident(id)
  return useMutation({
    mutationFn: (input: AnalyzeIncidentRcaInput) =>
      analyzeIncidentRca(id, input),
    onSuccess: () => invalidate(),
  })
}

export function useDiagnoseIncident(id: string) {
  const invalidate = useInvalidateIncident(id)
  return useMutation({
    mutationFn: (input: DiagnoseIncidentInput) => diagnoseIncident(id, input),
    onSuccess: () => invalidate(),
  })
}

export function useGenerateIncidentReport(id: string) {
  const invalidate = useInvalidateIncident(id)
  return useMutation({
    mutationFn: (input: GenerateIncidentReportInput) =>
      generateIncidentReport(id, input),
    onSuccess: () => invalidate(),
  })
}

export function useResolveIncident(id: string) {
  const invalidate = useInvalidateIncident(id)
  return useMutation({
    mutationFn: () => resolveIncident(id),
    onSuccess: () => invalidate(true),
  })
}

export function useCloseIncident(id: string) {
  const invalidate = useInvalidateIncident(id)
  return useMutation({
    mutationFn: () => closeIncident(id),
    onSuccess: () => invalidate(true),
  })
}

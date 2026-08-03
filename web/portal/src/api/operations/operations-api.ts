import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiErrorCode } from '@/lib/api-error'
import { apiResponseSchema } from '@/lib/api-response'
import {
  aiDiagnosisSchema,
  alertEventSchema,
  incidentAlertSchema,
  incidentDetailSchema,
  incidentEvidenceSchema,
  incidentReportSchema,
  incidentSchema,
  incidentTimelineSchema,
  rcaAnalysisSchema,
} from '@/lib/operations/operations'

export type AnalyzeIncidentRcaInput = {
  force?: boolean
}

export type DiagnoseIncidentInput = {
  force?: boolean
  locale?: string
}

export type GenerateIncidentReportInput = DiagnoseIncidentInput & {
  createdBy?: string
}

function responseData<T extends z.ZodType>(
  schema: T,
  payload: unknown
): z.output<T> {
  const envelope = apiResponseSchema(z.unknown()).parse(payload)
  return schema.parse(envelope.data)
}

async function latestOrNull<T>(request: () => Promise<T>, missingCode: string) {
  try {
    return await request()
  } catch (error) {
    if (apiErrorCode(error) === missingCode) return null
    throw error
  }
}

export async function listAlerts() {
  const { data } = await apiClient.get('/api/alerts')
  return responseData(z.array(alertEventSchema), data)
}

export async function listIncidents() {
  const { data } = await apiClient.get('/api/incidents')
  return responseData(z.array(incidentSchema), data)
}

export async function getIncident(id: string) {
  const { data } = await apiClient.get(`/api/incidents/${id}`)
  return responseData(incidentDetailSchema, data)
}

export async function listIncidentAlerts(id: string) {
  const { data } = await apiClient.get(`/api/incidents/${id}/alerts`)
  return responseData(z.array(incidentAlertSchema), data)
}

export async function listIncidentTimeline(id: string) {
  const { data } = await apiClient.get(`/api/incidents/${id}/timeline`)
  return responseData(z.array(incidentTimelineSchema), data)
}

export async function listIncidentEvidence(id: string) {
  const { data } = await apiClient.get(`/api/incidents/${id}/evidence`)
  return responseData(z.array(incidentEvidenceSchema), data)
}

export async function getLatestRca(id: string) {
  return latestOrNull(async () => {
    const { data } = await apiClient.get(`/api/incidents/${id}/rca/latest`)
    return responseData(rcaAnalysisSchema, data)
  }, 'RCA_NOT_FOUND')
}

export async function getLatestAiDiagnosis(id: string) {
  return latestOrNull(async () => {
    const { data } = await apiClient.get(`/api/incidents/${id}/ai/latest`)
    return responseData(aiDiagnosisSchema, data)
  }, 'AI_DIAGNOSIS_NOT_FOUND')
}

export async function getLatestIncidentReport(id: string) {
  return latestOrNull(async () => {
    const { data } = await apiClient.get(`/api/incidents/${id}/reports/latest`)
    return responseData(incidentReportSchema, data)
  }, 'INCIDENT_REPORT_NOT_FOUND')
}

export async function analyzeIncidentRca(
  id: string,
  input: AnalyzeIncidentRcaInput = {}
) {
  const { data } = await apiClient.post(
    `/api/incidents/${id}/rca/analyze`,
    input
  )
  return responseData(rcaAnalysisSchema, data)
}

export async function diagnoseIncident(
  id: string,
  input: DiagnoseIncidentInput = {}
) {
  const { data } = await apiClient.post(
    `/api/incidents/${id}/ai/diagnose`,
    input
  )
  return responseData(aiDiagnosisSchema, data)
}

export async function generateIncidentReport(
  id: string,
  input: GenerateIncidentReportInput = {}
) {
  const { data } = await apiClient.post(`/api/incidents/${id}/reports`, input)
  return responseData(incidentReportSchema, data)
}

export async function resolveIncident(id: string) {
  const { data } = await apiClient.post(`/api/incidents/${id}/resolve`)
  return responseData(incidentSchema, data)
}

export async function closeIncident(id: string) {
  const { data } = await apiClient.post(`/api/incidents/${id}/close`)
  return responseData(incidentSchema, data)
}

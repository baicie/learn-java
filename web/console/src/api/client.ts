export type ApiResponse<T> = {
  success: boolean
  data: T
  errorCode?: string
  message?: string
  timestamp: string
}

export type LoginResponse = {
  token: string
  user: Me
}

export type Me = {
  id: string
  tenantId: string
  username: string
  displayName: string
  roles: string[]
}

export type DataSourceRecord = {
  id: string
  tenantId: string
  type: string
  name: string
  status: string
  createdAt: string
  updatedAt: string
  lastSyncAt?: string
}

export type CreateZabbixDataSourcePayload = {
  type: 'zabbix'
  name: string
  zabbix: {
    endpoint: string
    username?: string
    password?: string
    apiToken?: string
    connectTimeoutSeconds?: number
    readTimeoutSeconds?: number
  }
}

export type TestDataSourceResponse = {
  ok: boolean
  message: string
  version?: string
}

export type SyncDataSourceResponse = {
  runId: string
  status: string
  hostsCreated: number
  hostsUpdated: number
  alertsCreated: number
  alertsUpdated: number
  message: string
}

export type AssetRecord = {
  id: string
  tenantId: string
  assetType: string
  name: string
  displayName?: string
  source: string
  status: string
  createdAt: string
}

export type AlertEventRecord = {
  id: string
  tenantId: string
  source: string
  severity: string
  title: string
  status: string
  startsAt: string
  createdAt: string
}

export type IncidentRecord = {
  id: string
  tenantId: string
  title: string
  summary?: string
  severity: string
  status: string
  source: string
  primaryAssetId?: string
  aggregationKey?: string
  alertCount: number
  suspectedRootCause?: string
  confidence?: number
  startedAt: string
  detectedAt: string
  lastSeenAt?: string
  resolvedAt?: string
  createdAt: string
  updatedAt: string
}

export type IncidentAlertRecord = {
  id: string
  source: string
  sourceEventId?: string
  severity: string
  title: string
  status: string
  assetId?: string
  entityName?: string
  fingerprint: string
  startsAt: string
  relationType: string
}

export type IncidentTimelineRecord = {
  id: string
  eventTime: string
  eventType: string
  title: string
  description?: string
  source: string
  payloadJson: string
}

export type IncidentDetailRecord = {
  incident: IncidentRecord
  alerts: IncidentAlertRecord[]
  timeline: IncidentTimelineRecord[]
}

export type IncidentAggregationResponse = {
  alertsScanned: number
  groups: number
  incidentsCreated: number
  incidentsUpdated: number
  alertsLinked: number
}

export type RcaEvidence = {
  ruleId: string
  title: string
  description: string
  score: number
  confidence: number
  attributes: Record<string, unknown>
}

export type RcaAnalysisResponse = {
  id: string
  incidentId: string
  status: string
  suspectedRootCause: string
  confidence: number
  summary: string
  evidence: RcaEvidence[]
  matchedRules?: string[]
  evidenceRefs?: string[]
  modelVersion: string
  createdAt: string
}

const TOKEN_KEY = 'aegisops_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const resp = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
  })

  const payload = await parseApiResponse<T>(resp)
  if (!resp.ok || !payload.success) {
    throw new Error(
      payload.message || payload.errorCode || `Request failed with status ${resp.status}`,
    )
  }
  return payload.data
}

async function parseApiResponse<T>(resp: Response): Promise<ApiResponse<T>> {
  const contentType = resp.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    const text = await resp.text()
    return {
      success: false,
      data: undefined as T,
      errorCode: `HTTP_${resp.status}`,
      message: text || resp.statusText || 'Non-JSON response',
      timestamp: new Date().toISOString(),
    }
  }
  return (await resp.json()) as ApiResponse<T>
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function me() {
  return apiRequest<Me>('/api/auth/me')
}

export function overview() {
  return apiRequest<Record<string, number | string>>('/api/system/overview')
}

export function listDataSources() {
  return apiRequest<DataSourceRecord[]>('/api/datasources')
}

export function createZabbixDataSource(payload: CreateZabbixDataSourcePayload) {
  return apiRequest<DataSourceRecord>('/api/datasources', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function testDataSource(id: string) {
  return apiRequest<TestDataSourceResponse>(`/api/datasources/${id}/test`, { method: 'POST' })
}

export function syncDataSource(id: string) {
  return apiRequest<SyncDataSourceResponse>(`/api/datasources/${id}/sync`, { method: 'POST' })
}

export function listAssets() {
  return apiRequest<AssetRecord[]>('/api/assets')
}

export function listAlerts() {
  return apiRequest<AlertEventRecord[]>('/api/alerts')
}

export function listIncidents() {
  return apiRequest<IncidentRecord[]>('/api/incidents')
}

export function aggregateIncidents() {
  return apiRequest<IncidentAggregationResponse>('/api/incidents/aggregate', {
    method: 'POST',
    body: JSON.stringify({
      windowMinutes: 1440,
      limit: 1000,
    }),
  })
}

export function getIncident(id: string) {
  return apiRequest<IncidentDetailRecord>(`/api/incidents/${id}`)
}

export function resolveIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/resolve`, { method: 'POST' })
}

export function closeIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/close`, { method: 'POST' })
}

export function analyzeIncidentRca(id: string, force = true) {
  return apiRequest<RcaAnalysisResponse>(`/api/incidents/${id}/rca/analyze`, {
    method: 'POST',
    body: JSON.stringify({ force }),
  })
}

export function getLatestIncidentRca(id: string) {
  return apiRequest<RcaAnalysisResponse>(`/api/incidents/${id}/rca/latest`)
}

export type AiDiagnosisResponse = {
  id: string
  incidentId: string
  status: string
  provider: string
  model: string
  agentName: string
  summary: string
  rootCause: string
  impact: string
  nextSteps: string[]
  runbookSuggestions: string[]
  risks: string[]
  matchedRules?: string[]
  evidenceRefs?: string[]
  timeline?: Array<Record<string, unknown>>
  raw?: Record<string, unknown>
  createdAt: string
}

export function diagnoseIncidentAi(id: string, force = true) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${id}/ai/diagnose`, {
    method: 'POST',
    body: JSON.stringify({ force, locale: 'zh-CN' }),
  })
}

export function getLatestIncidentAiDiagnosis(id: string) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${id}/ai/latest`)
}

// --- Z8: Evidence types ---

export type DiagnosisEvidenceRecord = {
  id: string
  incidentId?: string
  evidenceKey: string
  source?: string
  evidenceType: string
  title?: string
  summary?: string
  timeRangeStart?: string
  timeRangeEnd?: string
  confidence?: number
  payloadJson?: string
  createdAt?: string
}

export type EvidenceCollectResponse = {
  incidentId?: string
  itemsMatched?: number
  historyPoints?: number
  trendPoints?: number
  events?: number
  triggers?: number
  evidenceCreated?: number
  evidenceUpdated?: number
  collected?: number
  message?: string
}

// --- Z8: Incident report types ---

export type IncidentReportRecord = {
  id: string
  incidentId: string
  versionNo: number
  reportType?: string
  format?: string
  title?: string
  markdownContent: string
  snapshotJson?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

// --- Z8: Incident detail bundle ---
export type IncidentDetailBundle = {
  incident: IncidentRecord
  alerts: IncidentAlertRecord[]
  timeline: IncidentTimelineRecord[]
  evidence: DiagnosisEvidenceRecord[]
  rca?: RcaAnalysisResponse | null
  aiDiagnosis?: AiDiagnosisResponse | null
  report?: IncidentReportRecord | null
}

// --- Z8: API functions ---

export function listIncidentEvidence(incidentId: string) {
  return apiRequest<DiagnosisEvidenceRecord[]>(`/api/incidents/${incidentId}/evidence`)
}

export function collectIncidentEvidence(incidentId: string) {
  return apiRequest<EvidenceCollectResponse>(
    `/api/incidents/${incidentId}/evidence/zabbix/collect`,
    {
      method: 'POST',
      body: JSON.stringify({ lookbackMinutes: 30 }),
    },
  )
}

export function getIncidentAiDiagnosis(incidentId: string) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${incidentId}/ai/latest`)
}

export function runIncidentAiDiagnosis(incidentId: string, force = true) {
  return apiRequest<AiDiagnosisResponse>(`/api/incidents/${incidentId}/ai/diagnose`, {
    method: 'POST',
    body: JSON.stringify({ force, locale: 'zh-CN' }),
  })
}

export function getLatestReport(incidentId: string) {
  return apiRequest<IncidentReportRecord>(`/api/incidents/${incidentId}/reports/latest`)
}

export function generateReport(incidentId: string) {
  return apiRequest<IncidentReportRecord>(`/api/incidents/${incidentId}/reports`, {
    method: 'POST',
    body: JSON.stringify({ force: true, locale: 'zh-CN', createdBy: 'frontend' }),
  })
}

// --- Phase 1: Workbench ---
export type WorkbenchSummary = {
  activeIncidents: number
  criticalAlerts: number
  todayNewAlerts: number
  datasourceErrors: number
  pendingTasks: number
  moduleHealth: string
}

export function workbenchSummary() {
  return apiRequest<WorkbenchSummary>('/api/workbench/summary')
}

// --- Phase 1: Platform Modules ---
export type PlatformModuleRecord = {
  id: string
  moduleId: string
  name: string
  version: string
  enabled: boolean
  healthStatus: string
  configJson: string
  createdAt: string
}

export function listPlatformModules() {
  return apiRequest<PlatformModuleRecord[]>('/api/modules')
}

// --- Phase 1: Platform Users ---
export type PlatformUserRecord = {
  id: string
  tenantId: string
  username: string
  displayName: string
  email: string | null
  status: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

export function listPlatformUsers() {
  return apiRequest<PlatformUserRecord[]>('/api/users')
}

// --- Phase 01: Platform Navigation ---
export type PlatformMenuItem = {
  id: string
  moduleId: string
  parentId?: string | null
  path: string
  title: string
  icon?: string | null
  permissionCode?: string | null
  sortOrder: number
  enabled: boolean
  createdAt: string
}

export function listPlatformMenus() {
  return apiRequest<PlatformMenuItem[]>('/api/platform/navigation/menus')
}

export async function getIncidentBundle(incidentId: string): Promise<IncidentDetailBundle> {
  const [detail, evidence, rca, aiDiagnosis, report] = await Promise.all([
    getIncident(incidentId),
    listIncidentEvidence(incidentId).catch(() => []),
    getLatestIncidentRca(incidentId).catch(() => null),
    getIncidentAiDiagnosis(incidentId).catch(() => null),
    getLatestReport(incidentId).catch(() => null),
  ])
  return {
    ...detail,
    evidence,
    rca,
    aiDiagnosis,
    report,
  }
}

// --- Phase 2: Alert Ingest ---
export type AlertIngestPayload = {
  source: string
  sourceEventId?: string
  severity?: string
  title: string
  description?: string
  assetId?: string
  entityType?: string
  entityName?: string
  labels?: Record<string, unknown>
  status?: 'open' | 'resolved'
  rawPayload?: Record<string, unknown>
}

export type AlertIngestResult = {
  alertId: string
  created: boolean
  fingerprint: string
  aggregationKey: string
}

export function ingestAlertWebhook(payload: AlertIngestPayload) {
  return apiRequest<AlertIngestResult>('/api/alerts/ingest/webhook', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

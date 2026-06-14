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
  scannedAlerts: number
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
      ...(init.headers || {})
    }
  })

  const payload = await parseApiResponse<T>(resp)
  if (!resp.ok || !payload.success) {
    throw new Error(payload.message || payload.errorCode || `Request failed with status ${resp.status}`)
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
      timestamp: new Date().toISOString()
    }
  }
  return (await resp.json()) as ApiResponse<T>
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
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
    body: JSON.stringify(payload)
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
      limit: 1000
    })
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
    body: JSON.stringify({ force })
  })
}

export function getLatestIncidentRca(id: string) {
  return apiRequest<RcaAnalysisResponse>(`/api/incidents/${id}/rca/latest`)
}

export const operationsKeys = {
  all: ['operations'] as const,
  tenant: (tenantId: string | null) =>
    [...operationsKeys.all, 'tenant', tenantId] as const,
  alerts: (tenantId: string | null) =>
    [...operationsKeys.tenant(tenantId), 'alerts'] as const,
  incidents: (tenantId: string | null) =>
    [...operationsKeys.tenant(tenantId), 'incidents'] as const,
  incident: (tenantId: string | null, id: string) =>
    [...operationsKeys.incidents(tenantId), id] as const,
  incidentAlerts: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'alerts'] as const,
  incidentTimeline: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'timeline'] as const,
  incidentEvidence: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'evidence'] as const,
  incidentRca: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'rca', 'latest'] as const,
  incidentAiDiagnosis: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'ai', 'latest'] as const,
  incidentReport: (tenantId: string | null, id: string) =>
    [...operationsKeys.incident(tenantId, id), 'reports', 'latest'] as const,
}

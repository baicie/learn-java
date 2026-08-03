import { z } from 'zod'

const idSchema = z.string().min(1)
const timestampSchema = z.string().min(1)
const jsonObjectSchema = z.record(z.string(), z.unknown())

export const alertEventSchema = z.object({
  id: idSchema,
  tenantId: idSchema,
  source: z.string().min(1),
  severity: z.string().min(1),
  title: z.string().min(1),
  status: z.string().min(1),
  startsAt: timestampSchema,
  createdAt: timestampSchema,
})

export const incidentSchema = z.object({
  id: idSchema,
  tenantId: idSchema,
  title: z.string().min(1),
  summary: z.string().nullish(),
  severity: z.string().min(1),
  status: z.string().min(1),
  source: z.string().min(1),
  primaryAssetId: z.string().nullish(),
  aggregationKey: z.string().nullish(),
  alertCount: z.number().int().nonnegative(),
  startedAt: timestampSchema,
  detectedAt: timestampSchema,
  lastSeenAt: timestampSchema.nullish(),
  resolvedAt: timestampSchema.nullish(),
  createdAt: timestampSchema,
  updatedAt: timestampSchema,
})

export const incidentAlertSchema = z.object({
  id: idSchema,
  source: z.string().min(1),
  sourceEventId: z.string().nullish(),
  severity: z.string().min(1),
  title: z.string().min(1),
  status: z.string().min(1),
  assetId: z.string().nullish(),
  entityName: z.string().nullish(),
  fingerprint: z.string().min(1),
  startsAt: timestampSchema,
  relationType: z.string().min(1),
})

export const incidentTimelineSchema = z.object({
  id: idSchema,
  eventTime: timestampSchema,
  eventType: z.string().min(1),
  title: z.string().min(1),
  description: z.string().nullish(),
  source: z.string().min(1),
  payloadJson: z.string(),
})

export const incidentDetailSchema = z.object({
  incident: incidentSchema,
  alerts: z.array(incidentAlertSchema),
  timeline: z.array(incidentTimelineSchema),
})

export const incidentEvidenceSchema = z.object({
  id: idSchema,
  tenantId: idSchema,
  incidentId: idSchema,
  evidenceKey: z.string().min(1),
  source: z.string().min(1),
  evidenceType: z.string().min(1),
  title: z.string().min(1),
  summary: z.string(),
  timeRangeStart: timestampSchema.nullish(),
  timeRangeEnd: timestampSchema.nullish(),
  confidence: z.number(),
  payloadJson: z.string(),
  createdAt: timestampSchema,
  updatedAt: timestampSchema,
})

const rcaEvidenceSchema = z.object({
  ruleId: z.string().min(1),
  title: z.string().min(1),
  description: z.string().nullish(),
  score: z.number(),
  confidence: z.number(),
  attributes: jsonObjectSchema.nullish(),
})

export const rcaAnalysisSchema = z.object({
  id: idSchema,
  incidentId: idSchema,
  status: z.string().min(1),
  suspectedRootCause: z.string(),
  confidence: z.number(),
  summary: z.string().nullish(),
  evidence: z.array(rcaEvidenceSchema),
  matchedRules: z.array(z.string()),
  evidenceRefs: z.array(z.string()),
  modelVersion: z.string().min(1),
  createdAt: timestampSchema,
})

export const aiDiagnosisSchema = z.object({
  id: idSchema,
  incidentId: idSchema,
  status: z.string().min(1),
  provider: z.string().min(1),
  model: z.string().min(1),
  agentName: z.string().min(1),
  summary: z.string(),
  rootCause: z.string(),
  impact: z.string(),
  nextSteps: z.array(z.string()),
  runbookSuggestions: z.array(z.string()),
  risks: z.array(z.string()),
  matchedRules: z.array(z.string()),
  evidenceRefs: z.array(z.string()),
  timeline: z.array(jsonObjectSchema),
  raw: jsonObjectSchema,
  createdAt: timestampSchema,
})

export const incidentReportSchema = z.object({
  id: idSchema,
  incidentId: idSchema,
  versionNo: z.number().int().positive(),
  reportType: z.string().min(1),
  format: z.string().min(1),
  title: z.string().min(1),
  markdownContent: z.string(),
  snapshotJson: z.string(),
  createdBy: z.string().nullish(),
  createdAt: timestampSchema,
  updatedAt: timestampSchema,
})

export type AlertEvent = z.infer<typeof alertEventSchema>
export type Incident = z.infer<typeof incidentSchema>
export type IncidentAlert = z.infer<typeof incidentAlertSchema>
export type IncidentTimeline = z.infer<typeof incidentTimelineSchema>
export type IncidentEvidence = z.infer<typeof incidentEvidenceSchema>
export type RcaAnalysis = z.infer<typeof rcaAnalysisSchema>
export type AiDiagnosis = z.infer<typeof aiDiagnosisSchema>
export type IncidentReport = z.infer<typeof incidentReportSchema>

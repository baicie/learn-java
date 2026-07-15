import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

const seriesSchema = z.object({
  key: z.string(),
  label: z.string(),
  count: z.number(),
  value: z.number().nullable().optional(),
})
const statisticsSchema = z.object({
  totalRecords: z.number(),
  completedRecords: z.number(),
  distinctOwners: z.number(),
  series: z.array(seriesSchema),
  fieldAggregate: z.unknown().nullable().optional(),
})
const workloadSchema = z.object({
  workdayCount: z.number(),
  users: z.array(
    z.object({
      userId: z.string(),
      displayName: z.string(),
      recordCount: z.number(),
      completedCount: z.number(),
      numericWorkload: z.number(),
      recordsPerWorkday: z.number(),
    })
  ),
})
const handoverSchema = z.object({
  id: z.string(),
  fromUserId: z.string(),
  toUserId: z.string(),
  shiftStart: z.string(),
  shiftEnd: z.string(),
  status: z.enum(['draft', 'submitted', 'accepted', 'completed', 'cancelled']),
  summary: z.string(),
  rowVersion: z.number(),
})
const marketVersionSchema = z.object({
  id: z.string(),
  packageId: z.string(),
  packageCode: z.string(),
  name: z.string(),
  visibility: z.enum(['private', 'tenant', 'public']),
  versionNo: z.number(),
  packageJson: z.string(),
  checksum: z.string(),
})
const commentSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  content: z.string(),
  mentionUserIds: z.array(z.string()),
  createdBy: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  rowVersion: z.number(),
})
const attachmentSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  fileName: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  status: z.string(),
  uploadedBy: z.string(),
  createdAt: z.string(),
})
const relationSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  relationType: z.preprocess(
    (value) => (typeof value === 'string' ? value.toLowerCase() : value),
    z.enum(['alert', 'inspection', 'incident'])
  ),
  targetId: z.string(),
  targetTitle: z.string().nullable(),
  targetStatus: z.string().nullable(),
  snapshotJson: z.string(),
  createdBy: z.string(),
  createdAt: z.string(),
})
const aiGenerationSchema = z.object({
  id: z.string(),
  generationType: z.string(),
  resourceType: z.string(),
  resourceId: z.string(),
  status: z.string(),
  outputMarkdown: z.string().nullable(),
  provider: z.string().nullable(),
  model: z.string().nullable(),
  requestedBy: z.string(),
  reviewedBy: z.string().nullable(),
  createdAt: z.string(),
  finishedAt: z.string().nullable(),
})
const approvalTaskSchema = z.object({
  id: z.string(),
  instanceId: z.string(),
  recordId: z.string(),
  recordTitle: z.string(),
  assigneeType: z.string(),
  assigneeValue: z.string(),
  dueAt: z.string().nullable(),
  createdAt: z.string(),
})
const slaInstanceSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  policyName: z.string(),
  status: z.enum(['running', 'met', 'breached', 'cancelled']),
  startedAt: z.string(),
  dueAt: z.string(),
  stoppedAt: z.string().nullable(),
  breachedAt: z.string().nullable(),
  severity: z.enum(['info', 'warning', 'critical']),
})

export type WorkRecordStatistics = z.infer<typeof statisticsSchema>
export type WorkloadSummary = z.infer<typeof workloadSchema>
export type Handover = z.infer<typeof handoverSchema>
export type MarketVersion = z.infer<typeof marketVersionSchema>
export type WorkRecordComment = z.infer<typeof commentSchema>
export type WorkRecordAttachment = z.infer<typeof attachmentSchema>
export type WorkRecordRelation = z.infer<typeof relationSchema>
export type AiGeneration = z.infer<typeof aiGenerationSchema>
export type ApprovalTask = z.infer<typeof approvalTaskSchema>
export type SlaInstance = z.infer<typeof slaInstanceSchema>

export async function getStatistics(from: string, to: string) {
  const { data } = await apiClient.get(
    '/api/work-record/analytics/statistics',
    {
      params: { from, to, groupBy: 'day' },
    }
  )
  return apiResponseSchema(statisticsSchema).parse(data).data
}

export async function getWorkload(from: string, to: string) {
  const { data } = await apiClient.get('/api/work-record/analytics/workload', {
    params: { from, to, groupBy: 'owner' },
  })
  return apiResponseSchema(workloadSchema).parse(data).data
}

export async function listHandovers() {
  const { data } = await apiClient.get('/api/work-record/handovers')
  return apiResponseSchema(z.array(handoverSchema)).parse(data).data
}

export async function listMarketPackages() {
  const { data } = await apiClient.get('/api/work-record/template-market')
  return apiResponseSchema(z.array(marketVersionSchema)).parse(data).data
}

export async function listComments(recordId: string) {
  const { data } = await apiClient.get(
    `/api/work-record/records/${recordId}/comments`
  )
  return apiResponseSchema(z.array(commentSchema)).parse(data).data
}

export async function createComment(recordId: string, content: string) {
  const { data } = await apiClient.post(
    `/api/work-record/records/${recordId}/comments`,
    { content, mentionUserIds: [] }
  )
  return apiResponseSchema(commentSchema).parse(data).data
}

export async function listAttachments(recordId: string) {
  const { data } = await apiClient.get(
    `/api/work-record/records/${recordId}/attachments`
  )
  return apiResponseSchema(z.array(attachmentSchema)).parse(data).data
}

export async function uploadAttachment(recordId: string, file: File) {
  const prepared = await apiClient.post(
    `/api/work-record/records/${recordId}/attachments/uploads`,
    {
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
    }
  )
  const upload = apiResponseSchema(
    z.object({
      uploadId: z.string(),
      uploadUrl: z.string().url(),
      expiresAt: z.string(),
    })
  ).parse(prepared.data).data
  const response = await fetch(upload.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
  })
  if (!response.ok) throw new Error(`附件上传失败（${response.status}）`)
  const completed = await apiClient.post(
    `/api/work-record/records/${recordId}/attachments`,
    { uploadId: upload.uploadId }
  )
  return apiResponseSchema(attachmentSchema).parse(completed.data).data
}

export async function downloadAttachment(
  recordId: string,
  attachmentId: string
) {
  const { data } = await apiClient.post(
    `/api/work-record/records/${recordId}/attachments/${attachmentId}/download`
  )
  return apiResponseSchema(
    z.object({
      url: z.string().url(),
      fileName: z.string(),
      contentType: z.string(),
      expiresAt: z.string(),
    })
  ).parse(data).data
}

export async function listRelations(recordId: string) {
  const { data } = await apiClient.get(
    `/api/work-record/records/${recordId}/relations`
  )
  return apiResponseSchema(z.array(relationSchema)).parse(data).data
}

export async function createRelation(
  recordId: string,
  relationType: 'alert' | 'inspection' | 'incident',
  targetId: string
) {
  const { data } = await apiClient.post(
    `/api/work-record/records/${recordId}/relations`,
    { relationType, targetId }
  )
  return apiResponseSchema(relationSchema).parse(data).data
}

export async function listRecordAiGenerations(recordId: string) {
  const { data } = await apiClient.get('/api/work-record/ai-generations', {
    params: { resourceType: 'record', resourceId: recordId },
  })
  return apiResponseSchema(z.array(aiGenerationSchema)).parse(data).data
}

export async function requestRecordAiSummary(recordId: string) {
  const { data } = await apiClient.post(
    `/api/work-record/ai-generations/records/${recordId}/summary`
  )
  return apiResponseSchema(aiGenerationSchema).parse(data).data
}

export async function reviewAiGeneration(id: string, accepted: boolean) {
  const { data } = await apiClient.post(
    `/api/work-record/ai-generations/${id}/review`,
    undefined,
    { params: { accepted } }
  )
  return apiResponseSchema(aiGenerationSchema).parse(data).data
}

export async function listMonthlyAiGenerations(month: string) {
  const { data } = await apiClient.get('/api/work-record/ai-generations', {
    params: { resourceType: 'tenant_month', resourceId: month.slice(0, 7) },
  })
  return apiResponseSchema(z.array(aiGenerationSchema)).parse(data).data
}

export async function requestMonthlyAiReport(month: string) {
  const { data } = await apiClient.post(
    '/api/work-record/ai-generations/monthly',
    undefined,
    { params: { month: `${month.slice(0, 7)}-01` } }
  )
  return apiResponseSchema(aiGenerationSchema).parse(data).data
}

export async function listPendingApprovalTasks() {
  const { data } = await apiClient.get(
    '/api/work-record/workflow/approval-tasks'
  )
  return apiResponseSchema(z.array(approvalTaskSchema)).parse(data).data
}

export async function actOnApprovalTask(
  id: string,
  approved: boolean,
  comment: string
) {
  const { data } = await apiClient.post(
    `/api/work-record/workflow/approval-tasks/${id}/act`,
    { approved, comment }
  )
  return apiResponseSchema(z.object({ status: z.string() })).parse(data).data
}

export async function listRecordSla(recordId: string) {
  const { data } = await apiClient.get(
    `/api/work-record/workflow/records/${recordId}/sla`
  )
  return apiResponseSchema(z.array(slaInstanceSchema)).parse(data).data
}

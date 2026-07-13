import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import {
  WORK_RECORD_FIELD_TYPES,
  WORK_RECORD_STATUSES,
  type AuditEvent,
  type WorkRecord,
  type WorkRecordField,
  type WorkRecordRuntimeFormValue,
  type WorkRecordTemplate,
} from '@/lib/work-records/runtime/types'

const lower = (value: unknown) =>
  typeof value === 'string' ? value.toLowerCase() : value

const statusSchema = z.preprocess(lower, z.enum(WORK_RECORD_STATUSES))
const fieldTypeSchema = z.preprocess(lower, z.enum(WORK_RECORD_FIELD_TYPES))
const optionSourceSchema = z.preprocess(lower, z.enum(['static', 'dict']))

const templateStatusSchema = z.preprocess(
  lower,
  z.enum(['draft', 'published', 'disabled', 'archived'])
)

const templateSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  status: templateStatusSchema,
  enabled: z.boolean(),
  currentVersionId: z.string().nullable().optional(),
  draftSchemaJson: z.string(),
  draftDesignerJson: z.string(),
  createdBy: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deletedAt: z.string().nullable().optional(),
})

const recordFieldSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  templateVersionId: z.string(),
  fieldName: z.string(),
  fieldCode: z.string(),
  fieldType: fieldTypeSchema,
  required: z.boolean(),
  defaultValue: z.string().nullable().optional(),
  optionSource: optionSourceSchema,
  dictCode: z.string().nullable().optional(),
  optionsJson: z.string(),
  schemaPath: z.string().nullable().optional(),
  listVisible: z.boolean(),
  filterable: z.boolean(),
  exportable: z.boolean(),
  statistical: z.boolean(),
  sortOrder: z.number(),
  enabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

const auditEventSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  actorId: z.string(),
  action: z.string(),
  resourceType: z.string(),
  resourceId: z.string(),
  beforeJson: z.string(),
  afterJson: z.string(),
  detailJson: z.string(),
  createdAt: z.string(),
})

const recordSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  templateVersionId: z.string(),
  title: z.string(),
  status: statusSchema,
  ownerId: z.string().nullable().optional(),
  creatorId: z.string(),
  recordTime: z.string(),
  builtinDataJson: z.string(),
  customDataJson: z.string(),
  rowVersion: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deletedAt: z.string().nullable().optional(),
})

export async function listPublishedTemplates(): Promise<WorkRecordTemplate[]> {
  const { data } = await apiClient.get('/api/work-record/templates', {
    params: { includeDisabled: false },
  })

  return apiResponseSchema(z.array(templateSchema))
    .parse(data)
    .data.filter(
      (item) =>
        item.enabled && item.status === 'published' && item.currentVersionId
    )
}

export async function listTemplateVersionFields(
  templateId: string,
  versionId: string
): Promise<WorkRecordField[]> {
  const { data } = await apiClient.get(
    `/api/work-record/templates/${templateId}/versions/${versionId}/fields`
  )
  return apiResponseSchema(z.array(recordFieldSchema)).parse(data).data
}

export async function getWorkRecord(recordId: string): Promise<WorkRecord> {
  const { data } = await apiClient.get(`/api/work-record/records/${recordId}`)
  return apiResponseSchema(recordSchema).parse(data).data
}

export async function listWorkRecordHistory(
  recordId: string
): Promise<AuditEvent[]> {
  const { data } = await apiClient.get(
    `/api/work-record/records/${recordId}/history`
  )
  return apiResponseSchema(z.array(auditEventSchema)).parse(data).data
}

export async function createWorkRecord(
  value: WorkRecordRuntimeFormValue
): Promise<WorkRecord> {
  const { data } = await apiClient.post('/api/work-record/records', {
    templateId: value.templateId,
    templateVersionId: value.templateVersionId,
    title: value.title,
    status: value.status,
    ownerId: value.ownerId || undefined,
    recordTime: toOffsetDateTime(value.recordTime),
    builtinDataJson: '{}',
    customDataJson: JSON.stringify(value.customData ?? {}),
  })
  return apiResponseSchema(recordSchema).parse(data).data
}

export async function updateWorkRecord(
  recordId: string,
  value: WorkRecordRuntimeFormValue
): Promise<WorkRecord> {
  const { data } = await apiClient.put(`/api/work-record/records/${recordId}`, {
    title: value.title,
    status: value.status,
    ownerId: value.ownerId || undefined,
    recordTime: toOffsetDateTime(value.recordTime),
    builtinDataJson: '{}',
    customDataJson: JSON.stringify(value.customData ?? {}),
  })
  return apiResponseSchema(recordSchema).parse(data).data
}

export function parseCustomData(record?: WorkRecord): Record<string, unknown> {
  if (!record?.customDataJson) return {}
  try {
    const parsed = JSON.parse(record.customDataJson)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

export function toLocalDateTimeInput(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toOffsetDateTime(value: string) {
  if (!value) {
    throw new Error('recordTime is required')
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    throw new Error('invalid recordTime')
  }
  return date.toISOString()
}

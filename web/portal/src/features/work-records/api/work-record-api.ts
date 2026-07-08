import {
  apiResponseSchema,
  workRecordPageSchema,
  workRecordSchema,
  recordListMetadataSchema,
  type WorkRecord,
  type WorkRecordPage,
  type RecordListMetadata,
  type DynamicFilter,
} from '../data/schema'
import { workRecordHttp } from './http'

// ---- List query params ----

export type WorkRecordListParams = {
  page?: number
  pageSize?: number
  templateId?: string
  status?: string[]
  keyword?: string
  recordTimeFrom?: string
  recordTimeTo?: string
  filters?: DynamicFilter[]
}

export type WorkRecordPayload = {
  templateId: string
  title: string
  status?: string
  ownerId?: string | null
  recordTime?: string
  builtinDataJson?: string
  customDataJson?: string
}

// ---- List API ----

export async function listWorkRecords(
  params: WorkRecordListParams
): Promise<WorkRecordPage> {
  // Build query params
  const searchParams: Record<string, unknown> = {}

  if (params.page) searchParams.page = params.page
  if (params.pageSize) searchParams.pageSize = params.pageSize
  if (params.templateId) searchParams.templateId = params.templateId
  if (params.status?.length) searchParams.status = params.status
  if (params.keyword) searchParams.keyword = params.keyword
  if (params.recordTimeFrom) searchParams.recordTimeFrom = params.recordTimeFrom
  if (params.recordTimeTo) searchParams.recordTimeTo = params.recordTimeTo
  if (params.filters?.length) {
    // Base64url encode filters JSON
    const json = JSON.stringify(params.filters)
    searchParams.filters = btoa(unescape(encodeURIComponent(json)))
  }

  const { data } = await workRecordHttp.get('/api/work-record/records', {
    params: searchParams,
  })
  return apiResponseSchema(workRecordPageSchema).parse(data).data
}

// ---- List metadata API ----

export async function getListMetadata(
  templateId?: string
): Promise<RecordListMetadata> {
  const { data } = await workRecordHttp.get(
    '/api/work-record/records/list-metadata',
    {
      params: templateId ? { templateId } : undefined,
    }
  )
  return apiResponseSchema(recordListMetadataSchema).parse(data).data
}

// ---- Export API ----

export type ExportPayload = {
  templateId?: string
  status?: string[]
  keyword?: string
  recordTimeFrom?: string
  recordTimeTo?: string
  filters?: DynamicFilter[]
  columns?: string[]
  format?: string
}

export async function exportRecords(payload: ExportPayload): Promise<Blob> {
  const body: Record<string, unknown> = {}
  if (payload.templateId) body.templateId = payload.templateId
  if (payload.status?.length) body.status = payload.status
  if (payload.keyword) body.keyword = payload.keyword
  if (payload.recordTimeFrom) body.recordTimeFrom = payload.recordTimeFrom
  if (payload.recordTimeTo) body.recordTimeTo = payload.recordTimeTo
  if (payload.filters?.length) body.filters = payload.filters
  if (payload.columns?.length) body.columns = payload.columns
  body.format = payload.format ?? 'csv'

  const { data } = await workRecordHttp.post(
    '/api/work-record/records/export',
    body,
    { responseType: 'blob' }
  )
  return data as Blob
}

// ---- CRUD APIs ----

export async function getWorkRecord(recordId: string): Promise<WorkRecord> {
  const { data } = await workRecordHttp.get(
    `/api/work-record/records/${recordId}`
  )
  return apiResponseSchema(workRecordSchema).parse(data).data
}

export async function createWorkRecord(
  payload: WorkRecordPayload
): Promise<WorkRecord> {
  const { data } = await workRecordHttp.post(
    '/api/work-record/records',
    payload
  )
  return apiResponseSchema(workRecordSchema).parse(data).data
}

export async function updateWorkRecord(
  recordId: string,
  payload: Partial<WorkRecordPayload>
): Promise<WorkRecord> {
  const { data } = await workRecordHttp.put(
    `/api/work-record/records/${recordId}`,
    payload
  )
  return apiResponseSchema(workRecordSchema).parse(data).data
}

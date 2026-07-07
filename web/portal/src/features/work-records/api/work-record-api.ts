import {
  apiResponseSchema,
  workRecordPageSchema,
  workRecordSchema,
  type WorkRecord,
  type WorkRecordPage,
} from '../data/schema'
import { workRecordHttp } from './http'

export type WorkRecordListParams = {
  page?: number
  pageSize?: number
  status?: string[]
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

export async function listWorkRecords(
  params: WorkRecordListParams
): Promise<WorkRecordPage> {
  const { data } = await workRecordHttp.get('/api/work-record/records', {
    params: {
      page: params.page,
      size: params.pageSize,
      status: params.status?.[0],
    },
  })
  return apiResponseSchema(workRecordPageSchema).parse(data).data
}

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

import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import type { DynamicFilter, PageResult, RecordListMeta, WorkRecord } from './types'

const recordSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  templateVersionId: z.string(),
  title: z.string(),
  status: z
    .enum(['draft', 'processing', 'done', 'archived'])
    .or(z.string())
    .transform((v) => v.toLowerCase() as WorkRecord['status']),
  ownerId: z.string().nullable(),
  creatorId: z.string(),
  recordTime: z.string(),
  builtinDataJson: z.string(),
  customDataJson: z.string(),
  rowVersion: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deletedAt: z.string().nullable(),
})

const pageSchema = z.object({
  total: z.number(),
  page: z.number(),
  size: z.number(),
  items: z.array(recordSchema),
})

export async function fetchRecordList(params: {
  page: number
  pageSize: number
  quickView: string
  templateId?: string
  statuses?: string[]
  ownerId?: string
  creatorId?: string
  keyword?: string
  recordTimeFrom?: string
  recordTimeTo?: string
  sortBy?: string
  sortDir?: string
  dynamicFilters?: DynamicFilter[]
}): Promise<PageResult<WorkRecord>> {
  const { data } = await apiClient.get('/api/work-record/records', {
    params: {
      page: params.page,
      pageSize: params.pageSize,
      quickView: params.quickView,
      templateId: blank(params.templateId),
      statuses: params.statuses,
      ownerId: blank(params.ownerId),
      creatorId: blank(params.creatorId),
      keyword: blank(params.keyword),
      recordTimeFrom: toOffset(params.recordTimeFrom),
      recordTimeTo: toOffset(params.recordTimeTo),
      sortBy: params.sortBy,
      sortDir: params.sortDir,
      dynamicFilters: params.dynamicFilters?.length
        ? JSON.stringify(params.dynamicFilters)
        : undefined,
    },
  })
  return apiResponseSchema(pageSchema).parse(data).data
}

export async function fetchRecordListMeta(): Promise<RecordListMeta> {
  const { data } = await apiClient.get('/api/work-record/records/meta')
  return apiResponseSchema(z.any()).parse(data).data
}

function blank(value?: string) {
  return value && value.trim() ? value : undefined
}

function toOffset(value?: string) {
  if (!value) return undefined
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return date.toISOString()
}
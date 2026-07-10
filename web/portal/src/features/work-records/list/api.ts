import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import type {
  ListQueryState,
  PageResult,
  RecordListMeta,
  WorkRecord,
} from './types'

const recordStatusSchema = z.preprocess(
  (value) => (typeof value === 'string' ? value.toLowerCase() : value),
  z.enum(['draft', 'processing', 'done', 'archived'])
)

const recordSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  templateVersionId: z.string(),
  title: z.string(),
  status: recordStatusSchema,
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

const columnSchema = z.object({
  key: z.string(),
  title: z.string(),
  source: z.enum(['builtin', 'custom']),
  fieldCode: z.string().nullable(),
  fieldType: z.string(),
  optionSource: z.string().nullable(),
  dictCode: z.string().nullable(),
  optionsJson: z.string(),
  visibleByDefault: z.boolean(),
  sortable: z.boolean(),
  exportable: z.boolean(),
  sortOrder: z.number(),
})

const templateSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    status: z.enum(['draft', 'published', 'disabled', 'archived']),
    enabled: z.boolean(),
    currentVersionId: z.string().nullable(),
  })
  .passthrough()

const metaSchema = z.object({
  templates: z.array(templateSchema),
  columns: z.array(columnSchema),
  exportColumns: z.array(columnSchema),
  filterFields: z.array(columnSchema),
  dictCodes: z.array(z.string()),
  maxExportRows: z.number(),
  quickViews: z.array(z.string()),
})

export async function fetchRecordList(
  params: ListQueryState
): Promise<PageResult<WorkRecord>> {
  const { data } = await apiClient.get('/api/work-record/records', {
    params: {
      page: params.page,
      pageSize: params.pageSize,
      quickView: params.quickView,
      workdayCount: params.workdayCount,
      templateId: blank(params.templateId),
      templateVersionId: blank(params.templateVersionId),
      statuses: params.statuses,
      ownerId: blank(params.ownerId),
      creatorId: blank(params.creatorId),
      keyword: blank(params.keyword),
      recordTimeFrom: toOffset(params.recordTimeFrom),
      recordTimeTo: toOffset(params.recordTimeTo),
      sortBy: params.sortBy,
      sortDir: params.sortDir,
      dynamicFilters: params.dynamicFilters.length
        ? JSON.stringify(params.dynamicFilters)
        : undefined,
    },
  })

  return apiResponseSchema(pageSchema).parse(data).data
}

export async function fetchRecordListMeta(
  templateId?: string
): Promise<RecordListMeta> {
  const { data } = await apiClient.get('/api/work-record/records/meta', {
    params: {
      templateId: blank(templateId),
    },
  })

  return apiResponseSchema(metaSchema).parse(data).data
}

function blank(value?: string) {
  return value?.trim() || undefined
}

function toOffset(value?: string) {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

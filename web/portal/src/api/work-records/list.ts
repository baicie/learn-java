import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import type {
  ListQueryState,
  PageResult,
  RecordListMeta,
  RecordWorkdaySummary,
  WorkRecord,
} from '@/components/work-records/list/types'

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
  ownerId: z.string().nullable().optional(),
  creatorId: z.string(),
  recordTime: z.string(),
  builtinDataJson: z.string(),
  customDataJson: z.string(),
  rowVersion: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
  deletedAt: z.string().nullable().optional().optional(),
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
  fieldCode: z.string().nullable().optional(),
  fieldType: z.string(),
  optionSource: z.string().nullable().optional(),
  dictCode: z.string().nullable().optional(),
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
    currentVersionId: z.string().nullable().optional(),
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

export async function fetchRecordUserNames(
  userIds: string[]
): Promise<Record<string, string>> {
  if (!userIds.length) return {}
  const params = new URLSearchParams()
  userIds.forEach((userId) => params.append('ids', userId))
  const { data } = await apiClient.get('/api/work-record/users/display-names', {
    params,
  })
  return apiResponseSchema(z.record(z.string(), z.string())).parse(data).data
}

const workdaySummarySchema = z.object({
  calendarId: z.string(),
  calendarName: z.string(),
  timeZone: z.string(),
  month: z.string(),
  periodStart: z.string(),
  periodEnd: z.string(),
  workdayCount: z.number().int().nonnegative(),
  firstWorkday: z.string().nullable().optional(),
  lastWorkday: z.string().nullable().optional(),
})

export async function fetchWorkdaySummary(
  month?: string
): Promise<RecordWorkdaySummary> {
  const { data } = await apiClient.get(
    '/api/work-record/records/workdays/summary',
    {
      params: {
        month: blank(month),
      },
    }
  )

  return apiResponseSchema(workdaySummarySchema).parse(data).data
}

function blank(value?: string) {
  return value?.trim() || undefined
}

function toOffset(value?: string) {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

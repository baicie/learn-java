export type WorkRecordStatus = 'draft' | 'processing' | 'done' | 'archived'

export type WorkRecord = {
  id: string
  tenantId: string
  templateId: string
  templateVersionId: string
  title: string
  status: WorkRecordStatus
  ownerId: string | null
  creatorId: string
  recordTime: string
  builtinDataJson: string
  customDataJson: string
  rowVersion: number
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export type WorkRecordTemplate = {
  id: string
  code: string
  name: string
  status: 'draft' | 'published' | 'disabled' | 'archived'
  enabled: boolean
  currentVersionId: string | null
}

export type RecordListColumn = {
  key: string
  title: string
  source: 'builtin' | 'custom'
  fieldCode: string | null
  fieldType: string
  visibleByDefault: boolean
  sortable: boolean
  sortOrder: number
}

export type RecordListMeta = {
  templates: WorkRecordTemplate[]
  columns: RecordListColumn[]
  filterFields: RecordListColumn[]
  dictCodes: string[]
  maxExportRows: number
  quickViews: string[]
}

export type PageResult<T> = {
  total: number
  page: number
  size: number
  items: T[]
}

export type DynamicFilter = {
  fieldCode: string
  operator: 'eq' | 'in'
  value: unknown
}

export type ListQueryState = {
  page: number
  pageSize: number
  quickView: string
  templateId: string
  statuses: string[]
  ownerId: string
  creatorId: string
  keyword: string
  recordTimeFrom: string
  recordTimeTo: string
  sortBy: string
  sortDir: 'asc' | 'desc'
  dynamicFilters: DynamicFilter[]
  visibleColumns: string[]
}
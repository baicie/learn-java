export type WorkRecordStatus = 'draft' | 'processing' | 'done' | 'archived'

export type DynamicFilterOperator =
  | 'eq'
  | 'in'
  | 'contains'
  | 'gte'
  | 'lte'
  | 'between'
  | 'contains_any'
  | 'contains_all'
  | 'exists'
  | 'not_exists'

export type DynamicFilter = {
  fieldCode: string
  operator: DynamicFilterOperator
  value?: string | number | boolean | Array<string | number | boolean>
  values?: Array<string | number | boolean>
}

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
  optionSource: string | null
  dictCode: string | null
  optionsJson: string
  visibleByDefault: boolean
  sortable: boolean
  exportable: boolean
  sortOrder: number
}

export type RecordListMeta = {
  templates: WorkRecordTemplate[]
  columns: RecordListColumn[]
  exportColumns: RecordListColumn[]
  filterFields: RecordListColumn[]
  dictCodes: string[]
  maxExportRows: number
  quickViews: string[]
}

export type DictListOption = {
  value: string
  label: string
  enabled: boolean
}

export type DictOptionMap = Record<string, DictListOption[]>

export type PageResult<T> = {
  total: number
  page: number
  size: number
  items: T[]
}

export type ListQueryState = {
  page: number
  pageSize: number
  quickView: string
  workdayCount: number
  templateId: string
  templateVersionId: string
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

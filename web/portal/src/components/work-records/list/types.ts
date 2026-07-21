type WorkRecordStatus = 'draft' | 'processing' | 'done' | 'archived'

export type WorkRecord = {
  id: string
  tenantId: string
  templateId: string
  templateVersionId: string
  title: string
  status: WorkRecordStatus
  ownerId?: string | null
  creatorId: string
  recordTime: string
  builtinDataJson: string
  customDataJson: string
  rowVersion: number
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
}

type WorkRecordTemplate = {
  id: string
  code: string
  name: string
  status: 'draft' | 'published' | 'disabled' | 'archived'
  enabled: boolean
  currentVersionId?: string | null
}

export type RecordListColumn = {
  key: string
  title: string
  source: 'builtin' | 'custom'
  fieldCode?: string | null
  fieldType: string
  optionSource?: string | null
  dictCode?: string | null
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
  dictCodes: string[]
  maxExportRows: number
  quickViews: string[]
}

type DictListOption = {
  value: string
  label: string
  enabled: boolean
}

export type DictOptionMap = Record<string, DictListOption[]>

export type WorkRecordUserOption = {
  id: string
  label: string
}

export type PageResult<T> = {
  total: number
  page: number
  size: number
  items: T[]
}

export type RecordWorkdaySummary = {
  calendarId: string
  calendarName: string
  timeZone: string
  month: string
  periodStart: string
  periodEnd: string
  workdayCount: number
  firstWorkday?: string | null
  lastWorkday?: string | null
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
  visibleColumns: string[]
}

export function buildEmptyListQuery(): ListQueryState {
  return {
    page: 1,
    pageSize: 30,
    quickView: 'all',
    workdayCount: 5,
    templateId: '',
    templateVersionId: '',
    statuses: [],
    ownerId: '',
    creatorId: '',
    keyword: '',
    recordTimeFrom: '',
    recordTimeTo: '',
    sortBy: 'recordTime',
    sortDir: 'desc',
    visibleColumns: [],
  }
}

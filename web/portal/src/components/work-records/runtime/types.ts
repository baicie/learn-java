export const WORK_RECORD_STATUSES = [
  'draft',
  'processing',
  'pending_approval',
  'rejected',
  'done',
  'archived',
] as const

export type WorkRecordStatus = (typeof WORK_RECORD_STATUSES)[number]

export const WORK_RECORD_FIELD_TYPES = [
  'text',
  'textarea',
  'number',
  'date',
  'datetime',
  'select',
  'multi_select',
  'user',
  'boolean',
] as const

type WorkRecordFieldType = (typeof WORK_RECORD_FIELD_TYPES)[number]

type WorkRecordOptionSource = 'static' | 'dict'

export type WorkRecordTemplate = {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string | null
  status: 'draft' | 'published' | 'disabled' | 'archived'
  enabled: boolean
  isDefault?: boolean
  currentVersionId?: string | null
  draftSchemaJson: string
  draftDesignerJson: string
  createdBy: string
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
}

export type WorkRecordField = {
  id: string
  tenantId: string
  templateId: string
  templateVersionId: string
  fieldName: string
  fieldCode: string
  fieldType: WorkRecordFieldType
  required: boolean
  defaultValue?: string | null
  optionSource: WorkRecordOptionSource
  dictCode?: string | null
  optionsJson: string
  schemaPath?: string | null
  columnSpan?: 1 | 2
  validationJson?: string
  listVisible: boolean
  filterable: boolean
  exportable: boolean
  statistical: boolean
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

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

type DictItemOption = {
  id: string
  itemLabel: string
  itemValue: string
  color?: string | null
  icon?: string | null
  description?: string | null
  enabled: boolean
}

export type RuntimeDictOptions = Record<string, DictItemOption[]>

export type WorkRecordRuntimeFormValue = {
  title: string
  templateId: string
  templateVersionId: string
  status: WorkRecordStatus
  ownerId: string
  recordTime: string
  customData: Record<string, unknown>
}

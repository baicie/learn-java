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

export type WorkRecordFieldType = (typeof WORK_RECORD_FIELD_TYPES)[number]

export type WorkRecordOptionSource = 'static' | 'dict'

export type DesignerField = {
  id: string
  fieldName: string
  fieldCode: string
  fieldType: WorkRecordFieldType
  required: boolean
  optionSource: WorkRecordOptionSource
  dictCode: string
  listVisible: boolean
  filterable: boolean
  exportable: boolean
  statistical: boolean
  sortOrder: number
  enabled: boolean
  locked: boolean
  referenced: boolean
}

export type WorkRecordTemplate = {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string | null
  status: 'draft' | 'published' | 'disabled' | 'archived'
  enabled: boolean
  currentVersionId?: string | null
  draftSchemaJson: string
  draftDesignerJson: string
  createdBy: string
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
}

export type WorkRecordTemplateVersion = {
  id: string
  tenantId: string
  templateId: string
  versionNo: number
  versionName?: string | null
  schemaJson: string
  designerJson: string
  fieldIndexJson: string
  publishedBy: string
  publishedAt: string
  createdAt: string
}

export type WorkRecordVersionField = {
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
  listVisible: boolean
  filterable: boolean
  exportable: boolean
  statistical: boolean
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type TemplatePublishValidationResult = {
  valid: boolean
  schemaVersion: number
  fieldCount: number
  referencedRecordCount: number
  errors: string[]
  warnings: string[]
}

export type DictTypeOption = {
  id: string
  dictCode: string
  dictName: string
  enabled: boolean
}

export type SchemaDiffItem = {
  type: 'added' | 'removed' | 'disabled' | 'type_changed' | 'flag_changed'
  fieldCode: string
  message: string
}

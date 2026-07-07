export type WorkRecordTemplate = {
  id: string
  tenantId: string
  name: string
  code: string
  description?: string | null
  enabled: boolean
  schemaJson: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type WorkRecordField = {
  id: string
  tenantId: string
  templateId: string
  fieldName: string
  fieldCode: string
  fieldType: string
  required: boolean
  defaultValue?: string | null
  optionSource: 'static' | 'dict'
  dictCode?: string | null
  optionsJson: string
  listVisible: boolean
  filterable: boolean
  statistical: boolean
  sortOrder: number
  enabled: boolean
}

export type CreateTemplatePayload = {
  name: string
  code: string
  description?: string
  enabled?: boolean
  schemaJson?: string
}

export type UpdateTemplatePayload = {
  name?: string | null
  description?: string | null
  enabled?: boolean | null
  schemaJson?: string | null
}

export type CreateFieldPayload = {
  fieldName: string
  fieldCode: string
  fieldType: string
  required?: boolean
  defaultValue?: string
  optionSource?: 'static' | 'dict'
  dictCode?: string
  optionsJson?: string
  listVisible?: boolean
  filterable?: boolean
  statistical?: boolean
  sortOrder?: number
  enabled?: boolean
}

export type UpdateFieldPayload = {
  fieldName?: string | null
  required?: boolean | null
  defaultValue?: string | null
  optionSource?: 'static' | 'dict' | null
  dictCode?: string | null
  optionsJson?: string | null
  listVisible?: boolean | null
  filterable?: boolean | null
  statistical?: boolean | null
  sortOrder?: number | null
  enabled?: boolean | null
}

export type WorkRecord = {
  id: string
  tenantId: string
  templateId: string
  title: string
  status: string
  ownerId?: string | null
  creatorId: string
  recordTime: string
  builtinDataJson: string
  customDataJson: string
  createdAt: string
  updatedAt: string
}

export type CreateWorkRecordPayload = {
  templateId: string
  title: string
  status?: string
  ownerId?: string | null
  recordTime?: string
  builtinDataJson?: string
  customDataJson?: string
}

export type UpdateWorkRecordPayload = {
  title?: string | null
  status?: string | null
  ownerId?: string | null
  recordTime?: string | null
  builtinDataJson?: string | null
  customDataJson?: string | null
}

export type PageResult<T> = {
  total: number
  page: number
  size: number
  items: T[]
}

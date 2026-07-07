export type DictTypeRecord = {
  id: string
  tenantId: string
  dictCode: string
  dictName: string
  description: string | null
  systemBuiltin: boolean
  enabled: boolean
  sortOrder: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type DictItemRecord = {
  id: string
  tenantId: string
  dictTypeId: string
  itemLabel: string
  itemValue: string
  color: string | null
  icon: string | null
  description: string | null
  systemBuiltin: boolean
  enabled: boolean
  sortOrder: number
  extraJson: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type CreateDictTypePayload = {
  dictCode: string
  dictName: string
  description?: string | null
  enabled?: boolean
  sortOrder?: number
}

export type UpdateDictTypePayload = {
  dictName?: string | null
  description?: string | null
  enabled?: boolean | null
  sortOrder?: number | null
}

export type CreateDictItemPayload = {
  itemLabel: string
  itemValue: string
  color?: string | null
  icon?: string | null
  description?: string | null
  enabled?: boolean
  sortOrder?: number
  extraJson?: string
}

export type UpdateDictItemPayload = {
  itemLabel?: string | null
  color?: string | null
  icon?: string | null
  description?: string | null
  enabled?: boolean | null
  sortOrder?: number | null
}

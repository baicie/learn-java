import {
  parseCustomData,
  toLocalDateTimeInput,
} from '@/api/work-records/records'
import {
  type RuntimeDictOptions,
  type WorkRecord,
  type WorkRecordField,
  type WorkRecordRuntimeFormValue,
  type WorkRecordStatus,
} from './types'

export function buildInitialFormValue(input: {
  templates?: {
    id: string
    currentVersionId?: string | null
    isDefault?: boolean
  }[]
  record?: WorkRecord
}): WorkRecordRuntimeFormValue {
  if (input.record) {
    return {
      title: input.record.title,
      templateId: input.record.templateId,
      templateVersionId: input.record.templateVersionId,
      status: input.record.status,
      ownerId: input.record.ownerId ?? '',
      recordTime: toLocalDateTimeInput(input.record.recordTime),
      customData: parseCustomData(input.record),
    }
  }

  const template =
    input.templates?.find((item) => item.isDefault) ?? input.templates?.[0]

  return {
    title: '',
    templateId: template?.id ?? '',
    templateVersionId: template?.currentVersionId ?? '',
    status: 'draft',
    ownerId: '',
    recordTime: toLocalDateTimeInput(new Date().toISOString()),
    customData: {},
  }
}

export function hasMeaningfulCustomData(
  customData: Record<string, unknown>
): boolean {
  return Object.values(customData).some((value) => !isEmptyValue(value))
}

export function setCustomValue(
  value: WorkRecordRuntimeFormValue,
  fieldCode: string,
  fieldValue: unknown
): WorkRecordRuntimeFormValue {
  return {
    ...value,
    customData: {
      ...value.customData,
      [fieldCode]: fieldValue,
    },
  }
}

export function sanitizeCustomDataForSubmit(
  value: WorkRecordRuntimeFormValue,
  fields: WorkRecordField[]
): WorkRecordRuntimeFormValue {
  const enabledCodes = new Set(
    fields.filter((field) => field.enabled).map((field) => field.fieldCode)
  )

  const customData: Record<string, unknown> = {}
  for (const [key, fieldValue] of Object.entries(value.customData)) {
    if (enabledCodes.has(key) && !isEmptyValue(fieldValue)) {
      customData[key] = fieldValue
    }
  }

  return {
    ...value,
    customData,
  }
}

export function statusLabel(status: WorkRecordStatus) {
  const labels: Record<WorkRecordStatus, string> = {
    draft: '草稿',
    processing: '处理中',
    pending_approval: '待审批',
    rejected: '已驳回',
    done: '已完成',
    archived: '已归档',
  }
  return labels[status]
}

export function fieldDisplayValue(
  field: WorkRecordField,
  value: unknown,
  dictOptions: RuntimeDictOptions
) {
  if (isEmptyValue(value)) return '-'

  if (field.optionSource === 'dict' && field.dictCode) {
    const options = dictOptions[field.dictCode] ?? []
    if (Array.isArray(value)) {
      return value.map((item) => optionLabel(options, String(item))).join('、')
    }
    return optionLabel(options, String(value))
  }

  if (field.fieldType === 'boolean') {
    return value === true ? '是' : '否'
  }

  if (Array.isArray(value)) {
    return value.join('、')
  }

  return String(value)
}

function optionLabel(
  options: { itemLabel: string; itemValue: string; enabled: boolean }[],
  value: string
) {
  const option = options.find((item) => item.itemValue === value)
  if (!option) return value
  return option.enabled ? option.itemLabel : `${option.itemLabel}（已禁用）`
}

function isEmptyValue(value: unknown) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

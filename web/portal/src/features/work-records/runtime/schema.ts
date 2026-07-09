import {
  type RuntimeDictOptions,
  type WorkRecord,
  type WorkRecordField,
  type WorkRecordRuntimeFormValue,
  type WorkRecordRuntimeValidation,
  type WorkRecordStatus,
} from './types'
import { parseCustomData, toLocalDateTimeInput } from './api'

export function buildInitialFormValue(input: {
  templates?: { id: string; currentVersionId: string | null }[]
  record?: WorkRecord
}): WorkRecordRuntimeFormValue {
  const firstTemplate = input.templates?.[0]

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

  return {
    title: '',
    templateId: firstTemplate?.id ?? '',
    templateVersionId: firstTemplate?.currentVersionId ?? '',
    status: 'draft',
    ownerId: '',
    recordTime: toLocalDateTimeInput(new Date().toISOString()),
    customData: {},
  }
}

export function validateRuntimeForm(
  value: WorkRecordRuntimeFormValue,
  fields: WorkRecordField[]
): WorkRecordRuntimeValidation {
  const errors: string[] = []

  if (!value.title.trim()) {
    errors.push('标题不能为空')
  }

  if (!value.templateId) {
    errors.push('请选择模板')
  }

  if (!value.templateVersionId) {
    errors.push('模板未发布，无法填写记录')
  }

  if (!value.recordTime) {
    errors.push('记录时间不能为空')
  } else if (Number.isNaN(new Date(value.recordTime).getTime())) {
    errors.push('记录时间格式不正确')
  }

  for (const field of fields.filter((item) => item.enabled)) {
    const fieldValue = value.customData[field.fieldCode]

    if (field.required && isEmptyValue(fieldValue)) {
      errors.push(`${field.fieldName} 不能为空`)
      continue
    }

    if (isEmptyValue(fieldValue)) {
      continue
    }

    switch (field.fieldType) {
      case 'number':
        if (typeof fieldValue !== 'number' || Number.isNaN(fieldValue)) {
          errors.push(`${field.fieldName} 必须是数字`)
        }
        break
      case 'boolean':
        if (typeof fieldValue !== 'boolean') {
          errors.push(`${field.fieldName} 必须是布尔值`)
        }
        break
      case 'date':
        if (
          typeof fieldValue !== 'string' ||
          !/^\d{4}-\d{2}-\d{2}$/.test(fieldValue)
        ) {
          errors.push(`${field.fieldName} 必须是日期`)
        }
        break
      case 'datetime':
        if (
          typeof fieldValue !== 'string' ||
          Number.isNaN(new Date(fieldValue).getTime())
        ) {
          errors.push(`${field.fieldName} 必须是日期时间`)
        }
        break
      case 'multi_select':
        if (!Array.isArray(fieldValue)) {
          errors.push(`${field.fieldName} 必须是多选数组`)
        }
        break
      default:
        if (typeof fieldValue !== 'string') {
          errors.push(`${field.fieldName} 必须是文本`)
        }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  }
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
      return value
        .map((item) => optionLabel(options, String(item)))
        .join('、')
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
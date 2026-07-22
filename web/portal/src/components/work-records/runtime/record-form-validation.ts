import type {
  WorkRecordField,
  WorkRecordRuntimeFormValue,
  WorkRecordStatus,
} from './types'

export type RecordFormErrors = Record<string, string>

export function validateRecordForm(
  form: WorkRecordRuntimeFormValue,
  fields: WorkRecordField[],
  targetStatus: WorkRecordStatus
): RecordFormErrors {
  const errors: RecordFormErrors = {}

  if (!form.templateId.trim()) {
    errors.templateId = '请选择工作类型'
  }

  if (!form.templateVersionId.trim()) {
    errors.templateVersionId = '请选择工作类型版本'
  }

  if (!form.title.trim()) {
    errors.title = '请输入记录标题'
  } else if (form.title.trim().length > 200) {
    errors.title = '记录标题不能超过 200 个字符'
  }

  if (!form.recordTime.trim()) {
    errors.recordTime = '请选择记录时间'
  } else if (!isLocalDateTime(form.recordTime)) {
    errors.recordTime = '记录时间格式无效'
  }

  const enforceRequired = targetStatus !== 'draft'

  for (const field of fields) {
    if (!field.enabled) continue

    const value = form.customData[field.fieldCode]
    const key = `custom.${field.fieldCode}`

    if (enforceRequired && field.required && isEmptyValue(value)) {
      errors[key] = `请填写${field.fieldName}`
      continue
    }

    if (isEmptyValue(value)) continue

    switch (field.fieldType) {
      case 'text':
      case 'textarea':
      case 'select':
      case 'user':
        if (typeof value !== 'string') {
          errors[key] = `${field.fieldName}必须是文本`
        }
        break

      case 'number':
        if (typeof value !== 'number' || !Number.isFinite(value)) {
          errors[key] = `${field.fieldName}必须是数字`
        }
        break

      case 'boolean':
        if (typeof value !== 'boolean') {
          errors[key] = `${field.fieldName}必须是布尔值`
        }
        break

      case 'multi_select':
        if (
          !Array.isArray(value) ||
          value.some((item) => typeof item !== 'string')
        ) {
          errors[key] = `${field.fieldName}格式无效`
        }
        break

      case 'date':
        if (typeof value !== 'string' || !isIsoDate(value)) {
          errors[key] = `${field.fieldName}必须是有效日期`
        }
        break

      case 'datetime':
        if (typeof value !== 'string' || !isOffsetDateTime(value)) {
          errors[key] = `${field.fieldName}必须是带时区的日期时间`
        }
        break
    }

    if (!errors[key]) {
      const ruleError = validateRules(field, value)
      if (ruleError) errors[key] = ruleError
    }
  }

  return errors
}

function validateRules(field: WorkRecordField, value: unknown) {
  const rules = parseRules(field.validationJson)
  if (typeof value === 'string') {
    if (rules.minLength !== undefined && value.length < rules.minLength) {
      return `${field.fieldName}不能少于 ${rules.minLength} 个字符`
    }
    if (rules.maxLength !== undefined && value.length > rules.maxLength) {
      return `${field.fieldName}不能超过 ${rules.maxLength} 个字符`
    }
    if (rules.pattern) {
      try {
        if (!new RegExp(rules.pattern).test(value)) {
          return `${field.fieldName}格式不符合要求`
        }
      } catch {
        return `${field.fieldName}校验规则无效`
      }
    }
  }
  if (typeof value === 'number') {
    if (rules.minimum !== undefined && value < rules.minimum) {
      return `${field.fieldName}不能小于 ${rules.minimum}`
    }
    if (rules.maximum !== undefined && value > rules.maximum) {
      return `${field.fieldName}不能大于 ${rules.maximum}`
    }
  }
  return undefined
}

function parseRules(value?: string): {
  minLength?: number
  maxLength?: number
  pattern?: string
  minimum?: number
  maximum?: number
} {
  if (!value) return {}
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed && typeof parsed === 'object'
      ? (parsed as ReturnType<typeof parseRules>)
      : {}
  } catch {
    return {}
  }
}

function isEmptyValue(value: unknown) {
  return (
    value === null ||
    value === undefined ||
    value === '' ||
    (typeof value === 'string' && !value.trim()) ||
    (Array.isArray(value) && value.length === 0)
  )
}

function isLocalDateTime(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(value)) {
    return false
  }
  return !Number.isNaN(new Date(value).getTime())
}

function isOffsetDateTime(value: string) {
  if (!/(Z|[+-]\d{2}:\d{2})$/.test(value)) {
    return false
  }
  return !Number.isNaN(Date.parse(value))
}

function isIsoDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])

  const parsed = new Date(Date.UTC(year, month - 1, day))

  return (
    parsed.getUTCFullYear() === year &&
    parsed.getUTCMonth() === month - 1 &&
    parsed.getUTCDate() === day
  )
}

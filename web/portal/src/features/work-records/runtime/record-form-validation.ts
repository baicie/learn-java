export type RecordFormStatus = 'draft' | 'processing' | 'done' | 'archived'

export type ValidatableRecordField = {
  fieldCode: string
  fieldName: string
  fieldType: string
  required: boolean
  enabled: boolean
}

export type RecordFormValue = {
  templateId: string
  templateVersionId: string
  title: string
  status: RecordFormStatus
  recordTime: string
  customData: Record<string, unknown>
}

export type RecordFormErrors = Record<string, string>

export function validateRecordForm(
  form: RecordFormValue,
  fields: ValidatableRecordField[]
): RecordFormErrors {
  const errors: RecordFormErrors = {}

  if (!form.templateId.trim()) {
    errors.templateId = '请选择记录模板'
  }

  if (!form.templateVersionId.trim()) {
    errors.templateVersionId = '请选择模板版本'
  }

  if (!form.title.trim()) {
    errors.title = '请输入记录标题'
  } else if (form.title.trim().length > 200) {
    errors.title = '记录标题不能超过 200 个字符'
  }

  if (!form.recordTime.trim()) {
    errors.recordTime = '请选择记录时间'
  } else if (Number.isNaN(new Date(form.recordTime).getTime())) {
    errors.recordTime = '记录时间格式无效'
  }

  const enforceRequired = form.status !== 'draft'

  for (const field of fields) {
    if (!field.enabled) continue

    const value = form.customData[field.fieldCode]

    if (enforceRequired && field.required && isEmptyValue(value)) {
      errors[`custom.${field.fieldCode}`] = `请填写${field.fieldName}`
      continue
    }

    if (isEmptyValue(value)) continue

    validateFieldType(field, value, errors)
  }

  return errors
}

function validateFieldType(
  field: ValidatableRecordField,
  value: unknown,
  errors: RecordFormErrors
) {
  const key = `custom.${field.fieldCode}`

  switch (field.fieldType.toLowerCase()) {
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
    case 'datetime':
      if (
        typeof value !== 'string' ||
        Number.isNaN(new Date(value).getTime())
      ) {
        errors[key] = `${field.fieldName}日期格式无效`
      }
      break
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

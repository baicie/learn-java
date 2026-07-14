import {
  type DesignerField,
  type SchemaDiffItem,
  type WorkRecordFieldType,
  WORK_RECORD_FIELD_TYPES,
  type WorkRecordVersionField,
} from './types'

const FIELD_CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]{0,63}$/

const RESERVED_FIELD_CODES = new Set([
  'id',
  'tenant_id',
  'template_id',
  'template_version_id',
  'title',
  'status',
  'owner_id',
  'creator_id',
  'record_time',
  'builtin_data_json',
  'custom_data_json',
  'row_version',
  'created_at',
  'updated_at',
  'deleted_at',
])

function newId(fallback: string): string {
  const browserCrypto =
    typeof globalThis !== 'undefined' ? globalThis.crypto : undefined
  return browserCrypto?.randomUUID?.() ?? fallback
}

export function newDesignerField(
  fieldType: WorkRecordFieldType,
  index: number
): DesignerField {
  const base = `${fieldType}_${Date.now().toString(36)}_${index}`
  return {
    id: newId(base),
    fieldName: defaultFieldName(fieldType),
    fieldCode: base.replace(/[^a-zA-Z0-9_]/g, '_'),
    fieldType,
    required: false,
    optionSource: 'static',
    dictCode: '',
    listVisible: false,
    filterable: false,
    exportable: true,
    statistical: false,
    sortOrder: index,
    enabled: true,
    locked: false,
    referenced: false,
  }
}

export function defaultFieldName(fieldType: WorkRecordFieldType) {
  const labels: Record<WorkRecordFieldType, string> = {
    text: '单行文本',
    textarea: '多行文本',
    number: '数字',
    date: '日期',
    datetime: '日期时间',
    select: '单选',
    multi_select: '多选',
    user: '用户',
    boolean: '开关',
  }
  return labels[fieldType]
}

export function validateDesignerFields(fields: DesignerField[]): string[] {
  const errors: string[] = []
  const seen = new Set<string>()

  for (const field of fields.filter((item) => item.enabled)) {
    if (!field.fieldName.trim()) {
      errors.push('字段名称不能为空')
    }

    if (!field.fieldCode.trim()) {
      errors.push(`${field.fieldName} 的字段编码不能为空`)
    } else if (!FIELD_CODE_PATTERN.test(field.fieldCode)) {
      errors.push(`${field.fieldName} 的字段编码不符合规则`)
    } else if (RESERVED_FIELD_CODES.has(field.fieldCode)) {
      errors.push(`${field.fieldName} 的字段编码是保留字`)
    }

    if (seen.has(field.fieldCode)) {
      errors.push(`字段编码重复：${field.fieldCode}`)
    }
    seen.add(field.fieldCode)

    if (!WORK_RECORD_FIELD_TYPES.includes(field.fieldType)) {
      errors.push(`不支持的字段类型：${field.fieldType}`)
    }

    if (field.optionSource === 'dict' && !field.dictCode.trim()) {
      errors.push(`${field.fieldName} 选择了字典绑定，但没有选择字典`)
    }

    if (field.optionSource === 'static' && field.dictCode.trim()) {
      errors.push(`${field.fieldName} 是静态选项，不应绑定字典`)
    }
  }

  return errors
}

export function buildWorkRecordSchema(fields: DesignerField[]) {
  const enabledFields = fields
    .filter((field) => field.enabled)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  const properties: Record<string, unknown> = {}
  const required: string[] = []

  for (const field of enabledFields) {
    if (field.required) {
      required.push(field.fieldCode)
    }

    properties[field.fieldCode] = {
      type: schemaType(field.fieldType),
      title: field.fieldName,
      'x-component': componentName(field.fieldType),
      'x-work-record': {
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        optionSource: field.optionSource,
        ...(field.optionSource === 'dict' && field.dictCode
          ? { dictCode: field.dictCode }
          : {}),
        listVisible: field.listVisible,
        filterable: field.filterable,
        exportable: field.exportable,
        statistical: field.statistical,
        sortOrder: field.sortOrder,
      },
    }
  }

  return {
    type: 'object',
    'x-work-record-schema-version': 1,
    required,
    properties,
  }
}

function buildDesignerJson(fields: DesignerField[]) {
  return {
    version: 1,
    layout: 'single_column',
    fields: fields.map((field) => ({
      id: field.id,
      fieldCode: field.fieldCode,
      sortOrder: field.sortOrder,
      enabled: field.enabled,
      locked: field.locked,
      referenced: field.referenced,
    })),
  }
}

export function schemaToJson(fields: DesignerField[]) {
  return JSON.stringify(buildWorkRecordSchema(fields), null, 2)
}

export function designerToJson(fields: DesignerField[]) {
  return JSON.stringify(buildDesignerJson(fields), null, 2)
}

function safeParseObject(json: string) {
  try {
    const parsed = JSON.parse(json || '{}')
    return parsed && typeof parsed === 'object'
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

export function parseDraftSchema(
  schemaJson: string,
  designerJson = '{}'
): DesignerField[] {
  if (!schemaJson?.trim()) return []

  const root = safeParseObject(schemaJson)
  const designer = safeParseObject(designerJson)
  const designerFields = Array.isArray(designer.fields) ? designer.fields : []

  const designerByCode = new Map<string, Record<string, unknown>>()
  for (const raw of designerFields) {
    if (raw && typeof raw === 'object') {
      const item = raw as Record<string, unknown>
      const code = String(item.fieldCode ?? '')
      if (code) {
        designerByCode.set(code, item)
      }
    }
  }

  const properties = root.properties
  if (!properties || typeof properties !== 'object') {
    return restoreDisabledDesignerFields(designerByCode)
  }

  const parsed: DesignerField[] = []

  for (const [propertyName, raw] of Object.entries(
    properties as Record<string, unknown>
  )) {
    if (!raw || typeof raw !== 'object') continue

    const fieldNode = raw as Record<string, unknown>
    const ext = fieldNode['x-work-record']

    if (!ext || typeof ext !== 'object') {
      continue
    }

    const extNode = ext as Record<string, unknown>
    const fieldCode = String(extNode.fieldCode ?? propertyName)
    const designerField = designerByCode.get(fieldCode)
    const fieldType = String(extNode.fieldType ?? 'text') as WorkRecordFieldType

    parsed.push({
      id: String(designerField?.id ?? fieldCode),
      fieldName: String(fieldNode.title ?? extNode.fieldName ?? fieldCode),
      fieldCode,
      fieldType: WORK_RECORD_FIELD_TYPES.includes(fieldType)
        ? fieldType
        : 'text',
      required:
        Array.isArray(root.required) && root.required.includes(propertyName),
      optionSource: extNode.optionSource === 'dict' ? 'dict' : 'static',
      dictCode: String(extNode.dictCode ?? ''),
      listVisible: Boolean(extNode.listVisible),
      filterable: Boolean(extNode.filterable),
      exportable:
        extNode.exportable === undefined ? true : Boolean(extNode.exportable),
      statistical: Boolean(extNode.statistical),
      sortOrder: Number(
        extNode.sortOrder ?? designerField?.sortOrder ?? parsed.length
      ),
      enabled:
        designerField?.enabled === undefined
          ? true
          : Boolean(designerField.enabled),
      locked: Boolean(designerField?.locked),
      referenced: Boolean(designerField?.referenced),
    })
  }

  const activeCodes = new Set(parsed.map((field) => field.fieldCode))
  for (const field of restoreDisabledDesignerFields(designerByCode)) {
    if (!activeCodes.has(field.fieldCode)) {
      parsed.push(field)
    }
  }

  return normalizeSortOrder(parsed)
}

function restoreDisabledDesignerFields(
  designerByCode: Map<string, Record<string, unknown>>
): DesignerField[] {
  const result: DesignerField[] = []

  for (const item of designerByCode.values()) {
    if (item.enabled !== false) continue

    const fieldType = String(item.fieldType ?? 'text') as WorkRecordFieldType
    result.push({
      id: String(item.id ?? item.fieldCode),
      fieldName: String(item.fieldName ?? item.fieldCode),
      fieldCode: String(item.fieldCode),
      fieldType: WORK_RECORD_FIELD_TYPES.includes(fieldType)
        ? fieldType
        : 'text',
      required: Boolean(item.required),
      optionSource: item.optionSource === 'dict' ? 'dict' : 'static',
      dictCode: String(item.dictCode ?? ''),
      listVisible: Boolean(item.listVisible),
      filterable: Boolean(item.filterable),
      exportable:
        item.exportable === undefined ? true : Boolean(item.exportable),
      statistical: Boolean(item.statistical),
      sortOrder: Number(item.sortOrder ?? result.length),
      enabled: false,
      locked: Boolean(item.locked),
      referenced: Boolean(item.referenced),
    })
  }

  return result
}

export function mergePublishedLocks(
  draftFields: DesignerField[],
  currentVersionFields: WorkRecordVersionField[] | undefined,
  referencedRecordCount: number,
  restoreMissingAsEnabled = false
): DesignerField[] {
  if (!currentVersionFields?.length) return normalizeSortOrder(draftFields)

  const publishedByCode = new Map(
    currentVersionFields.map((field) => [field.fieldCode, field])
  )

  const merged = draftFields.map((field) => {
    const published = publishedByCode.get(field.fieldCode)
    if (!published) return field

    return {
      ...field,
      fieldType: published.fieldType,
      locked: true,
      referenced: referencedRecordCount > 0,
      enabled: published.enabled ? field.enabled : false,
    }
  })

  for (const published of currentVersionFields) {
    if (!merged.some((field) => field.fieldCode === published.fieldCode)) {
      merged.push({
        id: published.id,
        fieldName: published.fieldName,
        fieldCode: published.fieldCode,
        fieldType: published.fieldType,
        required: published.required,
        optionSource: published.optionSource,
        dictCode: published.dictCode ?? '',
        listVisible: published.listVisible,
        filterable: published.filterable,
        exportable: published.exportable,
        statistical: published.statistical,
        sortOrder: published.sortOrder,
        enabled: restoreMissingAsEnabled && published.enabled,
        locked: true,
        referenced: referencedRecordCount > 0,
      })
    }
  }

  return normalizeSortOrder(merged)
}

export function normalizeSortOrder(fields: DesignerField[]) {
  return fields.map((field, index) => ({
    ...field,
    sortOrder: index,
  }))
}

export function diffFields(
  previous: WorkRecordVersionField[] | undefined,
  next: DesignerField[]
): SchemaDiffItem[] {
  if (!previous?.length) {
    return next
      .filter((field) => field.enabled)
      .map((field) => ({
        type: 'added',
        fieldCode: field.fieldCode,
        message: `新增字段 ${field.fieldName}（${field.fieldCode}）`,
      }))
  }

  const nextByCode = new Map(next.map((field) => [field.fieldCode, field]))
  const previousByCode = new Map(
    previous.map((field) => [field.fieldCode, field])
  )
  const result: SchemaDiffItem[] = []

  for (const field of next) {
    const old = previousByCode.get(field.fieldCode)
    if (!old && field.enabled) {
      result.push({
        type: 'added',
        fieldCode: field.fieldCode,
        message: `新增字段 ${field.fieldName}（${field.fieldCode}）`,
      })
      continue
    }

    if (old && !field.enabled) {
      result.push({
        type: 'disabled',
        fieldCode: field.fieldCode,
        message: `禁用字段 ${field.fieldName}（${field.fieldCode}）`,
      })
      continue
    }

    if (old && old.fieldType !== field.fieldType) {
      result.push({
        type: 'type_changed',
        fieldCode: field.fieldCode,
        message: `字段类型变化 ${field.fieldCode}: ${old.fieldType} → ${field.fieldType}`,
      })
    }

    if (
      old &&
      (old.listVisible !== field.listVisible ||
        old.filterable !== field.filterable ||
        old.exportable !== field.exportable ||
        old.statistical !== field.statistical)
    ) {
      result.push({
        type: 'flag_changed',
        fieldCode: field.fieldCode,
        message: `字段展示/筛选/导出/统计属性变化：${field.fieldCode}`,
      })
    }
  }

  for (const old of previous) {
    if (!nextByCode.has(old.fieldCode)) {
      result.push({
        type: 'removed',
        fieldCode: old.fieldCode,
        message: `字段从草稿中移除：${old.fieldName}（发布时应转为禁用）`,
      })
    }
  }

  return result
}

function schemaType(fieldType: WorkRecordFieldType) {
  if (fieldType === 'number') return 'number'
  if (fieldType === 'boolean') return 'boolean'
  if (fieldType === 'multi_select') return 'array'
  return 'string'
}

function componentName(fieldType: WorkRecordFieldType) {
  const components: Record<WorkRecordFieldType, string> = {
    text: 'Input',
    textarea: 'Textarea',
    number: 'NumberPicker',
    date: 'DatePicker',
    datetime: 'DateTimePicker',
    select: 'Select',
    multi_select: 'MultiSelect',
    user: 'UserSelect',
    boolean: 'Switch',
  }
  return components[fieldType]
}

import {
  workRecordFieldTypes,
  type WorkRecordFieldType,
} from '@/features/work-records/data/field-types'
import { isReservedFieldCode } from '@/features/work-records/data/reserved-field-codes'

export const designerFieldTypes = workRecordFieldTypes

type JsonObject = Record<string, unknown>

export type FieldDescriptor = {
  fieldCode: string
  fieldType: WorkRecordFieldType
  title: string
  required: boolean
  listVisible: boolean
  filterable: boolean
  statistical: boolean
  optionSource: 'static' | 'dict'
  dictCode?: string
  options?: { label: string; value: string }[]
}

function normalizeCode(code: string): string {
  const trimmed = code.trim()
  if (!trimmed) {
    throw new Error('field code must not be empty')
  }
  if (isReservedFieldCode(trimmed)) {
    throw new Error(`reserved fieldCode: ${trimmed}`)
  }
  return trimmed
}

export function defaultFieldDescriptor(
  fieldCode: string,
  overrides: Partial<FieldDescriptor> = {}
): FieldDescriptor {
  const code = normalizeCode(fieldCode)
  const base: FieldDescriptor = {
    fieldCode: code,
    fieldType: 'text',
    title: code,
    required: false,
    listVisible: false,
    filterable: false,
    statistical: false,
    optionSource: 'static',
  }
  return { ...base, ...overrides, fieldCode: code }
}

function buildExtension(descriptor: FieldDescriptor): JsonObject {
  const {
    fieldCode,
    fieldType,
    optionSource,
    dictCode,
    listVisible,
    filterable,
    statistical,
  } = descriptor

  if (optionSource === 'dict' && !dictCode) {
    throw new Error(`dictCode is required for ${fieldCode}`)
  }

  const extension: JsonObject = {
    fieldCode,
    fieldType,
    optionSource,
    listVisible,
    filterable,
    statistical,
  }
  if (optionSource === 'dict') {
    extension.dictCode = dictCode
  }
  return extension
}

export function defaultSchemaProperty(
  descriptor: FieldDescriptor
): JsonObject {
  const { fieldType, title, required } = descriptor
  const extension = buildExtension(descriptor)

  switch (fieldType) {
    case 'text':
    case 'user':
      return {
        type: 'string',
        title,
        required,
        'x-work-record': extension,
      }
    case 'textarea':
      return {
        type: 'string',
        title,
        required,
        'x-component': 'TextArea',
        'x-work-record': extension,
      }
    case 'number':
      return {
        type: 'number',
        title,
        required,
        'x-work-record': extension,
      }
    case 'date':
      return {
        type: 'string',
        title,
        required,
        format: 'date',
        'x-work-record': extension,
      }
    case 'datetime':
      return {
        type: 'string',
        title,
        required,
        format: 'date-time',
        'x-work-record': extension,
      }
    case 'boolean':
      return {
        type: 'boolean',
        title,
        required,
        'x-work-record': extension,
      }
    case 'select':
    case 'multi_select': {
      const property: JsonObject = {
        type: fieldType === 'multi_select' ? 'array' : 'string',
        title,
        required,
        'x-work-record': extension,
      }
      if (descriptor.options && descriptor.options.length > 0) {
        property.enum = descriptor.options
      }
      return property
    }
    default:
      throw new Error(`unknown fieldType: ${fieldType satisfies never}`)
  }
}

export function defaultFieldSchema(
  fieldCode: string,
  fieldType: WorkRecordFieldType,
  overrides: Partial<FieldDescriptor> = {}
): JsonObject {
  const descriptor = defaultFieldDescriptor(fieldCode, {
    fieldType,
    ...overrides,
  })
  return defaultSchemaProperty(descriptor)
}

export function isSelectLike(fieldType: WorkRecordFieldType): boolean {
  return fieldType === 'select' || fieldType === 'multi_select'
}

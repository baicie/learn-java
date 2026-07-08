import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'
import { isReservedFieldCode } from '@/features/work-records/data/reserved-field-codes'
import {
  defaultFieldSchema,
  defaultSchemaProperty,
  isSelectLike,
  type FieldDescriptor,
} from '@/features/work-records/data/designer/field-types'

export type DesignerSchema = {
  type: 'object'
  properties: Record<string, Record<string, unknown>>
} & Record<string, unknown>

export class SchemaBuilderError extends Error {
  override readonly name = 'SchemaBuilderError'
}

function asSchema(value: unknown): DesignerSchema {
  if (
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    (value as { type?: unknown }).type !== 'object'
  ) {
    throw new SchemaBuilderError(
      'schema must be a Formily-compatible object schema'
    )
  }
  const properties = (value as { properties?: unknown }).properties
  if (
    properties === undefined ||
    properties === null ||
    typeof properties !== 'object' ||
    Array.isArray(properties)
  ) {
    return { ...(value as DesignerSchema), type: 'object', properties: {} }
  }
  return { ...(value as DesignerSchema), type: 'object', properties }
}

function lenientAsSchema(value: unknown): DesignerSchema {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return { type: 'object', properties: {} }
  }
  const obj = value as { type?: unknown; properties?: unknown }
  const properties =
    obj.properties !== undefined &&
    obj.properties !== null &&
    typeof obj.properties === 'object' &&
    !Array.isArray(obj.properties)
      ? (obj.properties as Record<string, unknown>)
      : {}
  if (obj.type !== 'object') {
    return { ...obj, type: 'object', properties }
  }
  return { ...obj, type: 'object', properties }
}

export function emptySchema(): DesignerSchema {
  return { type: 'object', properties: {} }
}

function normalizeCode(code: string): string {
  const trimmed = code.trim()
  if (!trimmed) {
    throw new SchemaBuilderError('field code must not be empty')
  }
  if (isReservedFieldCode(trimmed)) {
    throw new SchemaBuilderError(`reserved fieldCode: ${trimmed}`)
  }
  return trimmed
}

function validateDescriptor(descriptor: FieldDescriptor): FieldDescriptor {
  if (descriptor.optionSource === 'dict' && !descriptor.dictCode) {
    throw new SchemaBuilderError(
      `dictCode is required for ${descriptor.fieldCode}`
    )
  }
  if (descriptor.optionSource === 'dict' && !isSelectLike(descriptor.fieldType)) {
    throw new SchemaBuilderError(
      `dict optionSource requires select or multi_select, got ${descriptor.fieldType}`
    )
  }
  return descriptor
}

export type AddFieldOptions = Partial<
  Omit<FieldDescriptor, 'fieldCode' | 'fieldType'>
>

function ensureSchemaInput(schema: DesignerSchema | null | undefined): void {
  if (!schema) {
    throw new SchemaBuilderError('schema is required')
  }
}

export function addField(
  schema: DesignerSchema,
  fieldType: WorkRecordFieldType,
  rawFieldCode: string,
  options: AddFieldOptions = {}
): DesignerSchema {
  ensureSchemaInput(schema)
  const fieldCode = normalizeCode(rawFieldCode)
  const next = asSchema({
    ...(schema ?? {}),
    properties: { ...(schema?.properties ?? {}) },
  })
  if (next.properties[fieldCode]) {
    throw new SchemaBuilderError(`fieldCode already exists: ${fieldCode}`)
  }
  const descriptor = validateDescriptor({
    fieldCode,
    fieldType,
    title: options.title ?? fieldCode,
    required: options.required ?? false,
    listVisible: options.listVisible ?? false,
    filterable: options.filterable ?? false,
    statistical: options.statistical ?? false,
    optionSource: options.optionSource ?? 'static',
    dictCode: options.dictCode,
    options: options.options,
  })
  next.properties[fieldCode] = defaultSchemaProperty(descriptor)
  return next
}

export function removeField(
  schema: DesignerSchema,
  fieldCode: string
): DesignerSchema {
  ensureSchemaInput(schema)
  if (!schema.properties[fieldCode]) {
    throw new SchemaBuilderError(`fieldCode not found: ${fieldCode}`)
  }
  const next = asSchema({
    ...schema,
    properties: { ...schema.properties },
  })
  delete next.properties[fieldCode]
  return next
}

export function listFieldCodes(schema: DesignerSchema): string[] {
  return Object.keys(schema.properties)
}

function reorder(
  schema: DesignerSchema,
  fieldCode: string,
  targetIndex: number
): DesignerSchema {
  const order = listFieldCodes(schema)
  const currentIndex = order.indexOf(fieldCode)
  if (currentIndex === -1) {
    throw new SchemaBuilderError(`fieldCode not found: ${fieldCode}`)
  }
  if (targetIndex < 0 || targetIndex >= order.length) {
    throw new SchemaBuilderError(
      `targetIndex out of range: ${targetIndex} (size ${order.length})`
    )
  }
  if (targetIndex === currentIndex) return asSchema(schema)
  order.splice(currentIndex, 1)
  order.splice(targetIndex, 0, fieldCode)
  const next = asSchema({
    ...schema,
    properties: {},
  })
  for (const code of order) {
    next.properties[code] = schema.properties[code]
  }
  return next
}

export function moveField(
  schema: DesignerSchema,
  fieldCode: string,
  toIndex: number
): DesignerSchema {
  ensureSchemaInput(schema)
  return reorder(schema, fieldCode, toIndex)
}

export function moveFieldUp(
  schema: DesignerSchema,
  fieldCode: string
): DesignerSchema {
  const currentIndex = listFieldCodes(schema).indexOf(fieldCode)
  if (currentIndex <= 0) return asSchema(schema)
  return reorder(schema, fieldCode, currentIndex - 1)
}

export function moveFieldDown(
  schema: DesignerSchema,
  fieldCode: string
): DesignerSchema {
  const order = listFieldCodes(schema)
  const currentIndex = order.indexOf(fieldCode)
  if (currentIndex < 0 || currentIndex >= order.length - 1) return asSchema(schema)
  return reorder(schema, fieldCode, currentIndex + 1)
}

export type UpdateFieldPatch = Partial<
  Omit<FieldDescriptor, 'fieldCode' | 'fieldType'>
> & {
  fieldType?: WorkRecordFieldType
  fieldCode?: string
}

export function updateField(
  schema: DesignerSchema,
  currentFieldCode: string,
  patch: UpdateFieldPatch
): DesignerSchema {
  const target = schema.properties[currentFieldCode]
  if (!target) {
    throw new SchemaBuilderError(`fieldCode not found: ${currentFieldCode}`)
  }
  const extension = (target['x-work-record'] ?? {}) as Record<string, unknown>
  const baseDescriptor: FieldDescriptor = {
    fieldCode: (extension.fieldCode as string) ?? currentFieldCode,
    fieldType: (extension.fieldType as WorkRecordFieldType) ?? 'text',
    title: (target.title as string) ?? currentFieldCode,
    required: Boolean(target.required),
    listVisible: Boolean(extension.listVisible),
    filterable: Boolean(extension.filterable),
    statistical: Boolean(extension.statistical),
    optionSource: (extension.optionSource as 'static' | 'dict') ?? 'static',
    dictCode: extension.dictCode as string | undefined,
    options: Array.isArray(target.enum)
      ? (target.enum as { label: string; value: string }[])
      : undefined,
  }

  const nextCode =
    patch.fieldCode !== undefined
      ? normalizeCode(patch.fieldCode)
      : baseDescriptor.fieldCode

  const merged: FieldDescriptor = {
    ...baseDescriptor,
    ...patch,
    fieldCode: nextCode,
    fieldType: patch.fieldType ?? baseDescriptor.fieldType,
  }

  const descriptor = validateDescriptor(merged)
  const rebuilt = defaultSchemaProperty(descriptor)

  const next = asSchema({
    ...schema,
    properties: { ...schema.properties },
  })

  if (nextCode !== currentFieldCode) {
    if (next.properties[nextCode]) {
      throw new SchemaBuilderError(
        `cannot rename ${currentFieldCode} to existing fieldCode ${nextCode}`
      )
    }
    delete next.properties[currentFieldCode]
  }
  next.properties[nextCode] = rebuilt
  return next
}

export function setDictionaryCode(
  schema: DesignerSchema,
  fieldCode: string,
  dictCode: string | null
): DesignerSchema {
  const target = schema.properties[fieldCode]
  if (!target) {
    throw new SchemaBuilderError(`fieldCode not found: ${fieldCode}`)
  }
  const extension = (target['x-work-record'] ?? {}) as Record<string, unknown>
  const fieldType = (extension.fieldType as WorkRecordFieldType) ?? 'text'
  if (dictCode !== null && !isSelectLike(fieldType)) {
    throw new SchemaBuilderError(
      `dictCode requires select or multi_select, got ${fieldType}`
    )
  }

  return updateField(schema, fieldCode, {
    optionSource: dictCode ? 'dict' : 'static',
    dictCode: dictCode ?? undefined,
  })
}

export function normalizeSchema(value: unknown): DesignerSchema {
  return lenientAsSchema(value ?? {})
}

export function toFieldSchema(descriptor: FieldDescriptor) {
  return defaultFieldSchema(
    descriptor.fieldCode,
    descriptor.fieldType,
    descriptor
  )
}

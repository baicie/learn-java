import { z } from 'zod'
import { workRecordFieldTypes } from './field-types'

export const apiResponseSchema = <T extends z.ZodType>(schema: T) =>
  z.object({
    success: z.boolean().optional(),
    data: schema,
    errorCode: z.string().nullable().optional(),
    message: z.string().nullable().optional(),
    timestamp: z.string().optional(),
  })

export const workRecordTemplateSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  name: z.string(),
  code: z.string(),
  description: z.string().nullable(),
  enabled: z.boolean(),
  schemaJson: z.string(),
  designerJson: z.string().nullable().default('{}'),
  createdBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const workRecordFieldSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  fieldName: z.string(),
  fieldCode: z.string(),
  fieldType: z.enum(workRecordFieldTypes),
  required: z.boolean(),
  defaultValue: z.string().nullable(),
  optionSource: z.enum(['static', 'dict']),
  dictCode: z.string().nullable(),
  optionsJson: z.string(),
  listVisible: z.boolean(),
  filterable: z.boolean(),
  statistical: z.boolean(),
  sortOrder: z.number(),
  enabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const workRecordSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  templateId: z.string(),
  title: z.string(),
  status: z.string(),
  ownerId: z.string().nullable(),
  creatorId: z.string(),
  recordTime: z.string(),
  builtinDataJson: z.string(),
  customDataJson: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const workRecordPageSchema = z.object({
  total: z.number(),
  page: z.number(),
  size: z.number(),
  items: z.array(workRecordSchema),
})

// ---- List metadata types ----

export const recordListColumnSchema = z.object({
  fieldCode: z.string(),
  label: z.string(),
  fieldType: z.string(),
  listVisible: z.boolean(),
  filterable: z.boolean(),
  exportable: z.boolean(),
  statistical: z.boolean(),
  sortOrder: z.number(),
})

export const recordListFilterFieldSchema = z.object({
  fieldCode: z.string(),
  label: z.string(),
  fieldType: z.string(),
  operators: z.array(z.string()),
  dictionaryCode: z.string().nullable(),
  exportable: z.boolean(),
  options: z.array(z.object({ value: z.string(), label: z.string() })),
})

export const recordListTemplateSchema = z.object({
  id: z.string(),
  name: z.string(),
  enabled: z.boolean(),
})

export const recordListMetadataSchema = z.object({
  templates: z.array(recordListTemplateSchema),
  columns: z.array(recordListColumnSchema),
  filterFields: z.array(recordListFilterFieldSchema),
  maxExportRows: z.number(),
})

// ---- Filter types ----

export const dynamicFilterOperatorSchema = z.enum([
  'eq',
  'in',
  'contains',
  'gte',
  'lte',
  'between',
  'exists',
])

export const dynamicFilterSchema = z.object({
  fieldCode: z.string(),
  operator: dynamicFilterOperatorSchema,
  value: z.union([z.string(), z.number(), z.boolean()]).nullable(),
  values: z.array(z.union([z.string(), z.number(), z.boolean()])).nullable(),
})

export type DynamicFilter = z.infer<typeof dynamicFilterSchema>
export type DynamicFilterOperator = z.infer<typeof dynamicFilterOperatorSchema>
export type RecordListMetadata = z.infer<typeof recordListMetadataSchema>
export type RecordListColumn = z.infer<typeof recordListColumnSchema>
export type RecordListFilterField = z.infer<typeof recordListFilterFieldSchema>
export type RecordListTemplate = z.infer<typeof recordListTemplateSchema>

export type WorkRecordTemplate = z.infer<typeof workRecordTemplateSchema>
export type WorkRecordField = z.infer<typeof workRecordFieldSchema>
export type WorkRecord = z.infer<typeof workRecordSchema>
export type WorkRecordPage = z.infer<typeof workRecordPageSchema>

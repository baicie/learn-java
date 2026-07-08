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

export type WorkRecordTemplate = z.infer<typeof workRecordTemplateSchema>
export type WorkRecordField = z.infer<typeof workRecordFieldSchema>
export type WorkRecord = z.infer<typeof workRecordSchema>
export type WorkRecordPage = z.infer<typeof workRecordPageSchema>

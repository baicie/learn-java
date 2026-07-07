import { z } from 'zod'

export const dictTypeSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictCode: z.string(),
  dictName: z.string(),
  description: z.string().nullable(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
  createdBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const dictItemSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictTypeId: z.string(),
  itemLabel: z.string(),
  itemValue: z.string(),
  color: z.string().nullable(),
  icon: z.string().nullable(),
  description: z.string().nullable(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
  extraJson: z.string().nullable(),
  createdBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const apiResponseSchema = <T extends z.ZodType>(schema: T) =>
  z.object({
    success: z.boolean().optional(),
    data: schema,
    errorCode: z.string().nullable().optional(),
    message: z.string().nullable().optional(),
    timestamp: z.string().optional(),
  })

export type DictType = z.infer<typeof dictTypeSchema>
export type DictItem = z.infer<typeof dictItemSchema>

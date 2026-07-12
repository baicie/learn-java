import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

export const dictTypeSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictCode: z.string(),
  dictName: z.string(),
  description: z.string().nullable().optional(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
  createdBy: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const dictItemSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  dictTypeId: z.string(),
  itemLabel: z.string(),
  itemValue: z.string(),
  color: z.string().nullable().optional(),
  icon: z.string().nullable().optional(),
  description: z.string().nullable().optional(),
  systemBuiltin: z.boolean(),
  enabled: z.boolean(),
  sortOrder: z.number(),
  extraJson: z.string(),
  createdBy: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type DictType = z.infer<typeof dictTypeSchema>
export type DictItem = z.infer<typeof dictItemSchema>

export async function listDictTypes(
  includeDisabled = true
): Promise<DictType[]> {
  const { data } = await apiClient.get('/api/platform/dictionaries', {
    params: { includeDisabled },
  })
  return apiResponseSchema(z.array(dictTypeSchema)).parse(data).data
}

export async function createDictType(input: {
  dictCode: string
  dictName: string
  description?: string
  enabled?: boolean
  sortOrder?: number
}) {
  const { data } = await apiClient.post('/api/platform/dictionaries', input)
  return apiResponseSchema(dictTypeSchema).parse(data).data
}

export async function updateDictType(
  dictCode: string,
  input: {
    dictName?: string
    description?: string
    enabled?: boolean
    sortOrder?: number
  }
) {
  const { data } = await apiClient.put(
    `/api/platform/dictionaries/${dictCode}`,
    input
  )
  return apiResponseSchema(dictTypeSchema).parse(data).data
}

export async function disableDictType(dictCode: string) {
  const { data } = await apiClient.delete(
    `/api/platform/dictionaries/${dictCode}`
  )
  return apiResponseSchema(dictTypeSchema).parse(data).data
}

export async function listDictItems(
  dictCode: string,
  includeDisabled = true
): Promise<DictItem[]> {
  const { data } = await apiClient.get(
    `/api/platform/dictionaries/${dictCode}/items`,
    {
      params: { includeDisabled },
    }
  )
  return apiResponseSchema(z.array(dictItemSchema)).parse(data).data
}

export async function createDictItem(
  dictCode: string,
  input: {
    itemLabel: string
    itemValue: string
    color?: string
    icon?: string
    description?: string
    sortOrder?: number
    enabled?: boolean
    extraJson?: string
  }
) {
  const { data } = await apiClient.post(
    `/api/platform/dictionaries/${dictCode}/items`,
    input
  )
  return apiResponseSchema(dictItemSchema).parse(data).data
}

export async function updateDictItem(
  dictCode: string,
  itemId: string,
  input: {
    itemLabel?: string
    color?: string
    icon?: string
    description?: string
    sortOrder?: number
    enabled?: boolean
  }
) {
  const { data } = await apiClient.put(
    `/api/platform/dictionaries/${dictCode}/items/${itemId}`,
    input
  )
  return apiResponseSchema(dictItemSchema).parse(data).data
}

export async function disableDictItem(dictCode: string, itemId: string) {
  const { data } = await apiClient.delete(
    `/api/platform/dictionaries/${dictCode}/items/${itemId}`
  )
  return apiResponseSchema(dictItemSchema).parse(data).data
}

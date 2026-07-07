import { z } from 'zod'
import { workRecordHttp } from '@/features/work-records/api/http'
import {
  apiResponseSchema,
  dictItemSchema,
  dictTypeSchema,
  type DictItem,
  type DictType,
} from './data/schema'

export async function listDictTypes(): Promise<DictType[]> {
  const { data } = await workRecordHttp.get('/api/platform/dictionaries')
  return apiResponseSchema(z.array(dictTypeSchema)).parse(data).data
}

export async function listDictItems(
  dictCode: string,
  includeDisabled = false
): Promise<DictItem[]> {
  const { data } = await workRecordHttp.get(
    `/api/platform/dictionaries/${dictCode}/items`,
    { params: { includeDisabled } }
  )
  return apiResponseSchema(z.array(dictItemSchema)).parse(data).data
}

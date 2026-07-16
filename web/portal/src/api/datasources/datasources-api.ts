import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import { datasourceSchema, syncRunSchema } from '@/lib/datasources/datasource'

export type CreateDatasourceInput = {
  type: 'zabbix'
  name: string
  zabbix: {
    endpoint: string
    username?: string
    password?: string
    apiToken?: string
    connectTimeoutSeconds?: number
    readTimeoutSeconds?: number
  }
}

export async function listDatasources() {
  const { data } = await apiClient.get('/api/datasources')
  return apiResponseSchema(z.array(datasourceSchema)).parse(data).data
}

export async function createDatasource(input: CreateDatasourceInput) {
  const { data } = await apiClient.post('/api/datasources', input)
  return apiResponseSchema(datasourceSchema).parse(data).data
}

export async function testDatasource(id: string) {
  const { data } = await apiClient.post(`/api/datasources/${id}/test`)
  return apiResponseSchema(
    z.object({
      success: z.boolean(),
      message: z.string(),
      version: z.string().nullish(),
    })
  ).parse(data).data
}

export async function syncDatasource(id: string) {
  const { data } = await apiClient.post(`/api/datasources/${id}/sync`)
  return apiResponseSchema(
    z.object({ runId: z.string(), status: z.literal('pending') })
  ).parse(data).data
}

export async function listSyncRuns(id: string) {
  const { data } = await apiClient.get(`/api/datasources/${id}/sync-runs`)
  return apiResponseSchema(z.array(syncRunSchema)).parse(data).data
}

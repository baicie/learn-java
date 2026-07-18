import { z } from 'zod'
import { aiModelSchema } from '@/lib/ai-models/ai-model'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

export type SaveAiModelInput = {
  provider: 'deepseek'
  name: string
  modelName: string
  baseUrl: string
  apiKey: string
  enabled: boolean
}

const testResultSchema = z.object({
  success: z.boolean(),
  message: z.string(),
  testedAt: z.string().nullish(),
})

export async function listAiModels() {
  const { data } = await apiClient.get('/api/ai/models')
  return apiResponseSchema(z.array(aiModelSchema)).parse(data).data
}

export async function createAiModel(input: SaveAiModelInput) {
  const { data } = await apiClient.post('/api/ai/models', input)
  return apiResponseSchema(aiModelSchema).parse(data).data
}

export async function updateAiModel(id: string, input: SaveAiModelInput) {
  const { data } = await apiClient.put(`/api/ai/models/${id}`, input)
  return apiResponseSchema(aiModelSchema).parse(data).data
}

export async function deleteAiModel(id: string) {
  await apiClient.delete(`/api/ai/models/${id}`)
}

export async function testAiModel(id: string) {
  const { data } = await apiClient.post(`/api/ai/models/${id}/test`)
  return apiResponseSchema(testResultSchema).parse(data).data
}

export async function setDefaultAiModel(id: string) {
  const { data } = await apiClient.post(`/api/ai/models/${id}/default`)
  return apiResponseSchema(aiModelSchema).parse(data).data
}

import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'
import {
  assetIdentitySchema,
  assetImportRowPageSchema,
  assetImportSchema,
  assetPageSchema,
  assetRelationSchema,
  assetSchema,
  assetSourceSchema,
} from '@/lib/assets/asset'

export type AssetSearch = {
  page: number
  pageSize: number
  keyword?: string
  assetType?: string
  sourceType?: string
  status?: string
}

export type AssetInput = {
  assetType: string
  name: string
  displayName?: string
  description?: string
  environment?: string
  ip?: string
  site?: string
  ownerTeam?: string
  criticality?: string
  status?: string
  tags?: Record<string, unknown>
  identities?: Array<{
    identityType: string
    scopeKey: string
    identityValue: string
    verified: boolean
  }>
  version?: number
}

export async function listAssets(search: AssetSearch) {
  const { data } = await apiClient.get('/api/assets', { params: search })
  return apiResponseSchema(assetPageSchema).parse(data).data
}

export async function getAsset(id: string) {
  const { data } = await apiClient.get(`/api/assets/${id}`)
  return apiResponseSchema(assetSchema).parse(data).data
}

export async function createAsset(input: AssetInput) {
  const { data } = await apiClient.post('/api/assets', input)
  return apiResponseSchema(assetSchema).parse(data).data
}

export async function updateAsset(id: string, input: AssetInput) {
  const { data } = await apiClient.put(`/api/assets/${id}`, input)
  return apiResponseSchema(assetSchema).parse(data).data
}

export async function archiveAsset(id: string, version: number) {
  await apiClient.post(`/api/assets/${id}/archive`, undefined, {
    params: { version },
  })
}

export async function listAssetSources(id: string) {
  const { data } = await apiClient.get(`/api/assets/${id}/sources`)
  return apiResponseSchema(z.array(assetSourceSchema)).parse(data).data
}

export async function listAssetIdentities(id: string) {
  const { data } = await apiClient.get(`/api/assets/${id}/identities`)
  return apiResponseSchema(z.array(assetIdentitySchema)).parse(data).data
}

export async function listAssetRelations(id: string) {
  const { data } = await apiClient.get(`/api/assets/${id}/relations`)
  return apiResponseSchema(z.array(assetRelationSchema)).parse(data).data
}

export async function previewAssetImport(file: File, sourceInstanceId: string) {
  const form = new FormData()
  form.append('file', file)
  form.append('sourceInstanceId', sourceInstanceId)
  const { data } = await apiClient.post('/api/assets/imports/preview', form)
  return apiResponseSchema(assetImportSchema).parse(data).data
}

export async function listAssetImportRows(jobId: string, status?: string) {
  const { data } = await apiClient.get(`/api/assets/imports/${jobId}/rows`, {
    params: { page: 1, pageSize: 100, status },
  })
  return apiResponseSchema(assetImportRowPageSchema).parse(data).data
}

export async function confirmAssetImport(jobId: string) {
  const { data } = await apiClient.post(`/api/assets/imports/${jobId}/confirm`)
  return apiResponseSchema(assetImportSchema).parse(data).data
}

export async function downloadAssetTemplate() {
  const { data } = await apiClient.get('/api/assets/imports/template', {
    responseType: 'blob',
  })
  return data as Blob
}

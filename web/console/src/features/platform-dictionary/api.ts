import { apiRequest } from '../../api/client'

import type {
  CreateDictItemPayload,
  CreateDictTypePayload,
  DictItemRecord,
  DictTypeRecord,
  UpdateDictItemPayload,
  UpdateDictTypePayload,
} from './types'

export function listDictionaries() {
  return apiRequest<DictTypeRecord[]>('/api/platform/dictionaries')
}

export function createDictType(payload: CreateDictTypePayload) {
  return apiRequest<DictTypeRecord>('/api/platform/dictionaries', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDictType(dictCode: string, payload: UpdateDictTypePayload) {
  return apiRequest<DictTypeRecord>(`/api/platform/dictionaries/${encodeURIComponent(dictCode)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function listDictItems(dictCode: string) {
  return apiRequest<DictItemRecord[]>(
    `/api/platform/dictionaries/${encodeURIComponent(dictCode)}/items`,
  )
}

export function createDictItem(dictCode: string, payload: CreateDictItemPayload) {
  return apiRequest<DictItemRecord>(
    `/api/platform/dictionaries/${encodeURIComponent(dictCode)}/items`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function updateDictItem(dictCode: string, itemId: string, payload: UpdateDictItemPayload) {
  return apiRequest<DictItemRecord>(
    `/api/platform/dictionaries/${encodeURIComponent(dictCode)}/items/${encodeURIComponent(itemId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
  )
}

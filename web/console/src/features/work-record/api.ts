import { apiRequest, downloadFile } from '../../api/client'

import type {
  CreateFieldPayload,
  CreateTemplatePayload,
  CreateWorkRecordPayload,
  PageResult,
  UpdateFieldPayload,
  UpdateTemplatePayload,
  UpdateWorkRecordPayload,
  WorkRecord,
  WorkRecordField,
  WorkRecordTemplate,
} from './types'

export function listTemplates() {
  return apiRequest<WorkRecordTemplate[]>('/api/work-record/templates')
}

export function createTemplate(payload: CreateTemplatePayload) {
  return apiRequest<WorkRecordTemplate>('/api/work-record/templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateTemplate(templateId: string, payload: UpdateTemplatePayload) {
  return apiRequest<WorkRecordTemplate>(
    `/api/work-record/templates/${encodeURIComponent(templateId)}`,
    { method: 'PUT', body: JSON.stringify(payload) },
  )
}

export function listTemplateFields(templateId: string) {
  return apiRequest<WorkRecordField[]>(
    `/api/work-record/templates/${encodeURIComponent(templateId)}/fields`,
  )
}

export function createTemplateField(templateId: string, payload: CreateFieldPayload) {
  return apiRequest<WorkRecordField>(
    `/api/work-record/templates/${encodeURIComponent(templateId)}/fields`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export function updateTemplateField(
  templateId: string,
  fieldId: string,
  payload: UpdateFieldPayload,
) {
  return apiRequest<WorkRecordField>(
    `/api/work-record/templates/${encodeURIComponent(templateId)}/fields/${encodeURIComponent(fieldId)}`,
    { method: 'PUT', body: JSON.stringify(payload) },
  )
}

export function deleteTemplateField(templateId: string, fieldId: string) {
  return apiRequest<void>(
    `/api/work-record/templates/${encodeURIComponent(templateId)}/fields/${encodeURIComponent(fieldId)}`,
    { method: 'DELETE' },
  )
}

export function listWorkRecords(
  params: { ownerId?: string; status?: string; page?: number; size?: number } = {},
) {
  const query = new URLSearchParams()
  if (params.ownerId) query.set('ownerId', params.ownerId)
  if (params.status) query.set('status', params.status)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query}` : ''
  return apiRequest<PageResult<WorkRecord>>(`/api/work-record/records${suffix}`)
}

export function getWorkRecord(recordId: string) {
  return apiRequest<WorkRecord>(`/api/work-record/records/${encodeURIComponent(recordId)}`)
}

export function createWorkRecord(payload: CreateWorkRecordPayload) {
  return apiRequest<WorkRecord>('/api/work-record/records', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateWorkRecord(recordId: string, payload: UpdateWorkRecordPayload) {
  return apiRequest<WorkRecord>(`/api/work-record/records/${encodeURIComponent(recordId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteWorkRecord(recordId: string) {
  return apiRequest<void>(`/api/work-record/records/${encodeURIComponent(recordId)}`, {
    method: 'DELETE',
  })
}

export const WORK_RECORD_EXPORT_URL = '/api/work-record/records/export'

export function exportWorkRecordsCsv(params: { ownerId?: string; status?: string } = {}) {
  const query = new URLSearchParams()
  if (params.ownerId) query.set('ownerId', params.ownerId)
  if (params.status) query.set('status', params.status)
  const suffix = query.toString() ? `?${query}` : ''
  return downloadFile(
    `${WORK_RECORD_EXPORT_URL}${suffix}`,
    `work-records-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`,
  )
}

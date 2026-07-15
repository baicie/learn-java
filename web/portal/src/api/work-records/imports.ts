import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

const XLSX_CONTENT_TYPE =
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

const preparedUploadSchema = z.object({
  uploadId: z.string(),
  uploadUrl: z.string().url(),
  expiresAt: z.string(),
})

export type SubmitImportOptions = {
  templateId: string
  templateVersionId: string
  defaultStatus: 'draft' | 'done'
  stopOnError: boolean
}

export async function importWorkRecords(
  file: File,
  options: SubmitImportOptions
): Promise<string> {
  const prepared = await apiClient.post('/api/work-record/imports/uploads', {
    originalFileName: file.name,
    contentType: XLSX_CONTENT_TYPE,
    sizeBytes: file.size,
  })
  const upload = apiResponseSchema(preparedUploadSchema).parse(
    prepared.data
  ).data

  const uploaded = await fetch(upload.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': XLSX_CONTENT_TYPE },
    body: file,
  })
  if (!uploaded.ok) {
    throw new Error(`Excel 上传失败（${uploaded.status}）`)
  }

  const submitted = await apiClient.post('/api/work-record/imports', {
    uploadId: upload.uploadId,
    templateId: options.templateId,
    templateVersionId: options.templateVersionId,
    defaultStatus: options.defaultStatus,
    defaultOwnerId: null,
    defaultRecordTime: null,
    stopOnError: options.stopOnError,
  })
  return apiResponseSchema(z.object({ jobId: z.string() })).parse(
    submitted.data
  ).data.jobId
}

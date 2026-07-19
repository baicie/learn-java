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

export type ImportTemplateDownload = {
  blob: Blob
  fileName: string
}

export async function downloadWorkRecordImportTemplate(
  templateId: string,
  templateVersionId: string
): Promise<ImportTemplateDownload> {
  const response = await apiClient.get('/api/work-record/imports/template', {
    params: { templateId, templateVersionId },
    responseType: 'blob',
  })

  return {
    blob:
      response.data instanceof Blob
        ? response.data
        : new Blob([response.data], { type: XLSX_CONTENT_TYPE }),
    fileName: parseFileName(response.headers['content-disposition']),
  }
}

export function downloadImportTemplate(download: ImportTemplateDownload) {
  const url = URL.createObjectURL(download.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = download.fileName
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
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

function parseFileName(contentDisposition?: string) {
  if (!contentDisposition) return 'work-record-import-template.xlsx'

  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].replace(/^"|"$/g, ''))
    } catch {
      return 'work-record-import-template.xlsx'
    }
  }

  const normal = contentDisposition.match(/filename="?([^";]+)"?/i)
  return normal?.[1] ?? 'work-record-import-template.xlsx'
}

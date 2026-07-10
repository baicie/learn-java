import { apiClient } from '@/lib/api-client'
import type { ListQueryState } from './types'

export type ExportDownload = {
  blob: Blob
  fileName: string
  rowCount: number | null
}

export async function exportWorkRecords(
  query: ListQueryState,
  columns: string[]
): Promise<ExportDownload> {
  const response = await apiClient.post(
    '/api/work-record/records/export',
    {
      templateId: blank(query.templateId),
      templateVersionId: undefined,
      statuses: query.statuses,
      keyword: blank(query.keyword),
      recordTimeFrom: toOffset(query.recordTimeFrom),
      recordTimeTo: toOffset(query.recordTimeTo),
      creatorId: blank(query.creatorId),
      ownerId: blank(query.ownerId),
      quickView: query.quickView,
      workdayCount: query.workdayCount,
      dynamicFilters: query.dynamicFilters,
      sortBy: query.sortBy,
      sortDir: query.sortDir,
      columns,
    },
    {
      responseType: 'blob',
    }
  )

  return {
    blob:
      response.data instanceof Blob
        ? response.data
        : new Blob([response.data], {
            type: 'text/csv;charset=UTF-8',
          }),
    fileName: parseExportFileName(response.headers['content-disposition']),
    rowCount: parseRowCount(response.headers['x-export-row-count']),
  }
}

export function downloadExport(download: ExportDownload) {
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

export function parseExportFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return 'work-records.csv'
  }

  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)

  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].replace(/^"|"$/g, ''))
    } catch {
      return 'work-records.csv'
    }
  }

  const normal = contentDisposition.match(/filename="?([^";]+)"?/i)

  return normal?.[1] ?? 'work-records.csv'
}

function parseRowCount(value: string | undefined) {
  if (!value) return null

  const result = Number(value)
  return Number.isFinite(result) ? result : null
}

function blank(value?: string) {
  return value?.trim() || undefined
}

function toOffset(value?: string) {
  if (!value) return undefined

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

import axios from 'axios'
import { apiClient } from '@/lib/api-client'
import type { ListQueryState } from '@/components/work-records/list/types'

export type ExportDownload = {
  blob: Blob
  fileName: string
  rowCount: number | null
}

export async function exportWorkRecords(
  query: ListQueryState,
  columns: string[],
  recordIds?: string[]
): Promise<ExportDownload> {
  try {
    const response = await apiClient.post(
      '/api/work-record/records/export',
      {
        templateId: blank(query.templateId),
        templateVersionId: blank(query.templateVersionId),
        statuses: query.statuses,
        keyword: blank(query.keyword),
        recordTimeFrom: toOffsetStart(query.recordTimeFrom),
        recordTimeTo: toOffsetEnd(query.recordTimeTo),
        creatorId: blank(query.creatorId),
        ownerId: blank(query.ownerId),
        quickView: query.quickView,
        workdayCount: query.workdayCount,
        sortBy: query.sortBy,
        sortDir: query.sortDir,
        columns,
        ...(recordIds && recordIds.length > 0 ? { recordIds } : {}),
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
  } catch (error) {
    throw await normalizeExportError(error)
  }
}

export async function normalizeExportError(error: unknown): Promise<Error> {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error ? error : new Error('导出失败')
  }

  const responseData = error.response?.data

  if (responseData instanceof Blob) {
    try {
      const text = await responseData.text()
      const payload = JSON.parse(text) as {
        message?: string
        errorCode?: string
      }

      if (payload.message) {
        return new Error(payload.message)
      }
    } catch {
      // 继续使用 Axios 默认错误。
    }
  }

  if (
    responseData &&
    typeof responseData === 'object' &&
    'message' in responseData &&
    typeof responseData.message === 'string'
  ) {
    return new Error(responseData.message)
  }

  return new Error(error.message || '导出失败')
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

  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function parseExportFileName(contentDisposition?: string) {
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

// 筛选只精确到日：起始日按本地 00:00（含），结束日按次日 00:00（后端为 < 右开区间，含结束日当天）。
function toOffsetStart(value?: string) {
  return toOffset(value, 0)
}

function toOffsetEnd(value?: string) {
  return toOffset(value, 1)
}

function toOffset(value: string | undefined, dayOffset: number) {
  if (!value) return undefined

  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim())
  if (dateOnly) {
    const [, year, month, day] = dateOnly
    const local = new Date(
      Number(year),
      Number(month) - 1,
      Number(day) + dayOffset
    )
    return local.toISOString()
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

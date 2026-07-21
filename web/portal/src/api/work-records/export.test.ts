import axios from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import type { ListQueryState } from '@/components/work-records/list/types'
import { exportWorkRecords, normalizeExportError } from './export'

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}))

const query: ListQueryState = {
  page: 1,
  pageSize: 20,
  quickView: 'all',
  workdayCount: 5,
  templateId: '',
  templateVersionId: '',
  statuses: [],
  ownerId: '',
  creatorId: '',
  keyword: '',
  recordTimeFrom: '',
  recordTimeTo: '',
  sortBy: 'recordTime',
  sortDir: 'desc',
  visibleColumns: [],
}

describe('exportWorkRecords', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.post).mockResolvedValue({
      data: new Blob(['csv'], { type: 'text/csv;charset=UTF-8' }),
      headers: {},
    })
  })

  it('sends recordIds in the request body when provided', async () => {
    await exportWorkRecords(query, ['title'], ['record-1', 'record-2'])

    const body = vi.mocked(apiClient.post).mock.calls[0]?.[1] as {
      recordIds?: string[]
    }
    expect(body.recordIds).toEqual(['record-1', 'record-2'])
  })

  it('omits recordIds when not provided or empty', async () => {
    await exportWorkRecords(query, ['title'])
    await exportWorkRecords(query, ['title'], [])

    const calls = vi.mocked(apiClient.post).mock.calls
    for (const call of calls) {
      const body = call[1] as Record<string, unknown>
      expect('recordIds' in body).toBe(false)
    }
  })
})

describe('normalizeExportError', () => {
  it('extracts message from blob api error', async () => {
    const error = new axios.AxiosError(
      'Request failed',
      'ERR_BAD_REQUEST',
      undefined,
      undefined,
      {
        data: new Blob([
          JSON.stringify({
            success: false,
            errorCode: 'EXPORT_LIMIT',
            message: '导出结果超过 5000 行',
          }),
        ]),
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {
          headers: new axios.AxiosHeaders(),
        },
      }
    )

    const normalized = await normalizeExportError(error)

    expect(normalized.message).toBe('导出结果超过 5000 行')
  })

  it('returns default message for non-axios errors', async () => {
    const error = new Error('Something went wrong')

    const normalized = await normalizeExportError(error)

    expect(normalized.message).toBe('Something went wrong')
  })

  it('returns default message for unknown errors', async () => {
    const normalized = await normalizeExportError('not an error')

    expect(normalized.message).toBe('导出失败')
  })
})

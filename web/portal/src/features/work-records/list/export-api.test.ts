import axios from 'axios'
import { describe, expect, it } from 'vitest'
import { normalizeExportError } from './export-api'

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

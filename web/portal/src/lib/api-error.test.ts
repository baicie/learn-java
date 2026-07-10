import axios, { type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { apiErrorCode, apiErrorMessage, toAppError } from './api-error'

function makeAxiosError<T>(status: number, data: T) {
  const error = new axios.AxiosError('request failed')
  const response = {
    data,
    status,
    statusText: 'Status',
    headers: {},
    config: { headers: new axios.AxiosHeaders() },
  } as unknown as AxiosResponse<T>

  error.response = response

  return error
}

describe('toAppError', () => {
  it('reads backend api error', () => {
    const error = makeAxiosError(409, {
      errorCode: 'WORK_CALENDAR_INCOMPLETE',
      message: '工作日历数据不完整',
      errors: {
        recordTime: '记录时间无效',
      },
    })

    expect(toAppError(error)).toEqual(
      expect.objectContaining({
        code: 'WORK_CALENDAR_INCOMPLETE',
        message: '工作日历数据不完整',
        fieldErrors: { recordTime: '记录时间无效' },
      })
    )
  })

  it('uses fallback for unknown values', () => {
    expect(toAppError(null, 'fallback').message).toBe('fallback')
  })

  it('falls back to error.message for Error instances', () => {
    expect(toAppError(new Error('boom')).message).toBe('boom')
  })
})

describe('apiErrorMessage', () => {
  it('returns axios body message', () => {
    const error = makeAxiosError(500, { message: '操作失败' })

    expect(apiErrorMessage(error, 'fallback')).toBe('操作失败')
  })

  it('returns fallback for unknown errors', () => {
    expect(apiErrorMessage(null, 'custom fallback')).toBe('custom fallback')
  })
})

describe('apiErrorCode', () => {
  it('returns backend error code', () => {
    const error = makeAxiosError(400, { errorCode: 'BAD_REQUEST' })

    expect(apiErrorCode(error)).toBe('BAD_REQUEST')
  })

  it('returns null when no body', () => {
    expect(apiErrorCode(null)).toBeNull()
  })
})

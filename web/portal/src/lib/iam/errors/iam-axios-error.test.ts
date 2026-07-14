import { AxiosError } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { toIamRequestError } from './iam-api-error'

describe('AxiosError branch', () => {
  it('unwraps AxiosError with response.data envelope', () => {
    const err = new AxiosError('boom')
    err.response = {
      status: 422,
      data: {
        ok: false,
        code: 'platform.permission.directory.incomplete',
        httpStatus: 422,
        message: 'missing permissions',
        details: { codes: ['ghost'] },
      },
      statusText: 'Unprocessable Entity',
      headers: {},
      config: {} as never,
    }
    const decoded = toIamRequestError(err)
    expect(decoded.code).toBe('platform.permission.directory.incomplete')
    expect(decoded.details.codes).toEqual(['ghost'])
  })

  it('falls back gracefully when status is absent', () => {
    const err = new AxiosError('network')
    err.response = undefined
    err.request = {}
    const decoded = toIamRequestError(err)
    expect(decoded.code).toBe('platform.request.transport')
    expect(decoded.httpStatus).toBe(0)
  })
})

describe('non-AxiosError branch', () => {
  it('wraps plain Error', () => {
    const decoded = toIamRequestError(new Error('plain'))
    expect(decoded.code).toBe('platform.request.unknown')
  })

  it('wraps non-Error throws', () => {
    const decoded = toIamRequestError('just a string')
    expect(decoded.code).toBe('platform.request.unknown')
    expect(decoded.message).toBe('just a string')
  })
})

describe('vi sanity', () => {
  it('runs', () => {
    expect(vi).toBeDefined()
  })
})

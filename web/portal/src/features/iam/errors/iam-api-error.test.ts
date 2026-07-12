import { describe, expect, it } from 'vitest'
import { toIamRequestError } from './iam-api-error'

describe('toIamRequestError', () => {
  it('decodes the IAM envelope when present', () => {
    const err = toIamRequestError({
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          ok: false,
          code: 'platform.user.username_conflict',
          httpStatus: 409,
          message: 'username taken',
          details: { username: 'alice' },
        },
      },
      message: 'Request failed',
    })
    expect(err.code).toBe('platform.user.username_conflict')
    expect(err.httpStatus).toBe(409)
    expect(err.details.username).toBe('alice')
  })

  it('falls back to transport error when envelope is missing', () => {
    const err = toIamRequestError({
      isAxiosError: true,
      response: { status: 500, data: 'internal' },
      message: 'oops',
    })
    expect(err.code).toBe('platform.request.transport')
    expect(err.httpStatus).toBe(500)
  })

  it('handles unknown errors', () => {
    const err = toIamRequestError(new Error('boom'))
    expect(err.code).toBe('platform.request.unknown')
    expect(err.message).toBe('boom')
  })
})
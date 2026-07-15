import { z } from 'zod'
import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { shouldRetryQuery } from './query-retry'

describe('shouldRetryQuery', () => {
  it('does not retry deterministic response parsing failures', () => {
    const parseError = z.object({ id: z.string() }).safeParse({}).error

    expect(shouldRetryQuery(0, parseError)).toBe(false)
  })

  it('does not retry client request errors', () => {
    const error = new AxiosError(
      'Bad request',
      'ERR_BAD_REQUEST',
      undefined,
      undefined,
      {
        status: 400,
        statusText: 'Bad Request',
        data: {},
        headers: {},
        config: { headers: new AxiosHeaders() },
      }
    )

    expect(shouldRetryQuery(0, error)).toBe(false)
  })

  it('retries a transient failure only once', () => {
    expect(shouldRetryQuery(0, new Error('network'))).toBe(true)
    expect(shouldRetryQuery(1, new Error('network'))).toBe(false)
  })
})

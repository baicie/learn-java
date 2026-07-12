import { AxiosError } from 'axios'
import type { z } from 'zod'
import { iamApiErrorEnvelopeSchema } from '../schemas/platform-user'

export type IamRequestError = {
  code: string
  httpStatus: number
  message: string
  details: Record<string, unknown>
  cause: unknown
}

const envelopeSchema = iamApiErrorEnvelopeSchema

/**
 * Detect an "axios-like" error without relying on `axios.isAxiosError` which
 * only works on actual AxiosError instances. Tests use plain objects with an
 * `isAxiosError` flag; production uses real AxiosError.
 */
function looksLikeAxiosError(value: unknown): value is {
  isAxiosError: true
  response?: { status?: number; data?: unknown }
  message?: string
} {
  if (!value || typeof value !== 'object') return false
  if (value instanceof AxiosError) return true
  return (value as { isAxiosError?: unknown }).isAxiosError === true
}

export function toIamRequestError(error: unknown): IamRequestError {
  if (looksLikeAxiosError(error)) {
    const responseData = error.response?.data
    const parsed = tryParseEnvelope(responseData)
    if (parsed) {
      return {
        code: parsed.code,
        httpStatus: parsed.httpStatus,
        message: parsed.message,
        details: parsed.details,
        cause: error,
      }
    }
    return {
      code: 'platform.request.transport',
      httpStatus: error.response?.status ?? 0,
      message: error.message ?? 'request failed',
      details: {},
      cause: error,
    }
  }
  return {
    code: 'platform.request.unknown',
    httpStatus: 0,
    message: error instanceof Error ? error.message : String(error),
    details: {},
    cause: error,
  }
}

function tryParseEnvelope(payload: unknown): z.infer<typeof envelopeSchema> | null {
  try {
    return envelopeSchema.parse(payload)
  } catch {
    return null
  }
}
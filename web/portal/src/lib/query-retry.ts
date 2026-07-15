import { ZodError } from 'zod'
import { isAxiosError } from 'axios'

/** Retry only failures that may recover without changing the request. */
export function shouldRetryQuery(failureCount: number, error: unknown) {
  if (error instanceof ZodError) return false

  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status && status >= 400 && status < 500) return false
  }

  return failureCount < 1
}

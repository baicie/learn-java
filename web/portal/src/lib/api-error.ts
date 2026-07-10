import axios from 'axios'

type ApiErrorBody = {
  errorCode?: string | null
  message?: string | null
  errors?: Record<string, string> | null
}

export type AppError = {
  code: string | null
  message: string
  fieldErrors: Record<string, string>
  cause: unknown
}

const DEFAULT_FALLBACK = '操作失败，请稍后重试'

export function toAppError(
  error: unknown,
  fallback: string = DEFAULT_FALLBACK
): AppError {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data

    return {
      code: body?.errorCode ?? null,
      message: body?.message?.trim() || error.message || fallback,
      fieldErrors: body?.errors ?? {},
      cause: error,
    }
  }

  if (error instanceof Error) {
    return {
      code: null,
      message: error.message || fallback,
      fieldErrors: {},
      cause: error,
    }
  }

  return {
    code: null,
    message: fallback,
    fieldErrors: {},
    cause: error,
  }
}

export function apiErrorMessage(error: unknown, fallback?: string): string {
  return toAppError(error, fallback).message
}

export function apiErrorCode(error: unknown): string | null {
  return toAppError(error).code
}

export function apiFieldErrors(error: unknown): Record<string, string> {
  return toAppError(error).fieldErrors
}

import axios from 'axios'

type ApiErrorBody = {
  errorCode?: string | null
  message?: string | null
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.message ?? error.message ?? fallback
  }

  if (error instanceof Error) {
    return error.message
  }

  return fallback
}

export function apiErrorCode(error: unknown) {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }

  return null
}

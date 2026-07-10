// 同一文件同时导出组件（AppToaster）和工具（notify / withToast），
// 遵循 Phase 16 设计稿中的反馈基础设施约定。fast-refresh
// 仅影响开发体验，不影响功能正确性，因此显式禁用该 lint。
/* eslint-disable react-refresh/only-export-components */
import { toast, Toaster } from 'sonner'
import { apiErrorMessage } from '@/lib/api-error'

export function AppToaster() {
  return <Toaster richColors closeButton position='top-right' duration={4000} />
}

export const notify = {
  success(message: string) {
    toast.success(message)
  },

  info(message: string) {
    toast.info(message)
  },

  warning(message: string) {
    toast.warning(message)
  },

  error(error: unknown, fallback = '操作失败，请稍后重试') {
    toast.error(apiErrorMessage(error, fallback))
  },
}

export async function withToast<T>(
  promise: Promise<T>,
  options: {
    loading: string
    success: string | ((result: T) => string)
    error?: string
  }
): Promise<T> {
  const toastId = toast.loading(options.loading)

  try {
    const result = await promise
    const successMessage =
      typeof options.success === 'function'
        ? options.success(result)
        : options.success

    toast.success(successMessage, { id: toastId })

    return result
  } catch (error) {
    toast.error(
      apiErrorMessage(error, options.error ?? '操作失败，请稍后重试'),
      { id: toastId }
    )
    throw error
  }
}

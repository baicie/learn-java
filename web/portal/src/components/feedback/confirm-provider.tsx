// 同一文件同时导出组件（ConfirmProvider）和 hook（useConfirm），
// 遵循 Phase 16 设计稿中反馈基础设施的导出约定。
/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useRef,
  useState,
} from 'react'
import * as AlertDialog from '@radix-ui/react-alert-dialog'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

export type ConfirmVariant = 'default' | 'warning' | 'destructive'

export type ConfirmOptions = {
  title: string
  description: ReactNode
  confirmText?: string
  cancelText?: string
  variant?: ConfirmVariant
  details?: ReactNode
}

type PendingConfirm = {
  id: number
  options: ConfirmOptions
  resolve: (confirmed: boolean) => void
}

type ConfirmContextValue = {
  confirm: (options: ConfirmOptions) => Promise<boolean>
}

const ConfirmContext = createContext<ConfirmContextValue | null>(null)

let requestSequence = 0

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const queueRef = useRef<PendingConfirm[]>([])
  const [current, setCurrent] = useState<PendingConfirm | null>(null)

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        const request: PendingConfirm = {
          id: ++requestSequence,
          options,
          resolve,
        }

        setCurrent((existing) => {
          if (existing) {
            queueRef.current.push(request)
            return existing
          }
          return request
        })
      }),
    []
  )

  const finish = useCallback(
    (confirmed: boolean) => {
      if (!current) return
      current.resolve(confirmed)
      setCurrent(queueRef.current.shift() ?? null)
    },
    [current]
  )

  const variant = current?.options.variant ?? 'default'

  return (
    <ConfirmContext.Provider value={{ confirm }}>
      {children}

      <AlertDialog.Root
        open={Boolean(current)}
        onOpenChange={(open) => {
          if (!open && current) {
            finish(false)
          }
        }}
      >
        <AlertDialog.Portal>
          <AlertDialog.Overlay className='fixed inset-0 z-50 bg-black/50 data-[state=closed]:animate-out data-[state=open]:animate-in' />

          <AlertDialog.Content className='fixed top-1/2 left-1/2 z-50 grid w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 rounded-lg border bg-background p-5 shadow-lg sm:p-6'>
            <AlertDialog.Title className='text-lg font-semibold'>
              {current?.options.title}
            </AlertDialog.Title>

            <AlertDialog.Description asChild>
              <div className='text-sm leading-6 text-muted-foreground'>
                {current?.options.description}
              </div>
            </AlertDialog.Description>

            {current?.options.details ? (
              <div className='rounded-md border bg-muted/40 p-3 text-sm'>
                {current.options.details}
              </div>
            ) : null}

            <div className='flex flex-col-reverse gap-2 sm:flex-row sm:justify-end'>
              <AlertDialog.Cancel asChild>
                <Button
                  type='button'
                  variant='outline'
                  onClick={() => finish(false)}
                >
                  {current?.options.cancelText ?? '取消'}
                </Button>
              </AlertDialog.Cancel>

              <AlertDialog.Action asChild>
                <Button
                  type='button'
                  variant={
                    variant === 'destructive' ? 'destructive' : 'default'
                  }
                  className={cn(
                    variant === 'warning' &&
                      'bg-amber-600 text-white hover:bg-amber-700'
                  )}
                  onClick={() => finish(true)}
                >
                  {current?.options.confirmText ?? '确认'}
                </Button>
              </AlertDialog.Action>
            </div>
          </AlertDialog.Content>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </ConfirmContext.Provider>
  )
}

export function useConfirm() {
  const context = useContext(ConfirmContext)
  if (!context) {
    throw new Error('useConfirm must be used inside ConfirmProvider')
  }
  return context.confirm
}

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
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
  const activeRef = useRef<PendingConfirm | null>(null)
  const queueRef = useRef<PendingConfirm[]>([])
  const [active, setActive] = useState<PendingConfirm | null>(null)

  const activate = useCallback((request: PendingConfirm | null) => {
    activeRef.current = request
    setActive(request)
  }, [])

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        const request: PendingConfirm = {
          id: ++requestSequence,
          options,
          resolve,
        }

        if (activeRef.current) {
          queueRef.current.push(request)
          return
        }

        activate(request)
      }),
    [activate]
  )

  const settle = useCallback(
    (confirmed: boolean) => {
      const current = activeRef.current
      if (!current) return

      activeRef.current = null
      current.resolve(confirmed)

      const next = queueRef.current.shift() ?? null
      activate(next)
    },
    [activate]
  )

  useEffect(
    () => () => {
      activeRef.current?.resolve(false)
      for (const request of queueRef.current) {
        request.resolve(false)
      }
      activeRef.current = null
      queueRef.current = []
    },
    []
  )

  const variant = active?.options.variant ?? 'default'

  return (
    <ConfirmContext.Provider value={{ confirm }}>
      {children}

      <AlertDialog.Root
        open={Boolean(active)}
        onOpenChange={(open) => {
          if (!open && activeRef.current) {
            settle(false)
          }
        }}
      >
        <AlertDialog.Portal>
          <AlertDialog.Overlay className='fixed inset-0 z-50 bg-black/50' />

          <AlertDialog.Content className='fixed top-1/2 left-1/2 z-50 grid w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 rounded-lg border bg-background p-5 shadow-lg sm:p-6'>
            <AlertDialog.Title className='text-lg font-semibold'>
              {active?.options.title}
            </AlertDialog.Title>

            <AlertDialog.Description asChild>
              <div className='text-sm leading-6 text-muted-foreground'>
                {active?.options.description}
              </div>
            </AlertDialog.Description>

            {active?.options.details ? (
              <div className='rounded-md border bg-muted/40 p-3 text-sm'>
                {active.options.details}
              </div>
            ) : null}

            <div className='flex flex-col-reverse gap-2 sm:flex-row sm:justify-end'>
              {/*
                Dialog 由 active state 完全控制。
                这里不使用 AlertDialog.Cancel/Action，因为它们会在内部触发
                onOpenChange(false)，从而和 settle() 产生重复结算。
              */}
              <Button
                type='button'
                variant='outline'
                onClick={() => settle(false)}
              >
                {active?.options.cancelText ?? '取消'}
              </Button>

              <Button
                type='button'
                variant={variant === 'destructive' ? 'destructive' : 'default'}
                className={cn(
                  variant === 'warning' &&
                    'bg-amber-600 text-white hover:bg-amber-700'
                )}
                onClick={() => settle(true)}
              >
                {active?.options.confirmText ?? '确认'}
              </Button>
            </div>
          </AlertDialog.Content>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </ConfirmContext.Provider>
  )
}

// useConfirm must share a file with ConfirmProvider (Context + hook pattern)
// so it can access the internal queueing state through Context. Splitting
// them across files would force consumers to import from two paths and lose
// the React-Refresh-friendly colocated boundary the rest of the feedback
// infrastructure relies on.
// eslint-disable-next-line react-refresh/only-export-components
export function useConfirm() {
  const context = useContext(ConfirmContext)

  if (!context) {
    throw new Error('useConfirm must be used inside ConfirmProvider')
  }

  return context.confirm
}

import type { ReactNode } from 'react'
import { AlertCircle, Inbox, RefreshCw } from 'lucide-react'
import { apiErrorMessage } from '@/lib/api-error'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

export function PageLoadingState({ rows = 6 }: { rows?: number }) {
  return (
    <main className='grid gap-4 p-4 md:p-6'>
      <div className='grid gap-2'>
        <Skeleton className='h-8 w-48' />
        <Skeleton className='h-4 w-72 max-w-full' />
      </div>

      <Skeleton className='h-12 w-full' />

      <div className='grid gap-2 rounded-lg border p-4'>
        {Array.from({ length: rows }).map((_, index) => (
          <Skeleton key={index} className='h-10 w-full' />
        ))}
      </div>
    </main>
  )
}

export function TableLoadingState({
  rows = 8,
  columns = 5,
}: {
  rows?: number
  columns?: number
}) {
  return (
    <div
      className='grid gap-2 rounded-lg border p-4'
      aria-label='数据加载中'
      aria-busy='true'
    >
      {Array.from({ length: rows }).map((_, row) => (
        <div
          key={row}
          className='grid gap-2'
          style={{
            gridTemplateColumns: `repeat(${columns}, minmax(90px, 1fr))`,
          }}
        >
          {Array.from({ length: columns }).map((_, column) => (
            <Skeleton key={column} className='h-9' />
          ))}
        </div>
      ))}
    </div>
  )
}

export function EmptyState({
  title = '暂无数据',
  description,
  action,
  icon,
  compact = false,
}: {
  title?: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
  compact?: boolean
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-dashed text-center',
        compact ? 'min-h-40 p-6' : 'min-h-72 p-8'
      )}
    >
      <div className='mb-3 rounded-full bg-muted p-3 text-muted-foreground'>
        {icon ?? <Inbox className='size-6' />}
      </div>

      <h3 className='font-medium'>{title}</h3>

      {description ? (
        <p className='mt-1 max-w-md text-sm text-muted-foreground'>
          {description}
        </p>
      ) : null}

      {action ? <div className='mt-4'>{action}</div> : null}
    </div>
  )
}

export function ErrorState({
  error,
  title = '加载失败',
  fallbackMessage = '请求失败，请稍后重试',
  onRetry,
  compact = false,
}: {
  error: unknown
  title?: string
  fallbackMessage?: string
  onRetry?: () => void
  compact?: boolean
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-destructive/30 bg-destructive/5 text-center',
        compact ? 'min-h-40 p-6' : 'min-h-72 p-8'
      )}
      role='alert'
    >
      <div className='mb-3 rounded-full bg-destructive/10 p-3 text-destructive'>
        <AlertCircle className='size-6' />
      </div>

      <h3 className='font-medium text-destructive'>{title}</h3>

      <p className='mt-1 max-w-lg text-sm text-muted-foreground'>
        {apiErrorMessage(error, fallbackMessage)}
      </p>

      {onRetry ? (
        <Button
          type='button'
          variant='outline'
          className='mt-4'
          onClick={onRetry}
        >
          <RefreshCw className='mr-2 size-4' />
          重新加载
        </Button>
      ) : null}
    </div>
  )
}

export function QueryStateBoundary({
  loading,
  error,
  empty,
  loadingFallback,
  errorFallback,
  emptyFallback,
  children,
}: {
  loading: boolean
  error: unknown
  empty: boolean
  loadingFallback: ReactNode
  errorFallback: ReactNode
  emptyFallback: ReactNode
  children: ReactNode
}) {
  if (loading) return loadingFallback
  if (error) return errorFallback
  if (empty) return emptyFallback
  return children
}

import type { ReactNode } from 'react'
import { RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function TableToolbar({
  search,
  filters,
  actions,
  activeFilterCount = 0,
  onReset,
}: {
  search?: ReactNode
  filters?: ReactNode
  actions?: ReactNode
  activeFilterCount?: number
  onReset?: () => void
}) {
  return (
    <section className='grid gap-3 rounded-lg border bg-card p-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-start'>
      <div className='grid min-w-0 gap-3'>
        {search}

        <div className='flex min-w-0 flex-wrap items-center gap-2'>
          {filters}

          {activeFilterCount > 0 && onReset ? (
            <Button type='button' size='sm' variant='ghost' onClick={onReset}>
              <RotateCcw className='mr-1 size-4' />
              重置筛选
            </Button>
          ) : null}
        </div>
      </div>

      {actions ? (
        <div className='flex flex-wrap items-center gap-2 lg:justify-end'>
          {actions}
        </div>
      ) : null}
    </section>
  )
}

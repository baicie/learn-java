import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function ResponsiveTable({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'w-full max-w-full overflow-x-auto rounded-lg border',
        className
      )}
    >
      <div className='min-w-[760px]'>{children}</div>
    </div>
  )
}

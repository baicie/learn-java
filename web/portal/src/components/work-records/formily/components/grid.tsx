import type { PropsWithChildren } from 'react'

export function FormGrid({
  columns = 2,
  children,
}: PropsWithChildren<{ columns?: 1 | 2 | 3 | 4 }>) {
  const gridClass = {
    1: 'grid-cols-1',
    2: 'grid-cols-1 md:grid-cols-2',
    3: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    4: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-4',
  }[columns]

  return <div className={`grid gap-4 ${gridClass}`}>{children}</div>
}

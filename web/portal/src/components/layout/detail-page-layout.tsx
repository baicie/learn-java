import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function DetailPageLayout({
  title,
  description,
  meta,
  actions,
  children,
  aside,
}: {
  title: ReactNode
  description?: ReactNode
  meta?: ReactNode
  actions?: ReactNode
  children: ReactNode
  aside?: ReactNode
}) {
  return (
    <main className='grid gap-4 p-4 md:gap-6 md:p-6'>
      <header className='flex flex-col gap-4 rounded-lg border bg-card p-4 sm:p-5 lg:flex-row lg:items-start lg:justify-between'>
        <div className='min-w-0'>
          <h1 className='text-xl font-semibold break-words md:text-2xl'>
            {title}
          </h1>

          {description ? (
            <div className='mt-1 text-sm text-muted-foreground'>
              {description}
            </div>
          ) : null}

          {meta ? <div className='mt-3'>{meta}</div> : null}
        </div>

        {actions ? (
          <div className='flex shrink-0 flex-wrap gap-2'>{actions}</div>
        ) : null}
      </header>

      <div
        className={cn(
          'grid gap-4',
          aside && 'xl:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]'
        )}
      >
        <div className='grid min-w-0 gap-4'>{children}</div>
        {aside ? (
          <aside className='grid content-start gap-4'>{aside}</aside>
        ) : null}
      </div>
    </main>
  )
}

export function DetailSection({
  title,
  description,
  actions,
  children,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <section className='rounded-lg border bg-card'>
      <header className='flex flex-col gap-2 border-b p-4 sm:flex-row sm:items-start sm:justify-between'>
        <div>
          <h2 className='font-medium'>{title}</h2>
          {description ? (
            <p className='mt-1 text-sm text-muted-foreground'>{description}</p>
          ) : null}
        </div>

        {actions}
      </header>

      <div className='p-4'>{children}</div>
    </section>
  )
}

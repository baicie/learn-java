import type { ReactNode } from 'react'

type Props = {
  title?: ReactNode
  description?: ReactNode
  children?: ReactNode
  actions?: ReactNode
}

export function TableToolbar({ title, description, children, actions }: Props) {
  return (
    <section className='grid gap-3 rounded-lg border bg-card p-3'>
      {title || description || actions ? (
        <header className='flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between'>
          <div className='min-w-0'>
            {title ? (
              <h2 className='text-base font-semibold'>{title}</h2>
            ) : null}
            {description ? (
              <p className='text-sm text-muted-foreground'>{description}</p>
            ) : null}
          </div>
          {actions ? (
            <div className='flex flex-wrap items-center gap-2 lg:justify-end'>
              {actions}
            </div>
          ) : null}
        </header>
      ) : null}

      {children}
    </section>
  )
}

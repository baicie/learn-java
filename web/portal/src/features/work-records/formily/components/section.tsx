import type { PropsWithChildren } from 'react'

export function FormSection({
  title,
  description,
  children,
}: PropsWithChildren<{ title?: string; description?: string }>) {
  return (
    <section className='rounded-lg border bg-card'>
      {(title || description) && (
        <header className='border-b p-4'>
          {title ? <h3 className='font-medium'>{title}</h3> : null}
          {description ? (
            <p className='mt-1 text-sm text-muted-foreground'>
              {description}
            </p>
          ) : null}
        </header>
      )}
      <div className='grid gap-4 p-4'>{children}</div>
    </section>
  )
}
import { type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

type ErrorPageAction = {
  labelKey: string
  icon: LucideIcon
  onClick: () => void
  variant?: React.ComponentProps<typeof Button>['variant']
}

type ErrorPageProps = React.HTMLAttributes<HTMLElement> & {
  status: string
  titleKey: string
  descriptionKey: string
  icon: LucideIcon
  actions?: ErrorPageAction[]
  minimal?: boolean
}

export function ErrorPage({
  status,
  titleKey,
  descriptionKey,
  icon: StatusIcon,
  actions = [],
  minimal = false,
  className,
  ...props
}: ErrorPageProps) {
  const { t } = useTranslation()

  return (
    <main
      className={cn(
        'relative isolate flex min-h-svh w-full items-center justify-center overflow-hidden bg-background px-6 py-12',
        minimal && 'min-h-0 py-10',
        className
      )}
      {...props}
    >
      <div
        aria-hidden='true'
        className='absolute inset-x-0 top-0 h-px bg-border'
      />
      <div className='flex w-full max-w-xl flex-col items-center text-center'>
        {!minimal && (
          <div className='mb-6 flex size-14 items-center justify-center rounded-md border bg-muted/50 text-muted-foreground shadow-xs'>
            <StatusIcon className='size-7' strokeWidth={1.75} />
          </div>
        )}

        {!minimal && (
          <p className='mb-3 font-mono text-sm font-medium text-muted-foreground'>
            {t('errors.status', { status })}
          </p>
        )}
        <h1 className='text-3xl font-semibold sm:text-4xl'>{t(titleKey)}</h1>
        <p className='mt-4 max-w-md text-sm leading-6 text-muted-foreground sm:text-base'>
          {t(descriptionKey)}
        </p>

        {!minimal && actions.length > 0 && (
          <div className='mt-8 flex w-full flex-col-reverse justify-center gap-3 sm:w-auto sm:flex-row'>
            {actions.map(({ labelKey, icon: ActionIcon, ...action }) => (
              <Button key={labelKey} className='w-full sm:w-auto' {...action}>
                <ActionIcon data-icon='inline-start' />
                {t(labelKey)}
              </Button>
            ))}
          </div>
        )}
      </div>
    </main>
  )
}

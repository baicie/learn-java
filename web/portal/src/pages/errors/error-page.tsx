import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

type ErrorPageAction = {
  labelKey: string
  onClick: () => void
  variant?: React.ComponentProps<typeof Button>['variant']
}

type ErrorPageProps = React.HTMLAttributes<HTMLElement> & {
  status: string
  titleKey: string
  descriptionKey: string
  actions?: ErrorPageAction[]
  minimal?: boolean
}

export function ErrorPage({
  status,
  titleKey,
  descriptionKey,
  actions = [],
  minimal = false,
  className,
  ...props
}: ErrorPageProps) {
  const { t } = useTranslation()

  return (
    <main className={cn('h-svh w-full', className)} {...props}>
      <div className='m-auto flex h-full w-full flex-col items-center justify-center gap-2 px-6'>
        {!minimal && (
          <h1 className='text-[7rem] leading-tight font-bold'>{status}</h1>
        )}
        <h2 className='text-center font-medium'>{t(titleKey)}</h2>
        <p className='max-w-md text-center text-muted-foreground'>
          {t(descriptionKey)}
        </p>

        {!minimal && actions.length > 0 && (
          <div className='mt-6 flex gap-4'>
            {actions.map(({ labelKey, ...action }) => (
              <Button key={labelKey} {...action}>
                {t(labelKey)}
              </Button>
            ))}
          </div>
        )}
      </div>
    </main>
  )
}

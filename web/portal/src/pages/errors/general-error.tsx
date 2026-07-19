import { useNavigate, useRouter } from '@tanstack/react-router'
import { ErrorPage } from './error-page'

type GeneralErrorProps = React.HTMLAttributes<HTMLDivElement> & {
  minimal?: boolean
}

export function GeneralError({
  className,
  minimal = false,
}: GeneralErrorProps) {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      className={className}
      status='500'
      titleKey='errors.500.title'
      descriptionKey='errors.500.description'
      minimal={minimal}
      actions={[
        {
          labelKey: 'errors.actions.back',
          variant: 'outline',
          onClick: () => history.go(-1),
        },
        {
          labelKey: 'errors.actions.home',
          onClick: () => navigate({ to: '/' }),
        },
      ]}
    />
  )
}

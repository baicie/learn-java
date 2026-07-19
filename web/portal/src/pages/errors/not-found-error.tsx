import { useNavigate, useRouter } from '@tanstack/react-router'
import { ErrorPage } from './error-page'

export function NotFoundError() {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      status='404'
      titleKey='errors.404.title'
      descriptionKey='errors.404.description'
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

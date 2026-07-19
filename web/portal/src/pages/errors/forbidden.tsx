import { useNavigate, useRouter } from '@tanstack/react-router'
import { ErrorPage } from './error-page'

export function ForbiddenError() {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      status='403'
      titleKey='errors.403.title'
      descriptionKey='errors.403.description'
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

import { useNavigate, useRouter } from '@tanstack/react-router'
import { ArrowLeft, House, ShieldX } from 'lucide-react'
import { ErrorPage } from './error-page'

export function ForbiddenError() {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      status='403'
      titleKey='errors.403.title'
      descriptionKey='errors.403.description'
      icon={ShieldX}
      actions={[
        {
          labelKey: 'errors.actions.back',
          icon: ArrowLeft,
          variant: 'outline',
          onClick: () => history.go(-1),
        },
        {
          labelKey: 'errors.actions.home',
          icon: House,
          onClick: () => navigate({ to: '/' }),
        },
      ]}
    />
  )
}

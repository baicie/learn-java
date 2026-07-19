import { useNavigate, useRouter } from '@tanstack/react-router'
import { ArrowLeft, FileQuestion, House } from 'lucide-react'
import { ErrorPage } from './error-page'

export function NotFoundError() {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      status='404'
      titleKey='errors.404.title'
      descriptionKey='errors.404.description'
      icon={FileQuestion}
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

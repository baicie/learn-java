import { useNavigate, useRouter } from '@tanstack/react-router'
import { ArrowLeft, LockKeyhole, LogIn } from 'lucide-react'
import { ErrorPage } from './error-page'

export function UnauthorisedError() {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <ErrorPage
      status='401'
      titleKey='errors.401.title'
      descriptionKey='errors.401.description'
      icon={LockKeyhole}
      actions={[
        {
          labelKey: 'errors.actions.back',
          icon: ArrowLeft,
          variant: 'outline',
          onClick: () => history.go(-1),
        },
        {
          labelKey: 'errors.actions.signIn',
          icon: LogIn,
          onClick: () =>
            navigate({
              to: '/sign-in',
              search: { redirect: history.location.href },
            }),
        },
      ]}
    />
  )
}

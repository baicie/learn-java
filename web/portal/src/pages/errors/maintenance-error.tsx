import { useNavigate } from '@tanstack/react-router'
import { ErrorPage } from './error-page'

export function MaintenanceError() {
  const navigate = useNavigate()

  return (
    <ErrorPage
      status='503'
      titleKey='errors.503.title'
      descriptionKey='errors.503.description'
      actions={[
        {
          labelKey: 'errors.actions.home',
          variant: 'outline',
          onClick: () => navigate({ to: '/' }),
        },
        {
          labelKey: 'errors.actions.reload',
          onClick: () => window.location.reload(),
        },
      ]}
    />
  )
}

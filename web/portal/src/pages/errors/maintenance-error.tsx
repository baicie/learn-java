import { useNavigate } from '@tanstack/react-router'
import { Construction, House, RefreshCw } from 'lucide-react'
import { ErrorPage } from './error-page'

export function MaintenanceError() {
  const navigate = useNavigate()

  return (
    <ErrorPage
      status='503'
      titleKey='errors.503.title'
      descriptionKey='errors.503.description'
      icon={Construction}
      actions={[
        {
          labelKey: 'errors.actions.home',
          icon: House,
          variant: 'outline',
          onClick: () => navigate({ to: '/' }),
        },
        {
          labelKey: 'errors.actions.reload',
          icon: RefreshCw,
          onClick: () => window.location.reload(),
        },
      ]}
    />
  )
}

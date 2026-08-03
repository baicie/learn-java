import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { IncidentDetailPage } from '@/pages/incidents/detail'

export const Route = createFileRoute('/_authenticated/incidents/$incidentId')({
  beforeLoad: () => requireAnyPermission(['incident:read']),
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router requires a route-local component.
function Component() {
  const { incidentId } = Route.useParams()
  return <IncidentDetailPage incidentId={incidentId} />
}

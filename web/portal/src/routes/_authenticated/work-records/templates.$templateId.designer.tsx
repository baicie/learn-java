import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { WorkRecordDesigner } from '@/pages/work-records/designer'

export const Route = createFileRoute(
  '/_authenticated/work-records/templates/$templateId/designer'
)({
  beforeLoad: () => requireAnyPermission(['work-record:template:write']),
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router binds this component in the route module
function Component() {
  const { templateId } = Route.useParams()
  return <WorkRecordDesigner templateId={templateId} />
}

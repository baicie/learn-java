import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { EditWorkRecord } from '@/pages/work-records/edit'

export const Route = createFileRoute(
  '/_authenticated/work-records/$recordId_/edit'
)({
  beforeLoad: () => requireAnyPermission(['work-record:write']),
  component: EditWorkRecord,
})

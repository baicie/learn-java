import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { NewWorkRecord } from '@/pages/work-records/new'

export const Route = createFileRoute('/_authenticated/work-records/new')({
  beforeLoad: () => requireAnyPermission(['work-record:write']),
  component: NewWorkRecord,
})

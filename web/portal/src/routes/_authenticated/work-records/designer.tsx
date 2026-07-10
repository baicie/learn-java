import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { WorkRecordDesigner } from '@/features/work-records/designer'

export const Route = createFileRoute('/_authenticated/work-records/designer')({
  beforeLoad: () => requireAnyPermission(['work-record:template:read']),
  component: WorkRecordDesigner,
})

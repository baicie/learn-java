import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { WorkRecordDetail } from '@/pages/work-records/detail'

export const Route = createFileRoute('/_authenticated/work-records/$recordId')({
  beforeLoad: () =>
    requireAnyPermission(['work-record:read:self', 'work-record:read:all']),
  component: WorkRecordDetail,
})

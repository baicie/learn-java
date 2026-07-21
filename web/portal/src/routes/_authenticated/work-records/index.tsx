import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { WorkRecordListPage } from '@/components/work-records/list/work-record-list-page'

const recordsSearchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(30),
  quickView: z.string().catch('all'),
  workdayCount: z.coerce.number().int().min(1).max(60).catch(5),
  templateId: z.string().catch(''),
  templateVersionId: z.string().catch(''),
  statuses: z.array(z.string()).catch([]),
  ownerId: z.string().catch(''),
  creatorId: z.string().catch(''),
  keyword: z.string().catch(''),
  recordTimeFrom: z.string().catch(''),
  recordTimeTo: z.string().catch(''),
  sortBy: z.string().catch('recordTime'),
  sortDir: z.enum(['asc', 'desc']).catch('desc'),
  visibleColumns: z.array(z.string()).catch([]),
})

export const Route = createFileRoute('/_authenticated/work-records/')({
  beforeLoad: () =>
    requireAnyPermission(['work-record:read:self', 'work-record:read:all']),
  validateSearch: recordsSearchSchema,
  component: Component,
})

// eslint-disable-next-line react-refresh/only-export-components -- TanStack Router requires component inline with createFileRoute
function Component() {
  return <WorkRecordListPage />
}

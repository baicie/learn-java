import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { WorkRecordOperationsPage } from '@/pages/work-records/operations'

export const Route = createFileRoute('/_authenticated/work-records/operations')(
  {
    beforeLoad: () => requireAnyPermission(['work-record:analytics']),
    validateSearch: z.object({
      from: z.string().optional(),
      to: z.string().optional(),
    }),
    component: WorkRecordOperationsPage,
  }
)

import { z } from 'zod'
import { createFileRoute, redirect } from '@tanstack/react-router'
import { requireAuthenticated } from '@/auth/permission'
import { canAccessWorkRecordOperations } from '@/auth/work-record-access'
import { WorkRecordOperationsPage } from '@/pages/work-records/operations'

export const Route = createFileRoute('/_authenticated/work-records/operations')(
  {
    beforeLoad: async () => {
      const principal = await requireAuthenticated()
      if (!canAccessWorkRecordOperations(principal)) {
        throw redirect({ to: '/403' })
      }
      return principal
    },
    validateSearch: z.object({
      from: z.string().optional(),
      to: z.string().optional(),
    }),
    component: WorkRecordOperationsPage,
  }
)

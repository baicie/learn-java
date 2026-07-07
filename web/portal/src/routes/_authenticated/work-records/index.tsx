import z from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { WorkRecords } from '@/features/work-records'

const recordsSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(20),
  status: z.array(z.string()).optional().catch([]),
  keyword: z.string().optional().catch(''),
})

export const Route = createFileRoute('/_authenticated/work-records/')({
  validateSearch: recordsSearchSchema,
  component: WorkRecords,
})

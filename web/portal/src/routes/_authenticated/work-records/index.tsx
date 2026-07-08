import z from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { WorkRecords } from '@/features/work-records'
import { dynamicFilterSchema } from '@/features/work-records/data/schema'

const recordsSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(20),
  templateId: z.string().optional().catch(''),
  status: z.array(z.string()).optional().catch([]),
  keyword: z.string().optional().catch(''),
  recordTimeFrom: z.string().optional().catch(''),
  recordTimeTo: z.string().optional().catch(''),
  filters: z.array(dynamicFilterSchema).optional().catch([]),
})

export const Route = createFileRoute('/_authenticated/work-records/')({
  validateSearch: recordsSearchSchema,
  component: WorkRecords,
})

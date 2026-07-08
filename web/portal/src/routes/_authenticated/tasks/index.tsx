import z from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { Tasks } from '@/features/tasks'

const taskSearchSchema = z.object({
  page: z.number().optional().catch(1),
  pageSize: z.number().optional().catch(10),
  status: z
    .array(
      z.union([
        z.literal('backlog'),
        z.literal('todo'),
        z.literal('in progress'),
        z.literal('done'),
        z.literal('canceled'),
      ])
    )
    .optional()
    .catch([]),
  priority: z
    .array(
      z.union([
        z.literal('low'),
        z.literal('medium'),
        z.literal('high'),
        z.literal('critical'),
      ])
    )
    .optional()
    .catch([]),
  filter: z.string().optional().catch(''),
})

export const Route = createFileRoute('/_authenticated/tasks/')({
  validateSearch: taskSearchSchema,
  component: Tasks,
})

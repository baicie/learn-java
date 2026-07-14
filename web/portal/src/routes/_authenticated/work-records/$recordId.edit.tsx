import { createFileRoute } from '@tanstack/react-router'
import { EditWorkRecord } from '@/pages/work-records/edit'

export const Route = createFileRoute(
  '/_authenticated/work-records/$recordId/edit'
)({
  component: EditWorkRecord,
})

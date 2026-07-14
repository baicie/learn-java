import { createFileRoute } from '@tanstack/react-router'
import { NewWorkRecord } from '@/pages/work-records/new'

export const Route = createFileRoute('/_authenticated/work-records/new')({
  component: NewWorkRecord,
})

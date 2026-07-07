import { createFileRoute } from '@tanstack/react-router'
import { NewWorkRecord } from '@/features/work-records/new'

export const Route = createFileRoute('/_authenticated/work-records/new')({
  component: NewWorkRecord,
})

import { createFileRoute } from '@tanstack/react-router'
import { WorkRecordDesigner } from '@/features/work-records/designer'

export const Route = createFileRoute('/_authenticated/work-records/designer')({
  component: WorkRecordDesigner,
})

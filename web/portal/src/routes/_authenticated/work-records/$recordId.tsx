import { createFileRoute } from '@tanstack/react-router'
import { WorkRecordDetail } from '@/pages/work-records/detail'

export const Route = createFileRoute('/_authenticated/work-records/$recordId')({
  component: WorkRecordDetail,
})

import { createFileRoute } from '@tanstack/react-router'
import { WorkRecordTemplatesPage } from '@/pages/work-records/templates'

export const Route = createFileRoute('/_authenticated/work-records/templates/')(
  {
    component: WorkRecordTemplatesPage,
  }
)

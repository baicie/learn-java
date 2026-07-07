import { createFileRoute } from '@tanstack/react-router'
import { TemplateDesignerPage } from '@/features/work-records/components/template-designer-page'

export const Route = createFileRoute('/_authenticated/work-records/designer')({
  component: TemplateDesignerPage,
})

import { FormilyDesignerShell } from './formily-designer-shell'
import { WorkRecordsLayout } from './work-records-layout'

export function TemplateDesignerPage() {
  return (
    <WorkRecordsLayout
      titleKey='workRecords.designer.title'
      descriptionKey='workRecords.designer.description'
    >
      <FormilyDesignerShell />
    </WorkRecordsLayout>
  )
}

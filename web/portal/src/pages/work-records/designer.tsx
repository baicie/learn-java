import { WorkRecordDesignerPage } from '@/components/work-records/designer/work-record-designer-page'

export function WorkRecordDesigner({ templateId }: { templateId: string }) {
  return <WorkRecordDesignerPage templateId={templateId} />
}

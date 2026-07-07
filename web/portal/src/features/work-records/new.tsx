import { RecordForm } from './components/record-form'
import { WorkRecordsLayout } from './components/work-records-layout'

export function NewWorkRecord() {
  return (
    <WorkRecordsLayout titleKey='workRecords.new.title'>
      <RecordForm />
    </WorkRecordsLayout>
  )
}

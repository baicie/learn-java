import { useTranslation } from 'react-i18next'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { exportWorkRecords } from '../api/export-api'

type ExportRecordsButtonProps = {
  status?: string[]
}

export function ExportRecordsButton({ status }: ExportRecordsButtonProps) {
  const { t } = useTranslation()
  async function handleExport() {
    const blob = await exportWorkRecords({ status })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'work-records.csv'
    link.click()
    URL.revokeObjectURL(url)
    toast.success(t('workRecords.list.exportStarted'))
  }

  return (
    <Button variant='outline' onClick={handleExport}>
      <Download className='size-4' />
      {t('common.export')}
    </Button>
  )
}
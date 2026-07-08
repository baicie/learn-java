import { useState } from 'react'
import { Download, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { exportRecords } from '../api/work-record-api'
import type { DynamicFilter } from '../data/schema'

type ExportRecordsDialogProps = {
  templateId?: string
  status?: string[]
  keyword?: string
  filters?: DynamicFilter[]
  total: number
  maxRows?: number
}

export function ExportRecordsDialog({
  templateId,
  status,
  keyword,
  filters,
  total,
  maxRows = 5000,
}: ExportRecordsDialogProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  const exceedsLimit = total > maxRows

  async function handleExport() {
    setLoading(true)
    try {
      const blob = await exportRecords({
        templateId,
        status,
        keyword,
        filters,
        format: 'csv',
      })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      const date = new Date().toISOString().split('T')[0]
      link.href = url
      link.download = `work-records-${date}.csv`
      link.click()
      URL.revokeObjectURL(url)
      toast.success(t('workRecords.export.success'))
      setOpen(false)
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : t('workRecords.export.failed')
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant='outline' size='sm' className='gap-2'>
          <Download className='h-4 w-4' />
          {t('workRecords.list.export')}
        </Button>
      </DialogTrigger>
      <DialogContent className='sm:max-w-[400px]'>
        <DialogHeader>
          <DialogTitle>{t('workRecords.export.title')}</DialogTitle>
          <DialogDescription>
            {t('workRecords.export.description')}
          </DialogDescription>
        </DialogHeader>

        <div className='space-y-3 py-2'>
          <div className='flex justify-between text-sm'>
            <span className='text-muted-foreground'>
              {t('workRecords.export.currentFilters')}
            </span>
            <span>
              {total} {t('workRecords.export.records')}
            </span>
          </div>

          <div className='flex justify-between text-sm'>
            <span className='text-muted-foreground'>
              {t('workRecords.export.format')}
            </span>
            <span>CSV</span>
          </div>

          <div className='flex justify-between text-sm'>
            <span className='text-muted-foreground'>
              {t('workRecords.export.maxRows')}
            </span>
            <span>{maxRows}</span>
          </div>

          {exceedsLimit && (
            <div className='rounded-md border border-destructive bg-destructive/10 p-2 text-sm text-destructive'>
              {t('workRecords.export.tooManyRows', {
                count: total,
                max: maxRows,
              })}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant='outline' onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleExport} disabled={loading || exceedsLimit}>
            {loading && <Loader2 className='mr-2 h-4 w-4 animate-spin' />}
            {t('workRecords.export.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

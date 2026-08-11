import { useState } from 'react'
import { Download, Upload } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  downloadCalendarImportTemplate,
  importCalendarXlsx,
  saveCalendarImportTemplate,
} from '@/api/calendars'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { notify } from '@/components/feedback/app-toaster'
import { FormFieldShell } from '@/components/form/form-field-shell'
import { FileSelect } from '@/components/ui/file-select'

const MAX_FILE_SIZE = 5 * 1024 * 1024

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  calendarId: string
  calendarName: string
  year: number
  onImported: () => Promise<void>
}

export function CalendarImportDialog({
  open,
  onOpenChange,
  calendarId,
  calendarName,
  year,
  onImported,
}: Props) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const validFile =
    file?.name.toLowerCase().endsWith('.xlsx') &&
    file.size > 0 &&
    file.size <= MAX_FILE_SIZE
  const fileError =
    file && !validFile ? t('calendars.import.invalidFile') : undefined

  const changeOpen = (next: boolean) => {
    if (!next) setFile(null)
    onOpenChange(next)
  }

  const downloadTemplate = async () => {
    setDownloading(true)
    try {
      const template = await downloadCalendarImportTemplate(year)
      saveCalendarImportTemplate(template)
      notify.success(t('calendars.import.templateDownloaded'))
    } catch (error) {
      notify.error(error, t('calendars.import.templateDownloadFailed'))
    } finally {
      setDownloading(false)
    }
  }

  const submit = async () => {
    if (!file || !validFile) return
    setSubmitting(true)
    try {
      await importCalendarXlsx(calendarId, file)
      await onImported()
      notify.success(t('calendars.import.success'))
      changeOpen(false)
    } catch (error) {
      notify.error(error, t('calendars.import.failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('calendars.import.title')}</DialogTitle>
          <DialogDescription>
            {t('calendars.import.description')}
          </DialogDescription>
        </DialogHeader>
        <div className='grid gap-4'>
          <div className='grid gap-2 text-sm'>
            <span className='text-muted-foreground'>
              {t('calendars.import.target')}
            </span>
            <span className='font-medium'>{calendarName}</span>
          </div>
          <div className='grid gap-2'>
            <Button
              type='button'
              variant='outline'
              className='justify-self-start'
              disabled={downloading}
              onClick={() => void downloadTemplate()}
            >
              <Download className='size-4' />
              {downloading
                ? t('calendars.import.downloadingTemplate')
                : t('calendars.import.downloadTemplate')}
            </Button>
            <p className='text-xs text-muted-foreground'>
              {t('calendars.import.templateHint')}
            </p>
          </div>
          <FormFieldShell
            id='calendar-import-file'
            label={t('calendars.import.file')}
            error={fileError}
          >
            {(controlProps) => (
              <FileSelect
                id='calendar-import-file'
                accept='.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                file={file}
                onFileChange={setFile}
                nativeInputProps={controlProps as React.InputHTMLAttributes<HTMLInputElement>}
              />
            )}
          </FormFieldShell>
        </div>
        <DialogFooter>
          <Button
            type='button'
            variant='outline'
            onClick={() => changeOpen(false)}
          >
            {t('common.cancel')}
          </Button>
          <Button
            type='button'
            disabled={!validFile || submitting}
            onClick={() => void submit()}
          >
            <Upload className='size-4' />
            {submitting
              ? t('calendars.import.importing')
              : t('calendars.import.action')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

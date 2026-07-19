import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  downloadImportTemplate,
  downloadWorkRecordImportTemplate,
  importWorkRecords,
} from '@/api/work-records/imports'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'
import type { RecordListMeta } from './types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  meta?: RecordListMeta
}

export function WorkRecordImportDialog({ open, onOpenChange, meta }: Props) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [templateId, setTemplateId] = useState('')
  const [defaultStatus, setDefaultStatus] = useState<'draft' | 'done'>('draft')
  const [stopOnError, setStopOnError] = useState(false)
  const [downloadingTemplate, setDownloadingTemplate] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const selected = meta?.templates.find((item) => item.id === templateId)
  const validFile =
    file?.name.toLowerCase().endsWith('.xlsx') &&
    file.size > 0 &&
    file.size <= 20 * 1024 * 1024

  const changeOpen = (next: boolean) => {
    if (!next) {
      setFile(null)
      setTemplateId('')
      setDefaultStatus('draft')
      setStopOnError(false)
    }
    onOpenChange(next)
  }

  const submit = async () => {
    if (!file || !validFile || !selected?.currentVersionId) return
    setSubmitting(true)
    try {
      await importWorkRecords(file, {
        templateId: selected.id,
        templateVersionId: selected.currentVersionId,
        defaultStatus,
        stopOnError,
      })
      notify.success(t('workRecords.import.submitted'))
      changeOpen(false)
    } catch (error) {
      notify.error(error, t('workRecords.import.failed'))
    } finally {
      setSubmitting(false)
    }
  }

  const downloadTemplate = async () => {
    if (!selected?.currentVersionId) return
    setDownloadingTemplate(true)
    try {
      const download = await downloadWorkRecordImportTemplate(
        selected.id,
        selected.currentVersionId
      )
      downloadImportTemplate(download)
      notify.success(t('workRecords.import.templateDownloaded'))
    } catch (error) {
      notify.error(error, t('workRecords.import.templateDownloadFailed'))
    } finally {
      setDownloadingTemplate(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('workRecords.import.title')}</DialogTitle>
          <DialogDescription>
            {t('workRecords.import.description')}
          </DialogDescription>
        </DialogHeader>
        <div className='grid gap-4'>
          <label className='grid gap-2 text-sm'>
            <span>{t('workRecords.import.template')}</span>
            <Select value={templateId} onValueChange={setTemplateId}>
              <SelectTrigger
                className='w-full'
                aria-label={t('workRecords.import.template')}
              >
                <SelectValue
                  placeholder={t('workRecords.import.templatePlaceholder')}
                />
              </SelectTrigger>
              <SelectContent>
                {meta?.templates
                  .filter((item) => item.enabled && item.currentVersionId)
                  .map((item) => (
                    <SelectItem key={item.id} value={item.id}>
                      {item.name}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </label>
          <div className='grid gap-2'>
            <Button
              variant='outline'
              className='justify-self-start'
              disabled={!selected?.currentVersionId || downloadingTemplate}
              onClick={() => void downloadTemplate()}
            >
              {downloadingTemplate
                ? t('workRecords.import.downloadingTemplate')
                : t('workRecords.import.downloadTemplate')}
            </Button>
            <p className='text-xs text-muted-foreground'>
              {t('workRecords.import.templateHint')}
            </p>
          </div>
          <label className='grid gap-2 text-sm'>
            <span>{t('workRecords.import.file')}</span>
            <Input
              type='file'
              accept='.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            {file && !validFile ? (
              <span className='text-destructive'>
                {t('workRecords.import.invalidFile')}
              </span>
            ) : null}
          </label>
          <label className='grid gap-2 text-sm'>
            <span>{t('workRecords.import.defaultStatus')}</span>
            <Select
              value={defaultStatus}
              onValueChange={(value) =>
                setDefaultStatus(value as 'draft' | 'done')
              }
            >
              <SelectTrigger
                className='w-full'
                aria-label={t('workRecords.import.defaultStatus')}
              >
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value='draft'>
                  {t('workRecords.status.draft')}
                </SelectItem>
                <SelectItem value='done'>
                  {t('workRecords.status.done')}
                </SelectItem>
              </SelectContent>
            </Select>
          </label>
          <label className='flex items-center gap-2 text-sm'>
            <Checkbox
              checked={stopOnError}
              onCheckedChange={(value) => setStopOnError(value === true)}
            />
            {t('workRecords.import.stopOnError')}
          </label>
        </div>
        <DialogFooter>
          <Button variant='outline' onClick={() => changeOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={!validFile || !selected?.currentVersionId || submitting}
            onClick={submit}
          >
            {submitting
              ? t('workRecords.import.submitting')
              : t('workRecords.import.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { downloadExport, exportWorkRecords } from '@/api/work-records/export'
import { cn } from '@/lib/utils'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
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
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import type { ListQueryState, RecordListColumn, RecordListMeta } from './types'

type ExportScope = 'filtered' | 'selected'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  query: ListQueryState
  meta?: RecordListMeta
  currentColumns: RecordListColumn[]
  total: number
  selectedIds: string[]
}

export function WorkRecordExportDialog({
  open,
  onOpenChange,
  query,
  meta,
  currentColumns,
  total,
  selectedIds,
}: Props) {
  const { t } = useTranslation()
  const [selectedKeys, setSelectedKeys] = useState<string[]>([])
  const [scope, setScope] = useState<ExportScope>('filtered')
  const [submitting, setSubmitting] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const wasOpen = useRef(false)

  const exportableColumns = useMemo(
    () => (meta?.exportColumns ?? []).filter((column) => column.exportable),
    [meta?.exportColumns]
  )

  const hasDictionaryColumns = useMemo(
    () => exportableColumns.some((column) => Boolean(column.dictCode)),
    [exportableColumns]
  )

  const currentExportableKeys = useMemo(
    () =>
      currentColumns
        .filter((column) => column.exportable)
        .map((column) => column.key),
    [currentColumns]
  )

  const maxRows = meta?.maxExportRows ?? 5000
  const selectedCount = selectedIds.length
  const effectiveTotal = scope === 'selected' ? selectedCount : total
  const overLimit = effectiveTotal > maxRows

  const resetDialog = useCallback(() => {
    setSelectedKeys(currentExportableKeys)
    setScope(selectedIds.length > 0 ? 'selected' : 'filtered')
    setConfirmed(false)
    setError(null)
  }, [currentExportableKeys, selectedIds.length])

  useEffect(() => {
    if (open && !wasOpen.current) {
      resetDialog()
    }

    wasOpen.current = open
  }, [open, resetDialog])

  const handleOpenChange = (next: boolean) => {
    onOpenChange(next)
  }

  const toggleColumn = (key: string) => {
    setSelectedKeys((current) =>
      current.includes(key)
        ? current.filter((item) => item !== key)
        : [...current, key]
    )
  }

  const handleExport = async () => {
    if (!confirmed) {
      setError(t('workRecords.export.confirmRequired'))
      return
    }

    if (!selectedKeys.length) {
      setError(t('workRecords.export.columnRequired'))
      return
    }

    if (overLimit) {
      setError(
        t('workRecords.export.tooManyRows', {
          count: effectiveTotal,
          max: maxRows,
        })
      )
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const download = await exportWorkRecords(
        query,
        selectedKeys,
        scope === 'selected' ? selectedIds : undefined
      )

      downloadExport(download)
      onOpenChange(false)
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : t('workRecords.export.failed')
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className='max-w-2xl'>
        <DialogHeader>
          <DialogTitle>{t('workRecords.export.title')}</DialogTitle>
          <DialogDescription>
            {t('workRecords.export.descriptionWithLimit', { max: maxRows })}
          </DialogDescription>
        </DialogHeader>

        <div className='grid gap-4'>
          {hasDictionaryColumns ? (
            <Alert>
              <Info />
              <AlertTitle>
                {t('workRecords.export.dictionaryNoteTitle')}
              </AlertTitle>
              <AlertDescription>
                {t('workRecords.export.dictionaryNote')}
              </AlertDescription>
            </Alert>
          ) : null}

          <div className='grid gap-2 rounded-md border p-3'>
            <div className='text-sm font-medium'>
              {t('workRecords.export.scope')}
            </div>
            <RadioGroup
              value={scope}
              onValueChange={(value) => setScope(value as ExportScope)}
            >
              <label className='flex items-center gap-2 text-sm'>
                <RadioGroupItem value='filtered' />
                <span>
                  {t('workRecords.export.scopeFiltered', { count: total })}
                </span>
              </label>
              <label
                className={cn(
                  'flex items-center gap-2 text-sm',
                  !selectedCount && 'text-muted-foreground'
                )}
              >
                <RadioGroupItem value='selected' disabled={!selectedCount} />
                <span>
                  {t('workRecords.export.scopeSelected', {
                    count: selectedCount,
                  })}
                </span>
              </label>
            </RadioGroup>
          </div>

          <div className='rounded-md border p-3 text-sm'>
            <div>
              {t('workRecords.export.currentFilters')}:
              <strong className='ml-1'>
                {t('workRecords.export.rowCount', { count: total })}
              </strong>
            </div>

            <div>
              {t('workRecords.export.maxRows')}:
              <strong className='ml-1'>
                {t('workRecords.export.rowCount', { count: maxRows })}
              </strong>
            </div>

            {overLimit ? (
              <div className='mt-2 text-destructive'>
                {t('workRecords.export.tooManyRows', {
                  count: effectiveTotal,
                  max: maxRows,
                })}
              </div>
            ) : null}
          </div>

          <div className='flex items-center justify-between'>
            <div className='text-sm font-medium'>
              {t('workRecords.export.selectColumns')}
            </div>

            <div className='flex gap-2'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                onClick={() => setSelectedKeys(currentExportableKeys)}
              >
                {t('workRecords.export.currentColumns')}
              </Button>

              <Button
                type='button'
                variant='outline'
                size='sm'
                onClick={() =>
                  setSelectedKeys(exportableColumns.map((column) => column.key))
                }
              >
                {t('workRecords.export.allColumns')}
              </Button>
            </div>
          </div>

          <div className='grid max-h-72 gap-2 overflow-auto rounded-md border p-3 sm:grid-cols-2'>
            {exportableColumns.map((column) => (
              <label
                key={column.key}
                className='flex items-center gap-2 text-sm'
              >
                <Checkbox
                  checked={selectedKeys.includes(column.key)}
                  onCheckedChange={() => toggleColumn(column.key)}
                />

                <span>{column.title}</span>

                {column.source === 'custom' ? (
                  <span className='text-xs text-muted-foreground'>
                    {t('workRecords.export.dynamicField')}
                  </span>
                ) : null}
              </label>
            ))}
          </div>

          <label className='flex items-start gap-2 rounded-md border p-3 text-sm'>
            <Checkbox
              checked={confirmed}
              onCheckedChange={(checked) => setConfirmed(checked === true)}
            />

            <span>{t('workRecords.export.auditConfirm')}</span>
          </label>

          {error ? (
            <div className='rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive'>
              {error}
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button
            type='button'
            variant='outline'
            disabled={submitting}
            onClick={() => onOpenChange(false)}
          >
            {t('common.cancel')}
          </Button>

          <Button
            type='button'
            disabled={
              submitting || overLimit || !selectedKeys.length || !confirmed
            }
            onClick={handleExport}
          >
            {submitting
              ? t('workRecords.export.submitting')
              : t('workRecords.export.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

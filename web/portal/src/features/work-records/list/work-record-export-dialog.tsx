import { useCallback, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { downloadExport, exportWorkRecords } from './export-api'
import type { ListQueryState, RecordListColumn, RecordListMeta } from './types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  query: ListQueryState
  meta?: RecordListMeta
  currentColumns: RecordListColumn[]
  total: number
}

export function WorkRecordExportDialog({
  open,
  onOpenChange,
  query,
  meta,
  currentColumns,
  total,
}: Props) {
  const [selectedKeys, setSelectedKeys] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const exportableColumns = useMemo(
    () => (meta?.columns ?? []).filter((column) => column.exportable),
    [meta?.columns]
  )

  const currentExportableKeys = useMemo(
    () =>
      currentColumns
        .filter((column) => column.exportable)
        .map((column) => column.key),
    [currentColumns]
  )

  const maxRows = meta?.maxExportRows ?? 5000
  const overLimit = total > maxRows

  const resetDialog = useCallback(() => {
    setSelectedKeys(currentExportableKeys)
    setConfirmed(false)
    setError(null)
  }, [currentExportableKeys])

  const handleOpenChange = (next: boolean) => {
    onOpenChange(next)
    if (next) {
      resetDialog()
    }
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
      setError('请先确认导出操作')
      return
    }

    if (!selectedKeys.length) {
      setError('至少选择一个导出列')
      return
    }

    if (overLimit) {
      setError(
        `当前结果共 ${total} 条，超过最大导出行数 ${maxRows}，请缩小筛选范围`
      )
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const download = await exportWorkRecords(query, selectedKeys)

      downloadExport(download)
      onOpenChange(false)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '导出失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className='max-w-2xl'>
        <DialogHeader>
          <DialogTitle>导出工作记录</DialogTitle>
          <DialogDescription>
            导出当前筛选结果。第一版使用同步 CSV，最多导出 {maxRows} 行。
          </DialogDescription>
        </DialogHeader>

        <div className='grid gap-4'>
          <div className='rounded-md border p-3 text-sm'>
            <div>
              当前筛选结果：
              <strong className='ml-1'>{total}</strong>条
            </div>

            <div>
              最大导出行数：
              <strong className='ml-1'>{maxRows}</strong>条
            </div>

            {overLimit ? (
              <div className='mt-2 text-red-600'>
                当前结果超过导出上限，请继续添加筛选条件。
              </div>
            ) : null}
          </div>

          <div className='flex items-center justify-between'>
            <div className='text-sm font-medium'>选择导出列</div>

            <div className='flex gap-2'>
              <Button
                type='button'
                variant='outline'
                size='sm'
                onClick={() => setSelectedKeys(currentExportableKeys)}
              >
                当前显示列
              </Button>

              <Button
                type='button'
                variant='outline'
                size='sm'
                onClick={() =>
                  setSelectedKeys(exportableColumns.map((column) => column.key))
                }
              >
                全部可导出列
              </Button>
            </div>
          </div>

          <div className='grid max-h-72 gap-2 overflow-auto rounded-md border p-3 sm:grid-cols-2'>
            {exportableColumns.map((column) => (
              <label
                key={column.key}
                className='flex items-center gap-2 text-sm'
              >
                <input
                  type='checkbox'
                  checked={selectedKeys.includes(column.key)}
                  onChange={() => toggleColumn(column.key)}
                />

                <span>{column.title}</span>

                {column.source === 'custom' ? (
                  <span className='text-xs text-muted-foreground'>
                    动态字段
                  </span>
                ) : null}
              </label>
            ))}
          </div>

          <label className='flex items-start gap-2 rounded-md border p-3 text-sm'>
            <input
              type='checkbox'
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />

            <span>我确认导出当前筛选结果。导出行为会被记录到审计日志。</span>
          </label>

          {error ? (
            <div className='rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700'>
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
            取消
          </Button>

          <Button
            type='button'
            disabled={
              submitting || overLimit || !selectedKeys.length || !confirmed
            }
            onClick={handleExport}
          >
            {submitting ? '导出中...' : '确认导出'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileUp } from 'lucide-react'
import {
  confirmAssetImport,
  downloadAssetTemplate,
  listAssetImportRows,
  previewAssetImport,
} from '@/api/assets/assets-api'
import { assetKeys } from '@/api/assets/query-keys'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { notify } from '@/components/feedback/app-toaster'

export function AssetImportDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [sourceInstanceId, setSourceInstanceId] = useState('manual-csv')
  const [jobId, setJobId] = useState('')
  const preview = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('请选择 CSV 文件')
      return previewAssetImport(file, sourceInstanceId)
    },
    onSuccess: (job) => setJobId(job.jobId),
    onError: (error) => notify.error(error, 'CSV 预检失败'),
  })
  const rows = useQuery({
    queryKey: assetKeys.import(jobId),
    queryFn: () => listAssetImportRows(jobId),
    enabled: Boolean(jobId),
  })
  const job = preview.data
  const confirm = useMutation({
    mutationFn: () => confirmAssetImport(jobId),
    onSuccess: async () => {
      notify.success('CSV 资源导入完成')
      await queryClient.invalidateQueries({ queryKey: assetKeys.all })
    },
    onError: (error) => notify.error(error, '确认导入失败'),
  })

  const download = async () => {
    const blob = await downloadAssetTemplate()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'asset-import-template.csv'
    anchor.click()
    URL.revokeObjectURL(url)
  }
  const step = confirm.data ? 3 : jobId ? 2 : 1

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!confirm.isPending) onOpenChange(next)
      }}
    >
      <DialogContent className='max-h-[92vh] overflow-y-auto sm:max-w-5xl'>
        <DialogHeader>
          <DialogTitle>导入 CSV 资源</DialogTitle>
          <DialogDescription>
            步骤 {step}/3：
            {step === 1 ? '上传文件' : step === 2 ? '校验预览' : '导入结果'}
            。预检不会修改规范资源。
          </DialogDescription>
        </DialogHeader>
        {step === 1 ? (
          <div className='grid gap-4 py-4'>
            <div className='rounded-lg border border-dashed p-8 text-center'>
              <FileUp className='mx-auto mb-3 size-8 text-muted-foreground' />
              <p className='font-medium'>选择 UTF-8 CSV 文件</p>
              <p className='mb-4 text-sm text-muted-foreground'>
                最大 5 MiB、5000 行；先下载模板可避免表头错误。
              </p>
              <Input
                type='file'
                accept='.csv,text/csv'
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </div>
            <div className='grid gap-2'>
              <Label>来源实例标识</Label>
              <Input
                value={sourceInstanceId}
                onChange={(event) => setSourceInstanceId(event.target.value)}
              />
            </div>
            <Button
              variant='outline'
              className='justify-self-start'
              onClick={() => void download()}
            >
              <Download />
              下载模板
            </Button>
          </div>
        ) : step === 2 && job ? (
          <div className='grid gap-4 py-3'>
            <div className='grid gap-3 sm:grid-cols-4'>
              <Stat label='有效' value={job.validRows} />
              <Stat label='预计新增/更新' value={job.validRows} />
              <Stat label='冲突' value={job.conflictRows} />
              <Stat label='错误' value={job.invalidRows} />
            </div>
            <div className='max-h-96 overflow-auto rounded-lg border'>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>行号</TableHead>
                    <TableHead>External ID</TableHead>
                    <TableHead>动作</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>问题</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.data?.items.map((row) => (
                    <TableRow key={row.rowNumber}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.externalId}</TableCell>
                      <TableCell>{row.resolutionAction ?? '—'}</TableCell>
                      <TableCell>{row.validationStatus}</TableCell>
                      <TableCell className='text-destructive'>
                        {row.errorCodes.join('、') || '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {job.conflictRows ? (
              <p className='text-sm text-destructive'>
                存在未处理冲突，第一阶段需跳过冲突行后才能确认。
              </p>
            ) : null}
          </div>
        ) : (
          <div className='py-10 text-center'>
            <h3 className='text-lg font-semibold'>导入完成</h3>
            <p className='mt-2 text-sm text-muted-foreground'>
              新增 {confirm.data?.createdRows ?? 0} 条，更新{' '}
              {confirm.data?.updatedRows ?? 0} 条。
            </p>
          </div>
        )}
        <DialogFooter>
          {step === 1 ? (
            <Button
              disabled={!file || preview.isPending}
              onClick={() => preview.mutate()}
            >
              {preview.isPending ? '预检中…' : '开始校验'}
            </Button>
          ) : step === 2 ? (
            <Button
              disabled={
                !job?.validRows ||
                Boolean(job.conflictRows) ||
                confirm.isPending
              }
              onClick={() => confirm.mutate()}
            >
              {confirm.isPending
                ? '导入中…'
                : `确认导入 ${job?.validRows ?? 0} 条`}
            </Button>
          ) : (
            <Button onClick={() => onOpenChange(false)}>完成</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className='p-4'>
        <p className='text-xs text-muted-foreground'>{label}</p>
        <p className='text-2xl font-semibold'>{value}</p>
      </CardContent>
    </Card>
  )
}

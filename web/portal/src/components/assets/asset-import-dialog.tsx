import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileUp } from 'lucide-react'
import {
  confirmAssetImport,
  downloadAssetImportProblems,
  downloadAssetTemplate,
  listAssetImportRows,
  previewAssetImport,
  resolveAssetImportConflict,
} from '@/api/assets/assets-api'
import { assetKeys } from '@/api/assets/query-keys'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { FileSelect } from '@/components/ui/file-select'
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
  const [currentJob, setCurrentJob] =
    useState<Awaited<ReturnType<typeof previewAssetImport>>>()
  const [rowStatus, setRowStatus] = useState('all')
  const [linkTargets, setLinkTargets] = useState<Record<number, string>>({})
  const preview = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('请选择 CSV 文件')
      return previewAssetImport(file, sourceInstanceId)
    },
    onSuccess: (job) => {
      setJobId(job.jobId)
      setCurrentJob(job)
    },
    onError: (error) => notify.error(error, 'CSV 预检失败'),
  })
  const rows = useQuery({
    queryKey: [...assetKeys.import(jobId), rowStatus],
    queryFn: () =>
      listAssetImportRows(jobId, rowStatus === 'all' ? undefined : rowStatus),
    enabled: Boolean(jobId),
  })
  const job = currentJob
  const resolve = useMutation({
    mutationFn: ({
      rowNumber,
      action,
    }: {
      rowNumber: number
      action: 'create' | 'link' | 'skip'
    }) =>
      resolveAssetImportConflict(
        jobId,
        rowNumber,
        action,
        linkTargets[rowNumber]
      ),
    onSuccess: async (nextJob) => {
      setCurrentJob(nextJob)
      await queryClient.invalidateQueries({ queryKey: assetKeys.import(jobId) })
    },
    onError: (error) => notify.error(error, '处理冲突失败'),
  })
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
  const downloadProblems = async () => {
    const blob = await downloadAssetImportProblems(jobId)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `asset-import-${jobId}-problems.csv`
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
              <FileSelect
                id='asset-import-file'
                accept='.csv,text/csv'
                file={file}
                onFileChange={setFile}
                className='mx-auto max-w-xs justify-center'
                buttonChildren={
                  <>
                    <FileUp className='size-4' />
                    {file?.name ?? '选择文件'}
                  </>
                }
                showFileNameHint={!!file === false}
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
              <Stat label='预计新增' value={job.createdRows} />
              <Stat label='预计更新/关联' value={job.updatedRows} />
              <Stat label='冲突' value={job.conflictRows} />
              <Stat label='错误' value={job.invalidRows} />
            </div>
            <div className='flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between'>
              <Select value={rowStatus} onValueChange={setRowStatus}>
                <SelectTrigger className='w-full sm:w-48'>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value='all'>全部结果</SelectItem>
                  <SelectItem value='valid'>有效</SelectItem>
                  <SelectItem value='conflict'>冲突</SelectItem>
                  <SelectItem value='invalid'>错误</SelectItem>
                </SelectContent>
              </Select>
              <Button
                variant='outline'
                disabled={!job.conflictRows && !job.invalidRows}
                onClick={() => void downloadProblems()}
              >
                <Download />
                下载问题行
              </Button>
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
                    <TableHead>冲突处理</TableHead>
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
                      <TableCell>
                        {row.validationStatus === 'conflict' &&
                        !row.resolutionAction ? (
                          <div className='flex min-w-80 items-center gap-1'>
                            <Button
                              size='sm'
                              variant='outline'
                              onClick={() =>
                                resolve.mutate({
                                  rowNumber: row.rowNumber,
                                  action: 'skip',
                                })
                              }
                            >
                              跳过
                            </Button>
                            <Button
                              size='sm'
                              variant='outline'
                              onClick={() =>
                                resolve.mutate({
                                  rowNumber: row.rowNumber,
                                  action: 'create',
                                })
                              }
                            >
                              独立创建
                            </Button>
                            <Input
                              className='h-8'
                              placeholder='目标 Asset ID'
                              value={linkTargets[row.rowNumber] ?? ''}
                              onChange={(event) =>
                                setLinkTargets((current) => ({
                                  ...current,
                                  [row.rowNumber]: event.target.value,
                                }))
                              }
                            />
                            <Button
                              size='sm'
                              disabled={!linkTargets[row.rowNumber]}
                              onClick={() =>
                                resolve.mutate({
                                  rowNumber: row.rowNumber,
                                  action: 'link',
                                })
                              }
                            >
                              关联
                            </Button>
                          </div>
                        ) : (
                          (row.resolutionAction ?? '—')
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {job.conflictRows ? (
              <p className='text-sm text-destructive'>
                存在未处理冲突，请选择跳过、独立创建或关联到已有资源。
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
                !(job?.createdRows || job?.updatedRows) ||
                Boolean(job.conflictRows) ||
                confirm.isPending
              }
              onClick={() => confirm.mutate()}
            >
              {confirm.isPending
                ? '导入中…'
                : `确认导入 ${(job?.createdRows ?? 0) + (job?.updatedRows ?? 0)} 条`}
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

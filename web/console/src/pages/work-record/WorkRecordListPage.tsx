import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '../../components/ui/badge'
import { Button, buttonVariants } from '../../components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table'
import {
  createWorkRecord,
  deleteWorkRecord,
  exportWorkRecordsCsv,
  listWorkRecords,
} from '../../features/work-record/api'

const PAGE_SIZE = 20

export function WorkRecordListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const records = useQuery({
    queryKey: ['work-records', page],
    queryFn: () => listWorkRecords({ page, size: PAGE_SIZE }),
  })
  const createRecord = useMutation({
    mutationFn: createWorkRecord,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-records'] }),
  })
  const removeRecord = useMutation({
    mutationFn: (id: string) => deleteWorkRecord(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-records'] }),
  })
  const exportCsv = useMutation({
    mutationFn: () => exportWorkRecordsCsv(),
  })

  const total = records.data?.total ?? 0
  const items = records.data?.items ?? []
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <section className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">工作记录</h1>
          <p className="text-sm text-muted-foreground">
            运维日报、故障记录、巡检记录与变更记录的填写与查询。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            disabled={exportCsv.isPending}
            onClick={() => exportCsv.mutate()}
          >
            {exportCsv.isPending ? '导出中…' : '导出 CSV'}
          </Button>
          <Link
            to="/app/work-records/create"
            className={buttonVariants({ variant: 'default', size: 'default' })}
          >
            新建记录
          </Link>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>记录列表</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>标题</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>记录时间</TableHead>
                <TableHead>创建人</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((record) => (
                <TableRow key={record.id}>
                  <TableCell className="font-medium">{record.title}</TableCell>
                  <TableCell>
                    <Badge variant={record.status === 'done' ? 'secondary' : 'outline'}>
                      {record.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{new Date(record.recordTime).toLocaleString()}</TableCell>
                  <TableCell>{record.creatorId}</TableCell>
                  <TableCell className="space-x-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => navigate(`/app/work-records/${record.id}/edit`)}
                    >
                      编辑
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={removeRecord.isPending}
                      onClick={() => removeRecord.mutate(record.id)}
                    >
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground">
                    暂无工作记录
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
            <div>
              共 {total} 条 · 第 {page} / {totalPages} 页
            </div>
            <div className="space-x-2">
              <Button
                size="sm"
                variant="outline"
                disabled={page <= 1 || records.isFetching}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                上一页
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={page >= totalPages || records.isFetching}
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              >
                下一页
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>快速新建（演示）</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-3 md:grid-cols-[2fr_1fr_auto]"
            onSubmit={(e) => {
              e.preventDefault()
              const form = e.currentTarget as HTMLFormElement
              const data = new FormData(form)
              const templateId = String(data.get('templateId') ?? '')
              const title = String(data.get('title') ?? '')
              if (!templateId || !title) return
              createRecord.mutate({
                templateId,
                title,
                status: 'draft',
                recordTime: new Date().toISOString(),
                builtinDataJson: '{}',
                customDataJson: '{}',
              })
              form.reset()
            }}
          >
            <div className="space-y-1">
              <Label htmlFor="wr-template">模板 ID</Label>
              <Input
                id="wr-template"
                name="templateId"
                placeholder="由表单设计创建后获取"
                required
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="wr-title">标题</Label>
              <Input id="wr-title" name="title" placeholder="例如 7 月 6 日巡检" required />
            </div>
            <Button type="submit" disabled={createRecord.isPending} className="self-end">
              {createRecord.isPending ? '提交中…' : '创建草稿'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </section>
  )
}

# Phase 20.8：Portal、权限初始化、测试与企业验收

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

### 4.8 V0035：权限初始化

```sql
-- V0035__phase20_permissions.sql

insert into iam.permission(
    permission_code,
    permission_name,
    module_code,
    resource_type,
    description
)
values
    ('work-record:import', '导入工作记录', 'work-record', 'ACTION', '通过 Excel 批量导入工作记录'),
    ('work-record:export:async', '异步导出工作记录', 'work-record', 'ACTION', '创建和下载异步导出任务'),
    ('work-record:comment', '评论工作记录', 'work-record', 'ACTION', '创建和维护本人评论'),
    ('work-record:comment:moderate', '管理工作记录评论', 'work-record', 'ACTION', '删除任意工作记录评论'),
    ('work-record:attachment', '管理本人附件', 'work-record', 'ACTION', '上传、下载和删除本人附件'),
    ('work-record:attachment:moderate', '管理全部附件', 'work-record', 'ACTION', '删除任意工作记录附件'),
    ('work-record:relation', '关联运维对象', 'work-record', 'ACTION', '关联告警、巡检和事件'),
    ('work-record:analytics', '查看工作记录统计', 'work-record', 'API', '查看统计报表和工作量分析'),
    ('work-record:reminder:manage', '管理日报提醒', 'work-record', 'ACTION', '配置日报缺失提醒'),
    ('work-record:handover', '值班交接', 'work-record', 'ACTION', '创建、接收和完成值班交接'),
    ('work-record:ai:generate', '生成 AI 工作记录内容', 'work-record', 'ACTION', '生成记录总结和月报草稿'),
    ('work-record:ai:review', '审核 AI 工作记录内容', 'work-record', 'ACTION', '接受或拒绝 AI 生成结果'),
    ('work-record:market:read', '查看模板市场', 'work-record', 'API', '浏览模板市场'),
    ('work-record:market:install', '安装市场模板', 'work-record', 'ACTION', '从市场安装模板'),
    ('work-record:market:publish', '发布市场模板', 'work-record', 'ACTION', '发布或撤回模板包'),
    ('work-record:field-policy:manage', '管理字段权限', 'work-record', 'ACTION', '配置字段读写角色和脱敏'),
    ('work-record:approval:manage', '管理审批流', 'work-record', 'ACTION', '配置审批定义'),
    ('work-record:approval:act', '审批工作记录', 'work-record', 'ACTION', '处理待审批任务'),
    ('work-record:sla:read', '查看工作记录 SLA', 'work-record', 'API', '查看记录 SLA 状态'),
    ('work-record:sla:manage', '管理工作记录 SLA', 'work-record', 'ACTION', '配置 SLA 策略')
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    module_code = excluded.module_code,
    resource_type = excluded.resource_type,
    description = excluded.description,
    enabled = true,
    updated_at = now();

insert into iam.role_permission(role_code, permission_code)
select role_code, permission_code
from (
    values ('system_admin'), ('record_admin')
) roles(role_code)
cross join iam.permission permission
where permission.permission_code like 'work-record:%'
on conflict do nothing;

insert into iam.role_permission(role_code, permission_code)
select 'normal_user', permission_code
from iam.permission
where permission_code in (
    'work-record:import',
    'work-record:export:async',
    'work-record:comment',
    'work-record:attachment',
    'work-record:relation',
    'work-record:handover',
    'work-record:ai:generate',
    'work-record:market:read',
    'work-record:market:install',
    'work-record:sla:read'
)
on conflict do nothing;

insert into iam.role_permission(role_code, permission_code)
select 'readonly_user', permission_code
from iam.permission
where permission_code in (
    'work-record:market:read',
    'work-record:sla:read'
)
on conflict do nothing;
```

`PermissionCodes.java` 同步增加以上常量，并建立 `PHASE_20_PERMISSIONS` 集合；`ALL_PERMISSIONS` 合并 Phase 13 与 Phase 20，避免 `requireKnown()` 拒绝新权限。

## 15. Phase 20.8：Portal 页面与交互

### 15.1 页面规划

```text
/work-records/:recordId
  基本信息
  动态字段
  评论
  附件
  关联对象
  变更历史
  AI 总结
  审批状态
  SLA 状态

/work-records/jobs
  我的导入/导出/AI 异步任务

/work-records/analytics
  统计报表
  工作量分析
  AI 月报

/work-records/handovers
  我的交接
  待接收
  已完成

/work-records/market
  模板市场
  已安装模板

/work-records/approvals
  待我审批
  我发起的审批
```

详情页保留现有 `DetailPageLayout`，新增内容放到 `RecordExtensionPanel`；不修改动态字段只读渲染器。

### 15.2 extension-types.ts

```ts
import { z } from 'zod'

export const commentSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  recordId: z.string(),
  content: z.string(),
  mentionUserIds: z.array(z.string()).default([]),
  createdBy: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  rowVersion: z.number().int(),
})

export const attachmentSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  fileName: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  sha256: z.string(),
  uploadedBy: z.string(),
  createdAt: z.string(),
})

export const relationSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  relationType: z.enum(['ALERT', 'INSPECTION', 'INCIDENT']),
  targetId: z.string(),
  targetTitle: z.string().nullable(),
  targetStatus: z.string().nullable(),
  snapshotJson: z.string(),
  createdAt: z.string(),
})

export const asyncJobSchema = z.object({
  id: z.string(),
  jobType: z.enum(['RECORD_IMPORT', 'RECORD_EXPORT', 'AI_RECORD_SUMMARY', 'AI_MONTHLY_REPORT']),
  status: z.enum(['QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED']),
  requestedBy: z.string(),
  totalCount: z.number().int(),
  processedCount: z.number().int(),
  successCount: z.number().int(),
  failureCount: z.number().int(),
  resultFileName: z.string().nullable(),
  errorMessage: z.string().nullable(),
  createdAt: z.string(),
  startedAt: z.string().nullable(),
  finishedAt: z.string().nullable(),
})

export const aiGenerationSchema = z.object({
  id: z.string(),
  generationType: z.enum(['record_summary', 'monthly_report']),
  resourceType: z.enum(['record', 'tenant_month']),
  resourceId: z.string(),
  status: z.enum(['queued', 'running', 'success', 'failed', 'accepted', 'rejected']),
  outputMarkdown: z.string().nullable(),
  provider: z.string().nullable(),
  model: z.string().nullable(),
  requestedBy: z.string(),
  reviewedBy: z.string().nullable(),
  createdAt: z.string(),
})

export const approvalTaskSchema = z.object({
  id: z.string(),
  instanceId: z.string(),
  stepNo: z.number().int(),
  status: z.string(),
  assigneeType: z.string(),
  assigneeValue: z.string(),
  dueAt: z.string().nullable(),
  createdAt: z.string(),
})

export const slaInstanceSchema = z.object({
  id: z.string(),
  recordId: z.string(),
  status: z.enum(['running', 'met', 'breached', 'cancelled']),
  startedAt: z.string(),
  dueAt: z.string(),
  stoppedAt: z.string().nullable(),
  breachedAt: z.string().nullable(),
  severity: z.enum(['info', 'warning', 'critical']),
})

export const statisticsSchema = z.object({
  totalRecords: z.number(),
  completedRecords: z.number(),
  distinctOwners: z.number(),
  series: z.array(
    z.object({
      key: z.string(),
      label: z.string(),
      count: z.number(),
      value: z.number().nullable(),
    })
  ),
  fieldAggregate: z
    .object({
      fieldCode: z.string(),
      sum: z.number().nullable(),
      average: z.number().nullable(),
      minimum: z.number().nullable(),
      maximum: z.number().nullable(),
      valueCount: z.number(),
    })
    .nullable(),
})

export type WorkRecordComment = z.infer<typeof commentSchema>
export type WorkRecordAttachment = z.infer<typeof attachmentSchema>
export type RecordRelation = z.infer<typeof relationSchema>
export type AsyncJob = z.infer<typeof asyncJobSchema>
export type AiGeneration = z.infer<typeof aiGenerationSchema>
export type ApprovalTask = z.infer<typeof approvalTaskSchema>
export type SlaInstance = z.infer<typeof slaInstanceSchema>
export type StatisticsResult = z.infer<typeof statisticsSchema>
```

### 15.3 extension-api.ts

```ts
import { z } from 'zod'
import { request } from '@/lib/request'
import {
  aiGenerationSchema,
  asyncJobSchema,
  attachmentSchema,
  commentSchema,
  relationSchema,
  slaInstanceSchema,
  statisticsSchema,
} from './extension-types'

const unwrap = <T>(schema: z.ZodType<T>, value: unknown): T => {
  const envelope = z.object({ success: z.boolean(), data: schema }).parse(value)
  return envelope.data
}

export async function listComments(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/comments`)
  return unwrap(z.array(commentSchema), value)
}

export async function createComment(
  recordId: string,
  input: { content: string; mentionUserIds: string[] }
) {
  const value = await request(`/api/work-record/records/${recordId}/comments`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(commentSchema, value)
}

export async function deleteComment(recordId: string, commentId: string, rowVersion: number) {
  await request(
    `/api/work-record/records/${recordId}/comments/${commentId}?rowVersion=${rowVersion}`,
    { method: 'DELETE' }
  )
}

export async function listAttachments(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/attachments`)
  return unwrap(z.array(attachmentSchema), value)
}

export async function uploadAttachment(recordId: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  const value = await request(`/api/work-record/records/${recordId}/attachments`, {
    method: 'POST',
    body,
    omitContentType: true,
  })
  return unwrap(attachmentSchema, value)
}

export async function attachmentDownloadUrl(attachmentId: string) {
  const value = await request(`/api/work-record/attachments/${attachmentId}/download-url`)
  return unwrap(z.object({ url: z.string().url(), fileName: z.string() }), value)
}

export async function listRelations(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/relations`)
  return unwrap(z.array(relationSchema), value)
}

export async function createRelation(
  recordId: string,
  input: { relationType: string; targetId: string }
) {
  const value = await request(`/api/work-record/records/${recordId}/relations`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(relationSchema, value)
}

export async function submitImport(input: {
  file: File
  templateId: string
  templateVersionId: string
  defaultStatus: string
  stopOnError: boolean
}) {
  const body = new FormData()
  body.append('file', input.file)
  body.append('templateId', input.templateId)
  body.append('templateVersionId', input.templateVersionId)
  body.append('defaultStatus', input.defaultStatus)
  body.append('stopOnError', String(input.stopOnError))
  const value = await request('/api/work-record/imports', {
    method: 'POST',
    body,
    omitContentType: true,
  })
  return unwrap(z.object({ jobId: z.string() }), value)
}

export async function submitAsyncExport(input: unknown) {
  const value = await request('/api/work-record/jobs/exports', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  return unwrap(z.object({ jobId: z.string() }), value)
}

export async function listAsyncJobs() {
  const value = await request('/api/work-record/jobs')
  return unwrap(z.array(asyncJobSchema), value)
}

export async function asyncJobDownloadUrl(jobId: string) {
  const value = await request(`/api/work-record/jobs/${jobId}/download-url`)
  return unwrap(z.object({ url: z.string().url(), fileName: z.string() }), value)
}

export async function requestRecordAiSummary(recordId: string) {
  const value = await request(`/api/work-record/ai/records/${recordId}/summary`, {
    method: 'POST',
  })
  return unwrap(aiGenerationSchema, value)
}

export async function listRecordAiGenerations(recordId: string) {
  const value = await request(`/api/work-record/ai/records/${recordId}`)
  return unwrap(z.array(aiGenerationSchema), value)
}

export async function reviewAiGeneration(id: string, accepted: boolean) {
  const value = await request(`/api/work-record/ai/${id}/review`, {
    method: 'POST',
    body: JSON.stringify({ accepted }),
  })
  return unwrap(aiGenerationSchema, value)
}

export async function recordSla(recordId: string) {
  const value = await request(`/api/work-record/records/${recordId}/sla`)
  return unwrap(z.array(slaInstanceSchema), value)
}

export async function statistics(search: URLSearchParams) {
  const value = await request(`/api/work-record/analytics/statistics?${search}`)
  return unwrap(statisticsSchema, value)
}
```

`request()` 需要支持 `FormData`：当 `omitContentType=true` 时，不手工设置 `Content-Type`，由浏览器写入 multipart boundary。

### 15.4 RecordExtensionPanel.tsx

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ErrorState, TableLoadingState } from '@/components/feedback/async-state'
import {
  listAttachments,
  listComments,
  listRecordAiGenerations,
  listRelations,
  recordSla,
} from './extension-api'
import { AttachmentPanel } from './record-attachment-panel'
import { CommentTimeline } from './record-comment-timeline'
import { RecordAiPanel } from './record-ai-panel'
import { RecordRelationPanel } from './record-relation-panel'
import { RecordSlaPanel } from './record-sla-panel'

export function RecordExtensionPanel({ recordId }: { recordId: string }) {
  const [tab, setTab] = useState('comments')
  const comments = useQuery({
    queryKey: ['work-record-comments', recordId],
    queryFn: () => listComments(recordId),
  })
  const attachments = useQuery({
    queryKey: ['work-record-attachments', recordId],
    queryFn: () => listAttachments(recordId),
    enabled: tab === 'attachments',
  })
  const relations = useQuery({
    queryKey: ['work-record-relations', recordId],
    queryFn: () => listRelations(recordId),
    enabled: tab === 'relations',
  })
  const ai = useQuery({
    queryKey: ['work-record-ai', recordId],
    queryFn: () => listRecordAiGenerations(recordId),
    enabled: tab === 'ai',
  })
  const sla = useQuery({
    queryKey: ['work-record-sla', recordId],
    queryFn: () => recordSla(recordId),
    enabled: tab === 'sla',
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>协作与流程</CardTitle>
      </CardHeader>
      <CardContent>
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="flex h-auto flex-wrap justify-start">
            <TabsTrigger value="comments">评论</TabsTrigger>
            <TabsTrigger value="attachments">附件</TabsTrigger>
            <TabsTrigger value="relations">关联对象</TabsTrigger>
            <TabsTrigger value="ai">AI 总结</TabsTrigger>
            <TabsTrigger value="sla">SLA</TabsTrigger>
          </TabsList>

          <TabsContent value="comments">
            {comments.isLoading ? (
              <TableLoadingState columns={1} />
            ) : comments.error ? (
              <ErrorState error={comments.error} onRetry={() => comments.refetch()} />
            ) : (
              <CommentTimeline recordId={recordId} comments={comments.data ?? []} />
            )}
          </TabsContent>

          <TabsContent value="attachments">
            <AttachmentPanel
              recordId={recordId}
              attachments={attachments.data ?? []}
              loading={attachments.isLoading}
            />
          </TabsContent>

          <TabsContent value="relations">
            <RecordRelationPanel
              recordId={recordId}
              relations={relations.data ?? []}
              loading={relations.isLoading}
            />
          </TabsContent>

          <TabsContent value="ai">
            <RecordAiPanel recordId={recordId} generations={ai.data ?? []} loading={ai.isLoading} />
          </TabsContent>

          <TabsContent value="sla">
            <RecordSlaPanel items={sla.data ?? []} loading={sla.isLoading} />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
```

在现有 `RecordReadonlyView` 的 `RecordHistoryCard` 前插入：

```tsx
<RecordExtensionPanel recordId={record.id} />
```

### 15.5 record-comment-timeline.tsx

```tsx
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/feedback/async-state'
import { toast } from '@/components/ui/use-toast'
import { createComment, deleteComment } from './extension-api'
import type { WorkRecordComment } from './extension-types'

export function CommentTimeline({
  recordId,
  comments,
}: {
  recordId: string
  comments: WorkRecordComment[]
}) {
  const [content, setContent] = useState('')
  const queryClient = useQueryClient()
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['work-record-comments', recordId] })

  const create = useMutation({
    mutationFn: () => createComment(recordId, { content: content.trim(), mentionUserIds: [] }),
    onSuccess: async () => {
      setContent('')
      await refresh()
      toast({ title: '评论已发布' })
    },
  })

  const remove = useMutation({
    mutationFn: (comment: WorkRecordComment) =>
      deleteComment(recordId, comment.id, comment.rowVersion),
    onSuccess: refresh,
  })

  return (
    <div className="grid gap-4">
      <div className="grid gap-2">
        <Textarea
          value={content}
          maxLength={4000}
          placeholder="输入评论，可在后续版本接入 @用户选择器"
          onChange={(event) => setContent(event.target.value)}
        />
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{content.length}/4000</span>
          <Button disabled={!content.trim() || create.isPending} onClick={() => create.mutate()}>
            发布评论
          </Button>
        </div>
      </div>

      {comments.length === 0 ? (
        <EmptyState title="暂无评论" description="发布第一条协作评论。" />
      ) : (
        <ol className="grid gap-3">
          {comments.map((comment) => (
            <li key={comment.id} className="rounded-md border p-3">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium">{comment.createdBy}</span>
                <time className="text-xs text-muted-foreground">
                  {new Date(comment.createdAt).toLocaleString()}
                </time>
              </div>
              <p className="mt-2 whitespace-pre-wrap break-words text-sm">{comment.content}</p>
              <Button
                variant="ghost"
                size="sm"
                className="mt-2"
                onClick={() => remove.mutate(comment)}
              >
                删除
              </Button>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
```

删除按钮最终必须套 `PermissionGate` 并结合当前用户是否是评论作者；后端仍是最终授权边界。

### 15.6 record-attachment-panel.tsx

```tsx
import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { EmptyState, TableLoadingState } from '@/components/feedback/async-state'
import { attachmentDownloadUrl, uploadAttachment } from './extension-api'
import type { WorkRecordAttachment } from './extension-types'

export function AttachmentPanel({
  recordId,
  attachments,
  loading,
}: {
  recordId: string
  attachments: WorkRecordAttachment[]
  loading: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()
  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(recordId, file),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['work-record-attachments', recordId],
      }),
  })

  if (loading) return <TableLoadingState columns={3} />

  return (
    <div className="grid gap-3">
      <div>
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) upload.mutate(file)
            event.target.value = ''
          }}
        />
        <Button onClick={() => inputRef.current?.click()} disabled={upload.isPending}>
          上传附件
        </Button>
        <span className="ml-3 text-xs text-muted-foreground">单文件最大 20 MiB</span>
      </div>

      {attachments.length === 0 ? (
        <EmptyState title="暂无附件" description="上传记录相关文档或截图。" />
      ) : (
        <ul className="divide-y rounded-md border">
          {attachments.map((item) => (
            <li key={item.id} className="flex items-center justify-between gap-4 p-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{item.fileName}</p>
                <p className="text-xs text-muted-foreground">
                  {(item.sizeBytes / 1024).toFixed(1)} KiB · {item.contentType}
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={async () => {
                  const download = await attachmentDownloadUrl(item.id)
                  window.location.assign(download.url)
                }}
              >
                下载
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
```

### 15.7 ImportDialog.tsx

```tsx
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { submitImport } from './extension-api'

export function ImportDialog({
  open,
  templateId,
  templateVersionId,
  onOpenChange,
  onCreated,
}: {
  open: boolean
  templateId: string
  templateVersionId: string
  onOpenChange: (open: boolean) => void
  onCreated: (jobId: string) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const mutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('请选择 Excel 文件')
      return submitImport({
        file,
        templateId,
        templateVersionId,
        defaultStatus: 'draft',
        stopOnError: false,
      })
    },
    onSuccess: ({ jobId }) => {
      setFile(null)
      onOpenChange(false)
      onCreated(jobId)
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excel 导入工作记录</DialogTitle>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="work-record-import-file">Excel 文件</Label>
          <Input
            id="work-record-import-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          <p className="text-xs text-muted-foreground">
            第一行为字段编码；必须包含 title 和 recordTime。
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button disabled={!file || mutation.isPending} onClick={() => mutation.mutate()}>
            创建导入任务
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

### 15.8 JobCenterPage.tsx

```tsx
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, TableLoadingState } from '@/components/feedback/async-state'
import { ResponsiveTable } from '@/components/layout/responsive-table'
import { asyncJobDownloadUrl, listAsyncJobs } from './extension-api'

export function JobCenterPage() {
  const jobs = useQuery({
    queryKey: ['work-record-jobs'],
    queryFn: listAsyncJobs,
    refetchInterval: (query) =>
      query.state.data?.some((item) => ['QUEUED', 'RUNNING'].includes(item.status)) ? 3000 : false,
  })

  return (
    <main className="grid gap-4 p-4 md:p-6">
      <header>
        <h1 className="text-2xl font-semibold">任务中心</h1>
        <p className="text-sm text-muted-foreground">查看导入、导出和 AI 生成进度。</p>
      </header>
      <Card>
        <CardHeader>
          <CardTitle>异步任务</CardTitle>
        </CardHeader>
        <CardContent>
          {jobs.isLoading ? (
            <TableLoadingState columns={6} />
          ) : jobs.error ? (
            <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} />
          ) : (jobs.data?.length ?? 0) === 0 ? (
            <EmptyState title="暂无任务" description="导入或异步导出后会显示在这里。" />
          ) : (
            <ResponsiveTable>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left">
                    <th className="p-2">类型</th>
                    <th className="p-2">状态</th>
                    <th className="p-2">进度</th>
                    <th className="p-2">成功</th>
                    <th className="p-2">失败</th>
                    <th className="p-2">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.data?.map((job) => (
                    <tr key={job.id} className="border-b">
                      <td className="p-2">{job.jobType}</td>
                      <td className="p-2">{job.status}</td>
                      <td className="p-2">
                        {job.processedCount}/{job.totalCount || '-'}
                      </td>
                      <td className="p-2">{job.successCount}</td>
                      <td className="p-2">{job.failureCount}</td>
                      <td className="p-2">
                        {['SUCCEEDED', 'PARTIAL_SUCCESS'].includes(job.status) &&
                        job.resultFileName ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={async () => {
                              const result = await asyncJobDownloadUrl(job.id)
                              window.location.assign(result.url)
                            }}
                          >
                            下载
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </ResponsiveTable>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
```

### 15.9 AnalyticsPage.tsx

```tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ErrorState, PageLoadingState } from '@/components/feedback/async-state'
import { statistics } from './extension-api'

export function AnalyticsPage() {
  const [from, setFrom] = useState(() => new Date().toISOString().slice(0, 7) + '-01')
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10))
  const search = useMemo(() => {
    const value = new URLSearchParams()
    value.set('from', `${from}T00:00:00Z`)
    value.set('to', `${to}T23:59:59Z`)
    value.set('groupBy', 'day')
    return value
  }, [from, to])
  const query = useQuery({
    queryKey: ['work-record-statistics', search.toString()],
    queryFn: () => statistics(search),
  })

  if (query.isLoading) return <PageLoadingState />
  if (query.error) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  }

  return (
    <main className="grid gap-4 p-4 md:p-6">
      <header>
        <h1 className="text-2xl font-semibold">工作记录统计</h1>
      </header>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Metric title="记录数" value={query.data?.totalRecords ?? 0} />
        <Metric title="已完成" value={query.data?.completedRecords ?? 0} />
        <Metric title="负责人数量" value={query.data?.distinctOwners ?? 0} />
      </div>
      <Card>
        <CardHeader>
          <CardTitle>每日记录数量</CardTitle>
        </CardHeader>
        <CardContent className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={query.data?.series ?? []}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    </main>
  )
}

function Metric({ title, value }: { title: string; value: number }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent className="text-2xl font-semibold">{value}</CardContent>
    </Card>
  )
}
```

遵守当前前端测试/图表约束时，不在组件中写死颜色；由现有 CSS theme 控制。

### 15.10 路由

```tsx
// src/routes/_authenticated/work-records/jobs.tsx
import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/features/auth/permission'
import { JobCenterPage } from '@/features/work-records/extension/job-center-page'

export const Route = createFileRoute('/_authenticated/work-records/jobs')({
  beforeLoad: () => requireAnyPermission(['work-record:import', 'work-record:export:async']),
  component: JobCenterPage,
})
```

```tsx
// src/routes/_authenticated/work-records/analytics.tsx
import { createFileRoute } from '@tanstack/react-router'
import { requirePermission } from '@/features/auth/permission'
import { AnalyticsPage } from '@/features/work-records/extension/analytics-page'

export const Route = createFileRoute('/_authenticated/work-records/analytics')({
  beforeLoad: () => requirePermission('work-record:analytics'),
  component: AnalyticsPage,
})
```

其他页面同样以权限守卫注册：

```text
/work-records/handovers  -> work-record:handover
/work-records/market     -> work-record:market:install 或 publish
/work-records/approvals  -> work-record:approval:act
```

### 15.11 前端测试

#### record-comment-timeline.test.tsx

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CommentTimeline } from './record-comment-timeline'

vi.mock('./extension-api', () => ({
  createComment: vi.fn().mockResolvedValue({}),
  deleteComment: vi.fn().mockResolvedValue(undefined),
}))

describe('CommentTimeline', () => {
  it('renders empty state and disables blank submit', async () => {
    const client = new QueryClient()
    const screen = await render(
      <QueryClientProvider client={client}>
        <CommentTimeline recordId="r1" comments={[]} />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('暂无评论')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: '发布评论' })).toBeDisabled()
  })

  it('renders existing comments', async () => {
    const client = new QueryClient()
    const screen = await render(
      <QueryClientProvider client={client}>
        <CommentTimeline
          recordId="r1"
          comments={[
            {
              id: 'c1',
              tenantId: 't1',
              recordId: 'r1',
              content: '已完成数据库升级',
              mentionUserIds: [],
              createdBy: 'u1',
              createdAt: '2026-07-11T10:00:00Z',
              updatedAt: '2026-07-11T10:00:00Z',
              rowVersion: 1,
            },
          ]}
        />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('已完成数据库升级')).toBeVisible()
  })
})
```

#### job-center-page.test.tsx

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { JobCenterPage } from './job-center-page'

vi.mock('./extension-api', () => ({
  listAsyncJobs: vi.fn().mockResolvedValue([
    {
      id: 'j1',
      jobType: 'RECORD_EXPORT',
      status: 'RUNNING',
      requestedBy: 'u1',
      totalCount: 100,
      processedCount: 40,
      successCount: 40,
      failureCount: 0,
      resultFileName: null,
      errorMessage: null,
      createdAt: '2026-07-11T10:00:00Z',
      startedAt: '2026-07-11T10:00:01Z',
      finishedAt: null,
    },
  ]),
}))

describe('JobCenterPage', () => {
  it('shows async job progress', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const screen = await render(
      <QueryClientProvider client={client}>
        <JobCenterPage />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('RECORD_EXPORT')).toBeVisible()
    await expect.element(screen.getByText('40/100')).toBeVisible()
  })
})
```

#### import-dialog.test.tsx

```tsx
it('only accepts xlsx and creates import job', async () => {
  const screen = await render(
    <ImportDialog
      open
      templateId="tpl1"
      templateVersionId="v1"
      onOpenChange={() => undefined}
      onCreated={() => undefined}
    />
  )

  const input = screen.getByLabelText('Excel 文件')
  await input.upload(
    new File(['xlsx'], 'records.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
  )
  await expect.element(screen.getByRole('button', { name: '创建导入任务' })).toBeEnabled()
})
```

---

## JDBC 实现统一要求

### 16.11 JDBC 实现统一要求

上述 Repository 的 JDBC 实现遵守同一规则：

```text
1. 每个查询都有 tenant_id 条件。
2. 修改状态时带 expected status/row_version。
3. 多 Worker 抢占使用 FOR UPDATE SKIP LOCKED。
4. JSON 通过 ObjectMapper 序列化，不手拼用户内容。
5. 所有列表有固定上限和稳定 id 次级排序。
6. 删除采用软删除或明确状态迁移。
7. Repository 不做权限判断，权限在 Application Service 完成。
```

---

## 17. 测试体系

### 17.1 后端单元测试清单

```text
OutboxWriterTest
  延迟时间写入
  idempotencyKey 冲突复用
  payload 序列化失败回滚

JooqOutboxRepositoryTest
  claim 只选择 available_at<=now
  processing 租约超时可回收
  多线程 claim 不重复
  failure 指数退避

ExcelImportParserTest
  动态字段类型
  必填 title/recordTime
  未知列
  最大行数/列数
  boolean/date/datetime/multi_select

WorkRecordImportProcessorTest
  逐行领域校验
  stopOnError
  partial_success
  字典非法值
  历史模板版本

WorkRecordAsyncExportProcessorTest
  权限重新加载
  字段级权限
  大结果流式写 MinIO
  文件超限清理

CommentServiceTest
  记录不可见时拒绝
  仅作者或管理员删除
  mention 不能跨租户
  乐观锁

AttachmentServiceTest
  类型/大小限制
  数据库失败清理对象
  下载权限
  删除权限

RecordRelationServiceTest
  target 租户隔离
  alert/inspection/incident 权限
  重复关系冲突

StatisticsServiceTest
  statistical 白名单
  时间范围上限
  字段权限
  工作日数量

MissingDailyReminderServiceTest
  节假日跳过
  调休工作日提醒
  已填写不提醒
  dedupe

HandoverServiceTest
  状态机
  接收人权限
  记录可见性
  乐观锁

AiGenerationServiceTest
  inputHash 复用
  敏感字段裁剪
  人工接受/拒绝
  Agent 失败

TemplateMarketServiceTest
  checksum
  可见性
  字典依赖安装
  安装后模板独立

FieldPolicyServiceTest
  readRoles/writeRoles
  full/partial mask
  filter/export/AI 接入

ApprovalServiceTest
  any/all
  user/role/owner 审批人
  双击并发
  拒绝回写记录

SlaLifecycleExtensionTest
  start/stop event
  工作日历截止时间
  breach 幂等
```

### 17.2 PostgreSQL Testcontainers 集成测试

新增：

```text
Phase20MigrationPostgresIT
Phase20OutboxConcurrencyPostgresIT
WorkRecordImportPostgresIT
AsyncExportPostgresIT
CommentAttachmentRelationPostgresIT
StatisticsPostgresIT
ReminderDedupePostgresIT
ApprovalConcurrencyPostgresIT
SlaClaimPostgresIT
TemplateMarketInstallPostgresIT
FieldPolicyQueryPostgresIT
```

#### Phase20OutboxConcurrencyPostgresIT 核心测试

```java
@Test
void concurrentWorkersCannotClaimSameRows() throws Exception {
  insertPendingRows(100);
  ExecutorService executor = Executors.newFixedThreadPool(4);
  try {
    List<Future<List<String>>> futures =
        java.util.stream.IntStream.range(0, 4)
            .mapToObj(index ->
                executor.submit(() ->
                    repository.claimNextPending("worker", 25, Duration.ofMinutes(2))
                        .stream()
                        .map(AutomationOutboxRecord::getId)
                        .toList()))
            .toList();

    List<String> all = new ArrayList<>();
    for (Future<List<String>> future : futures) {
      all.addAll(future.get());
    }

    assertThat(all).hasSize(100);
    assertThat(new HashSet<>(all)).hasSize(100);
  } finally {
    executor.shutdownNow();
  }
}
```

#### FieldPolicyQueryPostgresIT 核心测试

```java
@Test
void forbiddenDynamicFieldCannotBeUsedAsSideChannelFilter() {
  var normalUser = TestPrincipals.normalUser();

  assertThatThrownBy(() ->
      queryService.page(
          "t1",
          TestQueries.filter("salary", "gte", 10000),
          normalUser))
      .isInstanceOf(AccessDeniedException.class);

  verifyNoInteractions(recordRepository);
}
```

### 17.3 ArchUnit

```java
package io.aegisops.workrecord.extension.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.aegisops.workrecord.extension",
    importOptions = ImportOption.DoNotIncludeTests.class)
class WorkRecordExtensionArchitectureTest {

  @ArchTest
  static final ArchRule domainIsIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "..application..",
              "..api..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..infrastructure..");

  @ArchTest
  static final ArchRule apiDoesNotUseJdbc =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.jdbc..", "java.sql..");
}
```

### 17.4 前端测试清单

```text
record-comment-timeline.test.tsx
record-attachment-panel.test.tsx
record-relation-panel.test.tsx
record-ai-panel.test.tsx
record-approval-panel.test.tsx
record-sla-panel.test.tsx
import-dialog.test.tsx
job-center-page.test.tsx
analytics-page.test.tsx
handover-page.test.tsx
template-market-page.test.tsx
field-policy-editor.test.tsx
```

必须验证：

```text
loading/empty/error
移动端卡片模式
权限按钮隐藏
导入文件限制
异步任务轮询停止
AI 结果必须审核
字段隐藏后 DOM 中不存在原值
审批并发错误提示
SLA breached 状态
```

### 17.5 E2E 企业场景

```text
1. 管理员发布带字段权限、审批和 SLA 的模板。
2. 普通用户通过 Excel 导入 3 条记录。
3. 1 条成功、1 条字典非法、1 条缺失必填，任务 partial_success。
4. 普通用户只能看自己的任务。
5. 记录添加评论、附件、告警、巡检、事件关联。
6. 受限字段普通用户看不到，管理员可见脱敏值。
7. 普通用户提交完成，记录进入 pending_approval。
8. 审批人通过后记录变为 done。
9. SLA 在截止前完成显示 met。
10. 另一条记录超时后显示 breached，并产生唯一通知。
11. 管理员查看统计与工作量。
12. 生成 AI 月报草稿，管理员接受。
13. 模板发布到市场，另一租户安装后得到独立模板。
14. 异步导出完成，通过 5 分钟预签名 URL 下载。
15. 节假日不发缺失提醒，调休工作日正常提醒。
16. 值班人员创建并完成交接。
17. 审计日志覆盖导入、评论、附件、关系、AI、审批、SLA、市场安装。
```

---

## 18. 实施顺序与提交边界

Phase 20 不应一次提交 17 项能力。推荐：

```text
20.0 phase20/outbox-foundation
  V0028
  Outbox availableAt/lease/idempotency
  AsyncJob/MinIO

20.1 phase20/import-export
  Excel 导入
  异步导出
  任务中心

20.2 phase20/collaboration
  评论
  附件
  关联告警/巡检/事件

20.4 phase20/analytics-reminder-handover
  统计报表
  工作量分析
  日报缺失提醒
  值班交接

20.5 phase20/ai-generation
  AI 自动总结
  AI 月报
  人工审核

20.6 phase20/market-field-policy
  Schema v2
  模板市场
  字段级权限

20.7 phase20/approval-sla
  审批流
  SLA

20.8 phase20-enterprise-e2e
  Portal 收口
  权限回归
  全链路 E2E
  性能和生产配置
```

每个子阶段都必须独立通过 Maven、Portal 和数据库迁移验证，禁止多个迁移阶段共享一个不可回滚的大提交。

---

## 19. 验收命令

```bash
mvn -B -ntp \
  -pl modules/aiops-common,modules/aiops-work-record,modules/aiops-work-record-extension,modules/aiops-security,modules/aiops-ai-client,apps/aiops-worker,apps/aiops-server \
  -am \
  verify
```

```bash
pnpm -C web/portal run typecheck
pnpm -C web/portal run lint
pnpm -C web/portal run test
pnpm -C web/portal run build
```

```bash
cd apps/aiops-agent
pytest
```

```bash
mvn -B -ntp \
  -pl modules/aiops-work-record-extension,apps/aiops-server \
  -am \
  -DskipITs=false \
  verify
```

生产前还必须执行：

```text
Phase 19 修复版通过
V0028~V0035 在空库和历史库均迁移成功
MinIO bucket 策略与生命周期配置完成
Worker 多实例 outbox 并发测试通过
字段权限无法通过筛选/导出/AI 侧信道绕过
AI 输出审核流程通过
附件恶意类型和超限测试通过
审批和 SLA 并发测试通过
```

---

## 20. 最终结论

Phase 20 最适合继续保持当前模块化单体：

```text
Server：HTTP、权限、领域事务、任务派发
Worker：异步导入/导出、提醒、AI、SLA 扫描
Agent：纯生成能力，不直接访问业务数据库
PostgreSQL：业务事实、任务状态、审批、SLA
MinIO：附件、导入源文件、导出结果
Portal：详情扩展、任务中心、报表、市场和审批页面
```

不需要因为 Phase 20 引入 Kafka、独立审批微服务、独立报表微服务或微前端。当前 `automation_outbox + worker + MinIO + 模块化单体` 足以支撑第一版企业增强能力；当异步任务达到高吞吐、多团队独立发布或跨区域部署时，再评估消息队列和服务拆分。

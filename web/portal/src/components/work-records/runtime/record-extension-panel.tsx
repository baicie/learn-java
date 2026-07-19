import { useState, type ChangeEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Clock3,
  Download,
  Link2,
  MessageSquare,
  Paperclip,
  Sparkles,
} from 'lucide-react'
import {
  createComment,
  createRelation,
  downloadAttachment,
  listAttachments,
  listComments,
  listRelations,
  listRecordSla,
  listRecordAiGenerations,
  requestRecordAiSummary,
  reviewAiGeneration,
  uploadAttachment,
} from '@/api/work-records/extensions'
import { useAuthStore } from '@/stores/auth-store'
import { formatDateTime } from '@/lib/date-format'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { ErrorState } from '@/components/feedback/async-state'
import { PermissionGate } from '@/components/permission-gate'

export function RecordExtensionPanel({ recordId }: { recordId: string }) {
  const client = useQueryClient()
  const principal = useAuthStore((state) => state.auth.principal)
  const permissions = principal?.permissions ?? []
  const canRead = permissions.some((permission) =>
    ['work-record:read:self', 'work-record:read:all'].includes(permission)
  )
  const [comment, setComment] = useState('')
  const [relationType, setRelationType] = useState<
    'alert' | 'inspection' | 'incident'
  >('incident')
  const [targetId, setTargetId] = useState('')
  const comments = useQuery({
    queryKey: ['work-record-comments', recordId],
    queryFn: () => listComments(recordId),
    enabled: canRead,
  })
  const attachments = useQuery({
    queryKey: ['work-record-attachments', recordId],
    queryFn: () => listAttachments(recordId),
    enabled: canRead,
  })
  const relations = useQuery({
    queryKey: ['work-record-relations', recordId],
    queryFn: () => listRelations(recordId),
    enabled: canRead,
  })
  const generations = useQuery({
    queryKey: ['work-record-ai-generations', recordId],
    queryFn: () => listRecordAiGenerations(recordId),
    enabled: permissions.includes('work-record:ai:generate'),
  })
  const sla = useQuery({
    queryKey: ['work-record-sla', recordId],
    queryFn: () => listRecordSla(recordId),
    enabled: canRead,
  })
  const addComment = useMutation({
    mutationFn: () => createComment(recordId, comment.trim()),
    onSuccess: async () => {
      setComment('')
      await client.invalidateQueries({
        queryKey: ['work-record-comments', recordId],
      })
    },
  })
  const addRelation = useMutation({
    mutationFn: () => createRelation(recordId, relationType, targetId.trim()),
    onSuccess: async () => {
      setTargetId('')
      await client.invalidateQueries({
        queryKey: ['work-record-relations', recordId],
      })
    },
  })
  const addAttachment = useMutation({
    mutationFn: (file: File) => uploadAttachment(recordId, file),
    onSuccess: async () => {
      await client.invalidateQueries({
        queryKey: ['work-record-attachments', recordId],
      })
    },
  })
  const generateSummary = useMutation({
    mutationFn: () => requestRecordAiSummary(recordId),
    onSuccess: async () => {
      await client.invalidateQueries({
        queryKey: ['work-record-ai-generations', recordId],
      })
    },
  })
  const review = useMutation({
    mutationFn: ({ id, accepted }: { id: string; accepted: boolean }) =>
      reviewAiGeneration(id, accepted),
    onSuccess: async () => {
      await client.invalidateQueries({
        queryKey: ['work-record-ai-generations', recordId],
      })
    },
  })

  const chooseFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) addAttachment.mutate(file)
    event.target.value = ''
  }

  return (
    <Card className='mx-4 mb-6 md:mx-6'>
      <CardHeader>
        <CardTitle>协作与关联</CardTitle>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue='comments'>
          <TabsList className='grid w-full grid-cols-5 md:w-fit'>
            <TabsTrigger value='comments'>
              <MessageSquare aria-hidden='true' />
              评论
            </TabsTrigger>
            <TabsTrigger value='attachments'>
              <Paperclip aria-hidden='true' />
              附件
            </TabsTrigger>
            <TabsTrigger value='relations'>
              <Link2 aria-hidden='true' />
              关联
            </TabsTrigger>
            <TabsTrigger value='ai'>
              <Sparkles aria-hidden='true' />
              AI 总结
            </TabsTrigger>
            <TabsTrigger value='sla'>
              <Clock3 aria-hidden='true' />
              SLA
            </TabsTrigger>
          </TabsList>
          <TabsContent value='comments' className='mt-4 space-y-4'>
            {comments.error && <ErrorState error={comments.error} />}
            <div className='space-y-2'>
              {comments.data?.map((item) => (
                <div key={item.id} className='rounded-md border p-3'>
                  <p className='text-sm whitespace-pre-wrap'>{item.content}</p>
                  <p className='mt-2 text-xs text-muted-foreground'>
                    {item.createdBy} · {formatDateTime(item.createdAt)}
                  </p>
                </div>
              ))}
              {comments.data?.length === 0 && (
                <p className='text-sm text-muted-foreground'>暂无评论</p>
              )}
            </div>
            <PermissionGate any={['work-record:comment']}>
              <div className='space-y-2'>
                <Label htmlFor='record-comment'>新增评论</Label>
                <Textarea
                  id='record-comment'
                  value={comment}
                  maxLength={4000}
                  onChange={(event) => setComment(event.target.value)}
                />
                <Button
                  disabled={!comment.trim() || addComment.isPending}
                  onClick={() => addComment.mutate()}
                >
                  {addComment.isPending ? '提交中…' : '提交评论'}
                </Button>
              </div>
            </PermissionGate>
          </TabsContent>
          <TabsContent value='attachments' className='mt-4 space-y-4'>
            {attachments.error && <ErrorState error={attachments.error} />}
            <div className='space-y-2'>
              {attachments.data?.map((item) => (
                <div
                  key={item.id}
                  className='flex items-center justify-between gap-3 rounded-md border p-3'
                >
                  <div className='min-w-0'>
                    <p className='truncate text-sm font-medium'>
                      {item.fileName}
                    </p>
                    <p className='text-xs text-muted-foreground'>
                      {(item.sizeBytes / 1024).toFixed(1)} KB
                    </p>
                  </div>
                  {item.status === 'ready' ? (
                    <Button
                      size='sm'
                      variant='outline'
                      onClick={async () => {
                        const value = await downloadAttachment(
                          recordId,
                          item.id
                        )
                        window.open(value.url, '_blank', 'noopener,noreferrer')
                      }}
                    >
                      <Download aria-hidden='true' />
                      下载
                    </Button>
                  ) : (
                    <Badge variant='secondary'>{item.status}</Badge>
                  )}
                </div>
              ))}
            </div>
            <PermissionGate any={['work-record:attachment']}>
              <div>
                <Label
                  htmlFor='record-attachment'
                  className='mb-2 inline-block'
                >
                  上传附件（最大 20 MiB）
                </Label>
                <Input
                  id='record-attachment'
                  type='file'
                  disabled={addAttachment.isPending}
                  onChange={chooseFile}
                />
              </div>
            </PermissionGate>
          </TabsContent>
          <TabsContent value='relations' className='mt-4 space-y-4'>
            {relations.error && <ErrorState error={relations.error} />}
            <div className='space-y-2'>
              {relations.data?.map((item) => (
                <div key={item.id} className='rounded-md border p-3'>
                  <div className='flex items-center gap-2'>
                    <Badge variant='outline'>{item.relationType}</Badge>
                    <span className='truncate text-sm font-medium'>
                      {item.targetTitle || item.targetId}
                    </span>
                  </div>
                  {item.targetStatus && (
                    <p className='mt-1 text-xs text-muted-foreground'>
                      状态：{item.targetStatus}
                    </p>
                  )}
                </div>
              ))}
            </div>
            <PermissionGate any={['work-record:relation']}>
              <div className='grid gap-3 md:grid-cols-[10rem_minmax(0,1fr)_auto] md:items-end'>
                <div className='space-y-2'>
                  <Label htmlFor='relation-type'>类型</Label>
                  <Select
                    value={relationType}
                    onValueChange={(value) =>
                      setRelationType(value as typeof relationType)
                    }
                  >
                    <SelectTrigger id='relation-type'>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value='alert'>告警</SelectItem>
                      <SelectItem value='inspection'>巡检</SelectItem>
                      <SelectItem value='incident'>事件</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className='space-y-2'>
                  <Label htmlFor='relation-target'>目标 ID</Label>
                  <Input
                    id='relation-target'
                    value={targetId}
                    onChange={(event) => setTargetId(event.target.value)}
                  />
                </div>
                <Button
                  disabled={!targetId.trim() || addRelation.isPending}
                  onClick={() => addRelation.mutate()}
                >
                  添加关联
                </Button>
              </div>
            </PermissionGate>
          </TabsContent>
          <TabsContent value='ai' className='mt-4 space-y-4'>
            {generations.error && <ErrorState error={generations.error} />}
            <PermissionGate any={['work-record:ai:generate']}>
              <Button
                disabled={generateSummary.isPending}
                onClick={() => generateSummary.mutate()}
              >
                <Sparkles aria-hidden='true' />
                {generateSummary.isPending ? '正在创建任务…' : '生成最新总结'}
              </Button>
            </PermissionGate>
            {generations.data?.map((item) => (
              <div key={item.id} className='space-y-3 rounded-md border p-4'>
                <div className='flex items-center justify-between gap-3'>
                  <span className='text-sm font-medium'>AI 工作记录总结</span>
                  <Badge variant='secondary'>{item.status}</Badge>
                </div>
                {item.outputMarkdown && (
                  <pre className='font-sans text-sm leading-6 whitespace-pre-wrap'>
                    {item.outputMarkdown}
                  </pre>
                )}
                {item.status === 'success' && (
                  <PermissionGate any={['work-record:ai:review']}>
                    <div className='flex gap-2'>
                      <Button
                        size='sm'
                        disabled={review.isPending}
                        onClick={() =>
                          review.mutate({ id: item.id, accepted: true })
                        }
                      >
                        采纳
                      </Button>
                      <Button
                        size='sm'
                        variant='outline'
                        disabled={review.isPending}
                        onClick={() =>
                          review.mutate({ id: item.id, accepted: false })
                        }
                      >
                        拒绝
                      </Button>
                    </div>
                  </PermissionGate>
                )}
              </div>
            ))}
          </TabsContent>
          <TabsContent value='sla' className='mt-4 space-y-3'>
            {sla.error && <ErrorState error={sla.error} />}
            {sla.data?.map((item) => (
              <div key={item.id} className='rounded-md border p-4'>
                <div className='flex items-center justify-between gap-3'>
                  <span className='text-sm font-medium'>{item.policyName}</span>
                  <div className='flex gap-2'>
                    <Badge variant='outline'>{item.severity}</Badge>
                    <Badge variant='secondary'>{item.status}</Badge>
                  </div>
                </div>
                <p className='mt-2 text-xs text-muted-foreground'>
                  {formatDateTime(item.startedAt)} →{' '}
                  {formatDateTime(item.dueAt)}
                </p>
              </div>
            ))}
            {sla.data?.length === 0 && (
              <p className='text-sm text-muted-foreground'>
                当前记录没有 SLA 实例
              </p>
            )}
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}

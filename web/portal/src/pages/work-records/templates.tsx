import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PermissionGate } from '@/auth/permission-gate'
import { Copy, Edit3, FileClock, Plus, Settings2 } from 'lucide-react'
import {
  archiveTemplate,
  copyTemplate,
  createTemplate,
  disableTemplate,
  enableTemplate,
  listTemplates,
  listTemplateVersionFields,
  listTemplateVersions,
  updateTemplate,
} from '@/api/work-records/templates'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ErrorState, PageLoadingState } from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import type { WorkRecordTemplate } from '@/components/work-records/designer/types'

type EditorMode = 'create' | 'edit' | 'copy'

export function WorkRecordTemplatesPage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const templates = useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })
  const [editor, setEditor] = useState<{
    mode: EditorMode
    template?: WorkRecordTemplate
  } | null>(null)
  const [form, setForm] = useState({ code: '', name: '', description: '' })
  const [versionTemplate, setVersionTemplate] =
    useState<WorkRecordTemplate | null>(null)
  const [versionId, setVersionId] = useState('')

  const versions = useQuery({
    queryKey: ['work-record-template-versions', versionTemplate?.id],
    queryFn: () => listTemplateVersions(versionTemplate!.id),
    enabled: Boolean(versionTemplate),
  })
  const selectedVersionId =
    versions.data?.some((version) => version.id === versionId) === true
      ? versionId
      : (versions.data?.[0]?.id ?? '')
  const versionFields = useQuery({
    queryKey: [
      'work-record-template-version-fields',
      versionTemplate?.id,
      selectedVersionId,
    ],
    queryFn: () =>
      listTemplateVersionFields(versionTemplate!.id, selectedVersionId),
    enabled: Boolean(versionTemplate && selectedVersionId),
  })

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['work-record-templates'] })

  const save = useMutation({
    mutationFn: async () => {
      if (editor?.mode === 'edit' && editor.template) {
        return updateTemplate(editor.template.id, {
          name: form.name,
          description: form.description || undefined,
        })
      }
      if (editor?.mode === 'copy' && editor.template) {
        return copyTemplate(editor.template.id, {
          targetCode: form.code,
          targetName: form.name,
          description: form.description || undefined,
        })
      }
      return createTemplate({
        code: form.code,
        name: form.name,
        description: form.description || undefined,
      })
    },
    onSuccess: async () => {
      setEditor(null)
      await refresh()
    },
  })

  const lifecycle = useMutation({
    mutationFn: ({
      id,
      action,
    }: {
      id: string
      action: 'enable' | 'disable' | 'archive'
    }) =>
      action === 'enable'
        ? enableTemplate(id)
        : action === 'disable'
          ? disableTemplate(id)
          : archiveTemplate(id),
    onSuccess: refresh,
  })

  const openEditor = (mode: EditorMode, template?: WorkRecordTemplate) => {
    setForm({
      code:
        mode === 'copy'
          ? `${template?.code ?? ''}-copy`
          : (template?.code ?? ''),
      name:
        mode === 'copy'
          ? `${template?.name ?? ''} 副本`
          : (template?.name ?? ''),
      description: template?.description ?? '',
    })
    setEditor({ mode, template })
  }

  const archive = async (template: WorkRecordTemplate) => {
    const accepted = await confirm({
      title: '归档模板',
      description: `归档“${template.name}”后不能再用于新记录。`,
      confirmText: '归档',
      variant: 'destructive',
    })
    if (accepted) lifecycle.mutate({ id: template.id, action: 'archive' })
  }

  if (templates.isLoading) return <PageLoadingState />
  if (templates.error) {
    return (
      <ErrorState
        error={templates.error}
        onRetry={() => void templates.refetch()}
      />
    )
  }

  return (
    <main className='grid gap-4 p-4 md:p-6'>
      <div className='flex flex-wrap items-center justify-between gap-3'>
        <div>
          <h1 className='text-2xl font-semibold'>模板管理</h1>
          <p className='text-sm text-muted-foreground'>
            管理工作记录模板、版本和独立设计器。
          </p>
        </div>
        <PermissionGate anyOf={['work-record:template:write']}>
          <Button onClick={() => openEditor('create')}>
            <Plus className='mr-2 size-4' />
            新建模板
          </Button>
        </PermissionGate>
      </div>

      <div className='overflow-hidden rounded-md border'>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>模板</TableHead>
              <TableHead>编码</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>当前版本</TableHead>
              <TableHead className='text-right'>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(templates.data ?? []).map((template) => (
              <TableRow key={template.id}>
                <TableCell className='font-medium'>{template.name}</TableCell>
                <TableCell>{template.code}</TableCell>
                <TableCell>
                  <Badge variant='outline'>{template.status}</Badge>
                </TableCell>
                <TableCell>{template.currentVersionId ?? '未发布'}</TableCell>
                <TableCell>
                  <div className='flex flex-wrap justify-end gap-1'>
                    <Button
                      variant='outline'
                      size='sm'
                      onClick={() => {
                        setVersionId('')
                        setVersionTemplate(template)
                      }}
                    >
                      <FileClock className='mr-1 size-4' />
                      版本
                    </Button>
                    <Button variant='outline' size='sm' asChild>
                      <Link
                        to='/work-records/templates/$templateId/designer'
                        params={{ templateId: template.id }}
                      >
                        <Settings2 className='mr-1 size-4' />
                        设计
                      </Link>
                    </Button>
                    <PermissionGate anyOf={['work-record:template:write']}>
                      <Button
                        variant='outline'
                        size='sm'
                        onClick={() => openEditor('edit', template)}
                      >
                        <Edit3 className='mr-1 size-4' />
                        编辑
                      </Button>
                      <Button
                        variant='outline'
                        size='sm'
                        onClick={() => openEditor('copy', template)}
                      >
                        <Copy className='mr-1 size-4' />
                        复制
                      </Button>
                      {template.status !== 'archived' ? (
                        <Button
                          variant='outline'
                          size='sm'
                          onClick={() =>
                            lifecycle.mutate({
                              id: template.id,
                              action: template.enabled ? 'disable' : 'enable',
                            })
                          }
                        >
                          {template.enabled ? '禁用' : '启用'}
                        </Button>
                      ) : null}
                      {template.status !== 'archived' ? (
                        <Button
                          variant='destructive'
                          size='sm'
                          onClick={() => void archive(template)}
                        >
                          归档
                        </Button>
                      ) : null}
                    </PermissionGate>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog
        open={Boolean(editor)}
        onOpenChange={(open) => !open && setEditor(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editor?.mode === 'create'
                ? '新建模板'
                : editor?.mode === 'copy'
                  ? '复制模板'
                  : '编辑模板'}
            </DialogTitle>
            <DialogDescription>
              模板编码创建后不可修改；字段结构在设计器中维护。
            </DialogDescription>
          </DialogHeader>
          <div className='grid gap-3'>
            <Input
              aria-label='模板编码'
              placeholder='模板编码'
              disabled={editor?.mode === 'edit'}
              value={form.code}
              onChange={(event) =>
                setForm({ ...form, code: event.target.value })
              }
            />
            <Input
              aria-label='模板名称'
              placeholder='模板名称'
              value={form.name}
              onChange={(event) =>
                setForm({ ...form, name: event.target.value })
              }
            />
            <Input
              aria-label='模板说明'
              placeholder='模板说明'
              value={form.description}
              onChange={(event) =>
                setForm({ ...form, description: event.target.value })
              }
            />
          </div>
          <DialogFooter>
            <Button variant='outline' onClick={() => setEditor(null)}>
              取消
            </Button>
            <Button
              disabled={
                !form.name.trim() ||
                (editor?.mode !== 'edit' && !form.code.trim()) ||
                save.isPending
              }
              onClick={() => save.mutate()}
            >
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(versionTemplate)}
        onOpenChange={(open) => !open && setVersionTemplate(null)}
      >
        <DialogContent className='max-w-3xl'>
          <DialogHeader>
            <DialogTitle>模板版本</DialogTitle>
            <DialogDescription>
              {versionTemplate?.name} 的发布版本及字段快照。
            </DialogDescription>
          </DialogHeader>
          <div className='flex flex-wrap gap-2'>
            {(versions.data ?? []).map((version) => (
              <Button
                key={version.id}
                size='sm'
                variant={
                  version.id === selectedVersionId ? 'default' : 'outline'
                }
                onClick={() => setVersionId(version.id)}
              >
                {version.versionName || `v${version.versionNo}`}
              </Button>
            ))}
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>字段</TableHead>
                <TableHead>编码</TableHead>
                <TableHead>类型</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(versionFields.data ?? []).map((field) => (
                <TableRow key={field.id}>
                  <TableCell>{field.fieldName}</TableCell>
                  <TableCell>{field.fieldCode}</TableCell>
                  <TableCell>{field.fieldType}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DialogContent>
      </Dialog>
    </main>
  )
}

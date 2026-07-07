import { useState } from 'react'

import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { Badge } from '../../components/ui/badge'
import { Button, buttonVariants } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/dialog'
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
  createTemplate,
  createTemplateField,
  deleteTemplateField,
  listTemplateFields,
  listTemplates,
  updateTemplateField,
} from '../../features/work-record/api'
import type { WorkRecordField, WorkRecordTemplate } from '../../features/work-record/types'

const FIELD_PALETTE = [
  { type: 'text', label: '单行文本' },
  { type: 'textarea', label: '多行文本' },
  { type: 'number', label: '数字' },
  { type: 'date', label: '日期' },
  { type: 'datetime', label: '日期时间' },
  { type: 'select', label: '单选' },
  { type: 'multi_select', label: '多选' },
  { type: 'switch', label: '开关' },
  { type: 'user', label: '人员' },
]

export function WorkRecordTemplateDesignerPage() {
  const navigate = useNavigate()
  const [selectedTemplate, setSelectedTemplate] = useState<WorkRecordTemplate | null>(null)
  const [expandedTemplateId, setExpandedTemplateId] = useState<string | null>(null)
  const [editingField, setEditingField] = useState<WorkRecordField | null>(null)
  const [addFieldForm, setAddFieldForm] = useState<Record<string, string>>({})

  const templatesQuery = useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })

  const fieldsQuery = useQuery({
    queryKey: ['work-record-template-fields', expandedTemplateId],
    queryFn: () =>
      expandedTemplateId ? listTemplateFields(expandedTemplateId) : Promise.resolve([]),
    enabled: !!expandedTemplateId,
  })

  const createTemplateMutation = useMutation({
    mutationFn: createTemplate,
    onSuccess: (created) => {
      templatesQuery.refetch()
      setSelectedTemplate(created)
      setExpandedTemplateId(created.id)
    },
  })

  const addFieldMutation = useMutation({
    mutationFn: ({
      templateId,
      payload,
    }: {
      templateId: string
      payload: { fieldCode: string; fieldName: string; fieldType: string }
    }) => createTemplateField(templateId, payload),
    onSuccess: () => {
      fieldsQuery.refetch()
      setAddFieldForm({})
    },
  })

  const updateFieldMutation = useMutation({
    mutationFn: ({
      templateId,
      fieldId,
      payload,
    }: {
      templateId: string
      fieldId: string
      payload: Record<string, unknown>
    }) => updateTemplateField(templateId, fieldId, payload),
    onSuccess: () => {
      fieldsQuery.refetch()
      setEditingField(null)
    },
  })

  const deleteFieldMutation = useMutation({
    mutationFn: ({ templateId, fieldId }: { templateId: string; fieldId: string }) =>
      deleteTemplateField(templateId, fieldId),
    onSuccess: () => fieldsQuery.refetch(),
  })

  const moveFieldMutation = useMutation({
    mutationFn: async ({
      templateId,
      fields,
      fromIndex,
      direction,
    }: {
      templateId: string
      fields: WorkRecordField[]
      fromIndex: number
      direction: 'up' | 'down'
    }) => {
      const toIndex = direction === 'up' ? fromIndex - 1 : fromIndex + 1
      if (toIndex < 0 || toIndex >= fields.length) return
      const reordered = [...fields]
      const [moved] = reordered.splice(fromIndex, 1)
      reordered.splice(toIndex, 0, moved)
      await Promise.all(
        reordered.map((f, idx) => updateTemplateField(templateId, f.id, { sortOrder: idx })),
      )
    },
    onSuccess: () => fieldsQuery.refetch(),
  })

  const fields = fieldsQuery.data ?? []

  return (
    <section className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">模板字段配置</h1>
          <p className="text-sm text-muted-foreground">
            为工作记录模板配置字段集合。支持字段增删改、上下移排序、属性编辑。
          </p>
        </div>
        <Button variant="outline" onClick={() => navigate('/app/work-records')}>
          返回列表
        </Button>
      </div>

      {/* 新增模板 */}
      <Card>
        <CardHeader>
          <CardTitle>新增模板</CardTitle>
          <CardDescription>先创建模板，再向其中加入字段。</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-3 md:grid-cols-[1fr_1fr_1fr_auto]"
            onSubmit={(e) => {
              e.preventDefault()
              const form = e.currentTarget as HTMLFormElement
              const data = new FormData(form)
              createTemplateMutation.mutate({
                name: String(data.get('name') ?? ''),
                code: String(data.get('code') ?? ''),
                description: String(data.get('description') ?? '') || undefined,
                enabled: true,
                schemaJson: '{}',
              })
              form.reset()
            }}
          >
            <div className="space-y-1">
              <Label htmlFor="tpl-name">名称</Label>
              <Input id="tpl-name" name="name" required placeholder="例如 每日运维记录" />
            </div>
            <div className="space-y-1">
              <Label htmlFor="tpl-code">编码</Label>
              <Input id="tpl-code" name="code" required placeholder="例如 daily-ops" />
            </div>
            <div className="space-y-1">
              <Label htmlFor="tpl-desc">描述</Label>
              <Input id="tpl-desc" name="description" placeholder="可选" />
            </div>
            <Button type="submit" disabled={createTemplateMutation.isPending} className="self-end">
              {createTemplateMutation.isPending ? '提交中…' : '新增模板'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* 主体：左侧模板列表 + 右侧字段展开 */}
      <div className="grid gap-4 md:grid-cols-[260px_1fr]">
        {/* 左侧：字段组件 + 模板列表 */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>字段组件</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="space-y-2">
                {FIELD_PALETTE.map((f) => (
                  <li
                    key={f.type}
                    className="flex items-center justify-between rounded-md border bg-card px-3 py-2 text-sm"
                  >
                    <span>{f.label}</span>
                    <Badge variant="outline">{f.type}</Badge>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>模板列表</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {templatesQuery.isLoading ? (
                <p className="p-4 text-sm text-muted-foreground">加载中…</p>
              ) : (templatesQuery.data ?? []).length === 0 ? (
                <p className="p-4 text-sm text-muted-foreground">暂无模板</p>
              ) : (
                <ul className="divide-y">
                  {(templatesQuery.data ?? []).map((tpl) => (
                    <li key={tpl.id}>
                      <button
                        type="button"
                        className={`w-full px-4 py-3 text-left text-sm transition-colors hover:bg-muted/50 ${
                          expandedTemplateId === tpl.id ? 'bg-muted font-medium' : ''
                        }`}
                        onClick={() => {
                          setExpandedTemplateId(expandedTemplateId === tpl.id ? null : tpl.id)
                          setSelectedTemplate(tpl)
                          setAddFieldForm({})
                        }}
                      >
                        <div className="flex items-center justify-between">
                          <span>{tpl.name}</span>
                          <Badge
                            variant={tpl.enabled ? 'secondary' : 'outline'}
                            className="text-xs"
                          >
                            {tpl.enabled ? '启用' : '停用'}
                          </Badge>
                        </div>
                        <div className="mt-0.5 font-mono text-xs text-muted-foreground">
                          {tpl.code}
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>

        {/* 右侧：选中模板的字段管理 */}
        <Card>
          <CardHeader>
            <CardTitle>
              {selectedTemplate ? `${selectedTemplate.name} — 字段管理` : '字段管理'}
            </CardTitle>
            <CardDescription>
              {selectedTemplate
                ? '点击展开模板行查看字段，支持上下移排序、编辑属性、删除。'
                : '请从左侧选择一个模板以管理其字段。'}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {!selectedTemplate ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                左侧选择模板后，此处显示字段列表。
              </p>
            ) : expandedTemplateId !== selectedTemplate.id ? (
              <div className="py-6 text-center">
                <Button
                  variant="outline"
                  onClick={() => setExpandedTemplateId(selectedTemplate.id)}
                >
                  展开字段列表
                </Button>
              </div>
            ) : fieldsQuery.isLoading ? (
              <p className="py-4 text-sm text-muted-foreground">加载字段中…</p>
            ) : (
              <>
                {/* 新增字段表单 */}
                <form
                  className="flex flex-wrap items-end gap-2 rounded-md border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    if (!addFieldForm.code || !addFieldForm.name) return
                    addFieldMutation.mutate({
                      templateId: selectedTemplate.id,
                      payload: {
                        fieldCode: addFieldForm.code,
                        fieldName: addFieldForm.name,
                        fieldType: addFieldForm.type ?? 'text',
                      },
                    })
                  }}
                >
                  <div className="space-y-1">
                    <Label className="text-xs">字段名称</Label>
                    <Input
                      className="h-8"
                      placeholder="如 巡检人"
                      value={addFieldForm.name ?? ''}
                      onChange={(e) => setAddFieldForm((p) => ({ ...p, name: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">字段编码</Label>
                    <Input
                      className="h-8 font-mono"
                      placeholder="如 inspector"
                      value={addFieldForm.code ?? ''}
                      onChange={(e) => setAddFieldForm((p) => ({ ...p, code: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">类型</Label>
                    <select
                      className="h-8 rounded-md border bg-background px-2 text-sm"
                      value={addFieldForm.type ?? 'text'}
                      onChange={(e) => setAddFieldForm((p) => ({ ...p, type: e.target.value }))}
                    >
                      {FIELD_PALETTE.map((f) => (
                        <option key={f.type} value={f.type}>
                          {f.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <Button type="submit" size="sm" disabled={addFieldMutation.isPending}>
                    {addFieldMutation.isPending ? '添加中…' : '添加字段'}
                  </Button>
                </form>

                {/* 字段列表 */}
                {fields.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted-foreground">
                    暂无字段，添加一个吧
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-16">排序</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>编码</TableHead>
                        <TableHead>类型</TableHead>
                        <TableHead>必填</TableHead>
                        <TableHead>启用</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {fields.map((field, index) => (
                        <TableRow key={field.id}>
                          <TableCell>
                            <div className="flex flex-col gap-0.5">
                              <button
                                type="button"
                                className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30"
                                disabled={index === 0}
                                onClick={() =>
                                  moveFieldMutation.mutate({
                                    templateId: selectedTemplate.id,
                                    fields,
                                    fromIndex: index,
                                    direction: 'up',
                                  })
                                }
                                title="上移"
                              >
                                ↑
                              </button>
                              <button
                                type="button"
                                className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30"
                                disabled={index === fields.length - 1}
                                onClick={() =>
                                  moveFieldMutation.mutate({
                                    templateId: selectedTemplate.id,
                                    fields,
                                    fromIndex: index,
                                    direction: 'down',
                                  })
                                }
                                title="下移"
                              >
                                ↓
                              </button>
                            </div>
                          </TableCell>
                          <TableCell className="font-medium">{field.fieldName}</TableCell>
                          <TableCell className="font-mono text-xs text-muted-foreground">
                            {field.fieldCode}
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className="text-xs">
                              {field.fieldType}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={field.required ? 'default' : 'outline'}
                              className="text-xs"
                            >
                              {field.required ? '是' : '否'}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={field.enabled ? 'secondary' : 'destructive'}
                              className="text-xs"
                            >
                              {field.enabled ? '启用' : '停用'}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-1">
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-7 px-2 text-xs"
                                onClick={() => setEditingField(field)}
                              >
                                编辑
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-7 px-2 text-xs text-destructive hover:text-destructive"
                                onClick={() =>
                                  deleteFieldMutation.mutate({
                                    templateId: selectedTemplate.id,
                                    fieldId: field.id,
                                  })
                                }
                              >
                                删除
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 字段属性编辑对话框 */}
      <Dialog open={!!editingField} onOpenChange={(open) => !open && setEditingField(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>编辑字段属性 — {editingField?.fieldCode}</DialogTitle>
          </DialogHeader>
          {editingField && (
            <FieldPropertyForm
              field={editingField}
              onSave={(updates) => {
                updateFieldMutation.mutate({
                  templateId: selectedTemplate!.id,
                  fieldId: editingField.id,
                  payload: updates,
                })
              }}
              saving={updateFieldMutation.isPending}
            />
          )}
        </DialogContent>
      </Dialog>
    </section>
  )
}

function FieldPropertyForm({
  field,
  onSave,
  saving,
}: {
  field: WorkRecordField
  onSave: (updates: Record<string, unknown>) => void
  saving: boolean
}) {
  const [form, setForm] = useState({
    fieldName: field.fieldName,
    required: field.required,
    enabled: field.enabled,
    listVisible: field.listVisible,
    filterable: field.filterable,
    statistical: field.statistical,
  })

  return (
    <div className="grid gap-4 py-2">
      <div className="space-y-1.5">
        <Label htmlFor="fp-name">字段名称</Label>
        <Input
          id="fp-name"
          value={form.fieldName}
          onChange={(e) => setForm((p) => ({ ...p, fieldName: e.target.value }))}
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.required}
            onChange={(e) => setForm((p) => ({ ...p, required: e.target.checked }))}
          />
          必填
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.enabled}
            onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.checked }))}
          />
          启用
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.listVisible}
            onChange={(e) => setForm((p) => ({ ...p, listVisible: e.target.checked }))}
          />
          列表可见
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.filterable}
            onChange={(e) => setForm((p) => ({ ...p, filterable: e.target.checked }))}
          />
          可筛选
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.statistical}
            onChange={(e) => setForm((p) => ({ ...p, statistical: e.target.checked }))}
          />
          可统计
        </label>
      </div>

      <DialogFooter>
        <Button
          variant="outline"
          onClick={() => {
            const cleaned = {
              ...(form.fieldName !== field.fieldName ? { fieldName: form.fieldName } : {}),
              ...(form.required !== field.required ? { required: form.required } : {}),
              ...(form.enabled !== field.enabled ? { enabled: form.enabled } : {}),
              ...(form.listVisible !== field.listVisible ? { listVisible: form.listVisible } : {}),
              ...(form.filterable !== field.filterable ? { filterable: form.filterable } : {}),
              ...(form.statistical !== field.statistical ? { statistical: form.statistical } : {}),
            }
            if (Object.keys(cleaned).length > 0) {
              onSave(cleaned)
            }
          }}
          disabled={saving}
        >
          {saving ? '保存中…' : '保存'}
        </Button>
      </DialogFooter>
    </div>
  )
}

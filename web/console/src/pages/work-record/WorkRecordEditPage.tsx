import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import {
  createWorkRecord,
  getWorkRecord,
  listTemplateFields,
  listTemplates,
  updateWorkRecord,
} from '../../features/work-record/api'

type WorkRecordEditPageProps = {
  /** 已选模板 id（创建流程中由页面维护）。 */
  initialTemplateId?: string
}

export function WorkRecordEditPage({ initialTemplateId }: WorkRecordEditPageProps = {}) {
  const navigate = useNavigate()
  const params = useParams<{ recordId: string }>()
  const recordId = params.recordId
  const isEdit = Boolean(recordId)
  const queryClient = useQueryClient()

  const templatesQuery = useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })
  const recordQuery = useQuery({
    queryKey: ['work-record-edit', recordId],
    queryFn: () => getWorkRecord(recordId as string),
    enabled: isEdit,
  })

  const [templateId, setTemplateId] = useState<string>(initialTemplateId ?? '')
  const [title, setTitle] = useState('')
  const [status, setStatus] = useState('draft')
  const [ownerId, setOwnerId] = useState('')
  const [customData, setCustomData] = useState<Record<string, unknown>>({})

  // 切换模板时，初始化字段值
  useEffect(() => {
    if (isEdit && recordQuery.data) {
      setTemplateId(recordQuery.data.templateId)
      setTitle(recordQuery.data.title)
      setStatus(recordQuery.data.status)
      setOwnerId(recordQuery.data.ownerId ?? '')
      try {
        const parsed = JSON.parse(recordQuery.data.customDataJson || '{}') as Record<
          string,
          unknown
        >
        setCustomData(parsed)
      } catch {
        setCustomData({})
      }
    }
  }, [isEdit, recordQuery.data])

  const fieldsQuery = useQuery({
    queryKey: ['work-record-template-fields', templateId],
    queryFn: () => listTemplateFields(templateId),
    enabled: Boolean(templateId),
  })

  const persist = useMutation({
    mutationFn: async () => {
      const payload = {
        title,
        status,
        ownerId: ownerId || null,
        builtinDataJson: JSON.stringify({
          templateName: templatesQuery.data?.find((t) => t.id === templateId)?.name ?? null,
        }),
        customDataJson: JSON.stringify(customData),
      }
      if (isEdit && recordId) {
        return updateWorkRecord(recordId, payload)
      }
      return createWorkRecord({
        templateId,
        title,
        status,
        ownerId: ownerId || null,
        builtinDataJson: payload.builtinDataJson,
        customDataJson: payload.customDataJson,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-records'] })
      navigate('/app/work-records')
    },
  })

  const enabledFields = useMemo(
    () => (fieldsQuery.data ?? []).filter((field) => field.enabled),
    [fieldsQuery.data],
  )

  const setCustomField = (code: string, value: unknown) => {
    setCustomData((current) => ({ ...current, [code]: value }))
  }

  return (
    <section className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{isEdit ? '编辑工作记录' : '新建工作记录'}</h1>
        <Button variant="outline" onClick={() => navigate('/app/work-records')}>
          返回列表
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>基础字段</CardTitle>
          <CardDescription>填写标题、状态、负责人；表单字段会按所选模板动态生成。</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              if (!templateId || !title) return
              persist.mutate()
            }}
          >
            <div className="grid gap-3 md:grid-cols-2">
              <div className="space-y-1">
                <Label htmlFor="wr-template">选择模板</Label>
                <select
                  id="wr-template"
                  name="templateId"
                  required
                  value={templateId}
                  onChange={(e) => setTemplateId(e.target.value)}
                  disabled={isEdit}
                  className="h-10 w-full rounded-md border bg-background px-3 text-sm disabled:opacity-60"
                >
                  <option value="" disabled>
                    请选择模板
                  </option>
                  {(templatesQuery.data ?? []).map((tpl) => (
                    <option key={tpl.id} value={tpl.id}>
                      {tpl.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="wr-title">标题</Label>
                <Input
                  id="wr-title"
                  name="title"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="wr-status">状态</Label>
                <select
                  id="wr-status"
                  name="status"
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                  className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                >
                  <option value="draft">草稿</option>
                  <option value="processing">处理中</option>
                  <option value="done">已完成</option>
                  <option value="archived">已归档</option>
                </select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="wr-owner">负责人</Label>
                <Input
                  id="wr-owner"
                  name="ownerId"
                  placeholder="可选"
                  value={ownerId}
                  onChange={(e) => setOwnerId(e.target.value)}
                />
              </div>
            </div>

            {templateId && (
              <div className="space-y-3 border-t pt-4">
                <h2 className="text-base font-medium">模板字段</h2>
                {fieldsQuery.isLoading && (
                  <p className="text-sm text-muted-foreground">加载字段中…</p>
                )}
                {fieldsQuery.error && <p className="text-sm text-destructive">加载字段失败</p>}
                {!fieldsQuery.isLoading && enabledFields.length === 0 && (
                  <p className="text-sm text-muted-foreground">该模板尚未配置字段。</p>
                )}
                <div className="grid gap-3 md:grid-cols-2">
                  {enabledFields.map((field) => (
                    <DynamicFieldInput
                      key={field.id}
                      field={field}
                      value={customData[field.fieldCode]}
                      onChange={(value) => setCustomField(field.fieldCode, value)}
                    />
                  ))}
                </div>
              </div>
            )}

            <div className="flex items-center gap-3">
              <Button type="submit" disabled={persist.isPending}>
                {persist.isPending ? '提交中…' : isEdit ? '保存修改' : '保存草稿'}
              </Button>
              {persist.isError && (
                <p className="text-sm text-destructive">
                  保存失败：{(persist.error as Error).message}
                </p>
              )}
            </div>
          </form>
        </CardContent>
      </Card>
    </section>
  )
}

type DynamicFieldInputProps = {
  field: {
    fieldName: string
    fieldCode: string
    fieldType: string
    required: boolean
    defaultValue?: string | null
    optionsJson: string
  }
  value: unknown
  onChange: (value: unknown) => void
}

function DynamicFieldInput({ field, value, onChange }: DynamicFieldInputProps) {
  const id = `fld-${field.fieldCode}`
  const label = (
    <Label htmlFor={id}>
      {field.fieldName}
      {field.required && <span className="ml-1 text-destructive">*</span>}
    </Label>
  )
  const initial = value ?? field.defaultValue ?? ''

  switch (field.fieldType) {
    case 'textarea':
      return (
        <div className="space-y-1 md:col-span-2">
          {label}
          <textarea
            id={id}
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
            className="min-h-24 w-full rounded-md border bg-background px-3 py-2 text-sm"
          />
        </div>
      )
    case 'number':
      return (
        <div className="space-y-1">
          {label}
          <Input
            id={id}
            type="number"
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
          />
        </div>
      )
    case 'switch':
      return (
        <div className="flex items-center gap-2">
          <input
            id={id}
            type="checkbox"
            checked={Boolean(value)}
            onChange={(e) => onChange(e.target.checked)}
          />
          {label}
        </div>
      )
    case 'select':
    case 'multi_select': {
      const options: Array<{ label: string; value: string }> = (() => {
        try {
          const parsed = JSON.parse(field.optionsJson || '[]') as Array<Record<string, unknown>>
          return parsed.map((opt) => ({
            label: String(opt.label ?? opt.value ?? ''),
            value: String(opt.value ?? ''),
          }))
        } catch {
          return []
        }
      })()
      if (field.fieldType === 'multi_select') {
        const selected = Array.isArray(value)
          ? (value as string[])
          : String(initial)
              .split(',')
              .map((s) => s.trim())
              .filter(Boolean)
        return (
          <div className="space-y-1 md:col-span-2">
            {label}
            <div className="grid gap-1 rounded-md border bg-background p-2">
              {options.map((opt) => (
                <label key={opt.value} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={selected.includes(opt.value)}
                    onChange={(e) => {
                      const next = new Set(selected)
                      if (e.target.checked) next.add(opt.value)
                      else next.delete(opt.value)
                      onChange(Array.from(next))
                    }}
                  />
                  {opt.label}
                </label>
              ))}
              {options.length === 0 && (
                <span className="text-xs text-muted-foreground">该字段尚未配置选项。</span>
              )}
            </div>
          </div>
        )
      }
      return (
        <div className="space-y-1">
          {label}
          <select
            id={id}
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
            className="h-10 w-full rounded-md border bg-background px-3 text-sm"
          >
            <option value="" disabled>
              请选择
            </option>
            {options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      )
    }
    case 'date':
      return (
        <div className="space-y-1">
          {label}
          <Input
            id={id}
            type="date"
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
          />
        </div>
      )
    case 'datetime':
      return (
        <div className="space-y-1">
          {label}
          <Input
            id={id}
            type="datetime-local"
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
          />
        </div>
      )
    case 'user':
      return (
        <div className="space-y-1">
          {label}
          <Input
            id={id}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
            placeholder="用户 ID"
          />
        </div>
      )
    case 'text':
    default:
      return (
        <div className="space-y-1">
          {label}
          <Input
            id={id}
            required={field.required}
            value={String(initial)}
            onChange={(e) => onChange(e.target.value)}
          />
        </div>
      )
  }
}

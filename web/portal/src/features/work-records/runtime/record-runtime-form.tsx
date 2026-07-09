import { useMemo } from 'react'
import { useAuthStore } from '@/stores/auth-store'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DynamicFieldControl } from './dynamic-field-control'
import { setCustomValue, statusLabel, validateRuntimeForm } from './schema'
import type {
  RuntimeDictOptions,
  WorkRecordField,
  WorkRecordRuntimeFormValue,
  WorkRecordStatus,
  WorkRecordTemplate,
} from './types'

type RecordRuntimeFormProps = {
  mode: 'create' | 'edit'
  templates: WorkRecordTemplate[]
  fields: WorkRecordField[]
  dictOptions: RuntimeDictOptions
  value: WorkRecordRuntimeFormValue
  submitting?: boolean
  onTemplateChange?: (templateId: string) => void
  onChange: (value: WorkRecordRuntimeFormValue) => void
  onSaveDraft: () => void
  onSubmitDone: () => void
  onCancel: () => void
}

export function RecordRuntimeForm({
  mode,
  templates,
  fields,
  dictOptions,
  value,
  submitting,
  onTemplateChange,
  onChange,
  onSaveDraft,
  onSubmitDone,
  onCancel,
}: RecordRuntimeFormProps) {
  const authUser = useAuthStore((state) => state.auth.user)
  const validation = useMemo(
    () => validateRuntimeForm(value, fields),
    [value, fields]
  )

  const enabledFields = fields
    .filter((field) => field.enabled)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  const changeStatus = (status: WorkRecordStatus) => {
    onChange({ ...value, status })
  }

  return (
    <main className='grid gap-4 p-6 xl:grid-cols-[minmax(0,1fr)_320px]'>
      <section className='grid gap-4'>
        <Card>
          <CardHeader>
            <CardTitle>
              {mode === 'create' ? '新建工作记录' : '编辑工作记录'}
            </CardTitle>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <label className='grid gap-1 text-sm'>
              <span className='font-medium'>标题 *</span>
              <input
                className='rounded-md border bg-background px-3 py-2'
                value={value.title}
                onChange={(event) =>
                  onChange({ ...value, title: event.target.value })
                }
              />
            </label>

            <label className='grid gap-1 text-sm'>
              <span className='font-medium'>模板 *</span>
              <select
                className='rounded-md border bg-background px-3 py-2'
                value={value.templateId}
                disabled={mode === 'edit'}
                onChange={(event) => onTemplateChange?.(event.target.value)}
              >
                <option value=''>请选择模板</option>
                {templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
            </label>

            <div className='grid gap-4 md:grid-cols-3'>
              <label className='grid gap-1 text-sm'>
                <span className='font-medium'>状态</span>
                <select
                  className='rounded-md border bg-background px-3 py-2'
                  value={value.status}
                  onChange={(event) =>
                    changeStatus(event.target.value as WorkRecordStatus)
                  }
                >
                  <option value='draft'>草稿</option>
                  <option value='processing'>处理中</option>
                  <option value='done'>已完成</option>
                  <option value='archived'>已归档</option>
                </select>
              </label>

              <label className='grid gap-1 text-sm'>
                <span className='font-medium'>负责人</span>
                <input
                  className='rounded-md border bg-background px-3 py-2'
                  placeholder='用户 ID / 账号'
                  value={value.ownerId}
                  onChange={(event) =>
                    onChange({ ...value, ownerId: event.target.value })
                  }
                />
              </label>

              <label className='grid gap-1 text-sm'>
                <span className='font-medium'>记录时间 *</span>
                <input
                  className='rounded-md border bg-background px-3 py-2'
                  type='datetime-local'
                  value={value.recordTime}
                  onChange={(event) =>
                    onChange({ ...value, recordTime: event.target.value })
                  }
                />
              </label>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>动态字段</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-4'>
            {enabledFields.length === 0 ? (
              <div className='rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground'>
                当前模板没有可填写字段
              </div>
            ) : null}

            {enabledFields.map((field) => (
              <label key={field.id} className='grid gap-1 text-sm'>
                <span className='font-medium'>
                  {field.fieldName}
                  {field.required ? (
                    <span className='text-red-500'> *</span>
                  ) : null}
                </span>
                <DynamicFieldControl
                  field={field}
                  value={value.customData[field.fieldCode]}
                  dictOptions={dictOptions}
                  onChange={(fieldValue) =>
                    onChange(setCustomValue(value, field.fieldCode, fieldValue))
                  }
                />
              </label>
            ))}
          </CardContent>
        </Card>
      </section>

      <aside className='grid content-start gap-4'>
        <Card>
          <CardHeader>
            <CardTitle>提交</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 text-sm'>
            <div>当前状态：{statusLabel(value.status)}</div>
            <div>模板版本：{value.templateVersionId || '-'}</div>
            <div>当前用户：{authUser?.accountNo ?? authUser?.email ?? '-'}</div>

            {validation.errors.length ? (
              <div className='rounded-md border border-red-200 bg-red-50 p-3 text-red-700'>
                <div className='mb-1 font-medium'>提交前校验失败</div>
                <ul className='list-disc pl-5'>
                  {validation.errors.map((error) => (
                    <li key={error}>{error}</li>
                  ))}
                </ul>
              </div>
            ) : (
              <div className='rounded-md border border-green-200 bg-green-50 p-3 text-green-800'>
                前端校验通过，提交后后端会再次校验。
              </div>
            )}

            <Button
              type='button'
              variant='outline'
              disabled={submitting || !validation.valid}
              onClick={onSaveDraft}
            >
              保存草稿
            </Button>

            <Button
              type='button'
              disabled={submitting || !validation.valid}
              onClick={onSubmitDone}
            >
              提交完成
            </Button>

            <Button type='button' variant='ghost' onClick={onCancel}>
              返回
            </Button>
          </CardContent>
        </Card>
      </aside>
    </main>
  )
}

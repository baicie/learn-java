import type { ConfirmOptions } from '@/components/feedback/confirm-provider'

export type FieldDeleteRisk = {
  fieldName: string
  fieldCode: string
  published: boolean
  required: boolean
  filterable: boolean
  exportable: boolean
}

export function fieldDeleteConfirmOptions(
  field: FieldDeleteRisk
): ConfirmOptions {
  const risks = [
    field.published
      ? '该字段已经进入发布版本，历史记录中的值必须继续保留。'
      : null,
    field.required
      ? '该字段当前为必填字段，移除后会改变新版本填写规则。'
      : null,
    field.filterable
      ? '该字段当前用于动态筛选，移除后将无法继续作为新版本筛选条件。'
      : null,
    field.exportable
      ? '该字段当前允许导出，移除后不会出现在新版本导出列中。'
      : null,
  ].filter(Boolean) as string[]

  return {
    title: `移除字段“${field.fieldName}”`,
    description:
      '字段将从当前设计稿中移除。已发布字段只能在新版本中禁用，禁止清理历史记录中的字段值。',
    confirmText: '确认移除字段',
    variant: 'destructive',
    details: (
      <div className='grid gap-2'>
        <div>
          字段编码：
          <code className='ml-1'>{field.fieldCode}</code>
        </div>

        {risks.length ? (
          <ul className='list-disc space-y-1 pl-5 text-muted-foreground'>
            {risks.map((risk) => (
              <li key={risk}>{risk}</li>
            ))}
          </ul>
        ) : null}
      </div>
    ),
  }
}

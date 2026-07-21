import type { TFunction } from 'i18next'
import type { ConfirmOptions } from '@/components/feedback/confirm-provider'

type FieldDeleteConfirmInput = {
  fieldName: string
  fieldCode: string
  published: boolean
  required: boolean
  exportable: boolean
  t: TFunction
}

export function fieldDeleteConfirmOptions(
  input: FieldDeleteConfirmInput
): ConfirmOptions {
  const { fieldName, fieldCode, published, required, exportable, t } = input

  const detail: string[] = []

  detail.push(
    `${t('workRecords.designer.removeField.fieldCode')}：${fieldCode}`
  )

  if (published) {
    detail.push(t('workRecords.designer.removeField.publishedHint'))
  }
  if (required) {
    detail.push(t('workRecords.designer.removeField.requiredHint'))
  }
  if (exportable) {
    detail.push(t('workRecords.designer.removeField.exportableHint'))
  }

  return {
    title: t('workRecords.designer.removeField.title', { name: fieldName }),
    description: t('workRecords.designer.removeField.description'),
    details: (
      <ul className='list-disc space-y-1 pl-5'>
        {detail.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    ),
    confirmText: t('workRecords.designer.removeField.confirm'),
    cancelText: t('common.cancel'),
    variant: published ? 'destructive' : 'warning',
  }
}

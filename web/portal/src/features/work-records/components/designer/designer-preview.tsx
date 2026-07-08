import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { FormilyRuntimeForm } from '@/features/work-records/components/formily-runtime-form'
import {
  listFieldCodes,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'

export type DesignerPreviewProps = {
  schema: DesignerSchema
}

export function DesignerPreview({ schema }: DesignerPreviewProps) {
  const { t } = useTranslation()
  const codes = listFieldCodes(schema)
  const serialized = useMemo(
    () => JSON.stringify({ ...schema, properties: { ...schema.properties } }),
    [schema]
  )

  if (codes.length === 0) {
    return (
      <div
        data-testid='designer-preview-empty'
        className='rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground'
      >
        {t('workRecords.designer.preview.empty')}
      </div>
    )
  }

  return (
    <div
      data-testid='designer-preview'
      className='grid gap-3 rounded-md border p-4'
      aria-label={t('workRecords.designer.preview.region')}
    >
      <p className='text-sm font-medium'>
        {t('workRecords.designer.preview.title')}
      </p>
      <FormilyRuntimeForm
        key={serialized}
        schema={schema}
        initialValues={{}}
        readOnly
      />
    </div>
  )
}

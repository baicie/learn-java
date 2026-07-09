import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { DesignerField } from './types'

type FormPreviewProps = {
  fields: DesignerField[]
}

export function FormPreview({ fields }: FormPreviewProps) {
  const enabledFields = fields.filter((field) => field.enabled)

  return (
    <Card>
      <CardHeader>
        <CardTitle>实时预览</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-3'>
        {enabledFields.length === 0 ? (
          <div className='text-sm text-muted-foreground'>暂无字段</div>
        ) : null}

        {enabledFields.map((field) => (
          <label key={field.id} className='grid gap-1 text-sm'>
            <span className='font-medium'>
              {field.fieldName}
              {field.required ? <span className='text-red-500'> *</span> : null}
            </span>
            <PreviewControl field={field} />
          </label>
        ))}
      </CardContent>
    </Card>
  )
}

function PreviewControl({ field }: { field: DesignerField }) {
  const className = 'rounded-md border bg-background px-3 py-2 text-sm'

  if (field.fieldType === 'textarea') {
    return <textarea className={className} placeholder={field.fieldCode} />
  }

  if (field.fieldType === 'number') {
    return <input className={className} type='number' placeholder='0' />
  }

  if (field.fieldType === 'date') {
    return <input className={className} type='date' />
  }

  if (field.fieldType === 'datetime') {
    return <input className={className} type='datetime-local' />
  }

  if (field.fieldType === 'select') {
    return (
      <select className={className}>
        <option>
          {field.optionSource === 'dict' ? field.dictCode : '选项'}
        </option>
      </select>
    )
  }

  if (field.fieldType === 'multi_select') {
    return (
      <select className={className} multiple>
        <option>
          {field.optionSource === 'dict' ? field.dictCode : '选项'}
        </option>
      </select>
    )
  }

  if (field.fieldType === 'boolean') {
    return (
      <label className='flex items-center gap-2'>
        <input type='checkbox' />是 / 否
      </label>
    )
  }

  return <input className={className} placeholder={field.fieldCode} />
}

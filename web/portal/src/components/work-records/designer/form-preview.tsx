import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import type { DesignerField } from './types'

type FormPreviewProps = {
  fields: DesignerField[]
}

export function FormPreview({ fields }: FormPreviewProps) {
  const { t } = useTranslation()
  const enabledFields = fields.filter((field) => field.enabled)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('workRecords.designer.preview.title')}</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-3'>
        {enabledFields.length === 0 ? (
          <div className='text-sm text-muted-foreground'>
            {t('workRecords.designer.preview.empty')}
          </div>
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
  if (field.fieldType === 'textarea') {
    return <Textarea placeholder={field.fieldCode} />
  }

  if (field.fieldType === 'number') {
    return <Input type='number' placeholder='0' />
  }

  if (field.fieldType === 'date') {
    return <Input type='date' />
  }

  if (field.fieldType === 'datetime') {
    return <Input type='datetime-local' />
  }

  if (field.fieldType === 'select') {
    return <PreviewSelect field={field} />
  }

  if (field.fieldType === 'multi_select') {
    return <PreviewSelect field={field} />
  }

  if (field.fieldType === 'boolean') {
    return (
      <label className='flex items-center gap-2'>
        <Checkbox />是 / 否
      </label>
    )
  }

  return <Input placeholder={field.fieldCode} />
}

function PreviewSelect({ field }: { field: DesignerField }) {
  const label = field.optionSource === 'dict' ? field.dictCode : '选项'
  return (
    <Select disabled value='preview'>
      <SelectTrigger className='w-full'>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value='preview'>{label}</SelectItem>
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

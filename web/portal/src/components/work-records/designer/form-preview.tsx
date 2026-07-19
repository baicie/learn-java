import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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

        {enabledFields.map((field) => {
          const inputId = `preview-${field.id}`
          return (
            <div key={field.id} className='grid gap-1 text-sm'>
              <Label htmlFor={inputId}>
                {field.fieldName}
                {field.required ? (
                  <span className='text-destructive' aria-hidden='true'>
                    {' '}
                    *
                  </span>
                ) : null}
              </Label>
              <PreviewControl field={field} inputId={inputId} />
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}

function PreviewControl({
  field,
  inputId,
}: {
  field: DesignerField
  inputId: string
}) {
  if (field.fieldType === 'textarea') {
    return <Textarea id={inputId} placeholder={field.fieldCode} />
  }

  if (field.fieldType === 'number') {
    return <Input id={inputId} type='number' placeholder='0' />
  }

  if (field.fieldType === 'date') {
    return <Input id={inputId} type='date' />
  }

  if (field.fieldType === 'datetime') {
    return <Input id={inputId} type='datetime-local' />
  }

  if (field.fieldType === 'select') {
    return <PreviewSelect field={field} inputId={inputId} />
  }

  if (field.fieldType === 'multi_select') {
    return <PreviewSelect field={field} inputId={inputId} />
  }

  if (field.fieldType === 'boolean') {
    return (
      <div className='flex items-center gap-2'>
        <Checkbox id={inputId} />
        <Label htmlFor={inputId} className='font-normal'>
          是 / 否
        </Label>
      </div>
    )
  }

  return <Input id={inputId} placeholder={field.fieldCode} />
}

function PreviewSelect({
  field,
  inputId,
}: {
  field: DesignerField
  inputId: string
}) {
  const label = field.optionSource === 'dict' ? field.dictCode : '选项'
  return (
    <Select disabled value='preview'>
      <SelectTrigger id={inputId} className='w-full'>
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

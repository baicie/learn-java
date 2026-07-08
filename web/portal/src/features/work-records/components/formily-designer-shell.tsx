import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { saveTemplateSchema } from '../api/template-api'
import { extractWorkRecordFields } from '../data/formily-schema'
import { useTemplates } from '../hooks/use-record-template'

const defaultSchema = {
  type: 'object',
  properties: {
    priority: {
      type: 'string',
      title: '优先级',
      required: true,
      'x-work-record': {
        fieldCode: 'priority',
        fieldType: 'select',
        optionSource: 'dict',
        dictCode: 'record_priority',
        listVisible: true,
        filterable: true,
        statistical: false,
      },
    },
  },
}

const defaultDesigner = {
  expandedProperties: new Set(Object.keys(defaultSchema.properties)),
}

const defaultSchemaText = JSON.stringify(defaultSchema, null, 2)
const defaultDesignerText = JSON.stringify(defaultDesigner, null, 2)

export function FormilyDesignerShell() {
  const { t } = useTranslation()
  const templates = useTemplates()
  const queryClient = useQueryClient()
  const [templateId, setTemplateId] = useState('')
  const selected = useMemo(
    () => templates.data?.find((template) => template.id === templateId),
    [templateId, templates.data]
  )
  const [schemaText, setSchemaText] = useState(defaultSchemaText)
  const [designerText, setDesignerText] = useState(defaultDesignerText)
  const schema = useMemo(() => {
    try {
      return JSON.parse(schemaText)
    } catch {
      return defaultSchema
    }
  }, [schemaText])
  const fields = useMemo(() => extractWorkRecordFields(schema), [schema])

  const mutation = useMutation({
    mutationFn: saveTemplateSchema,
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['work-record-templates'],
      })
      toast.success(t('common.save'))
    },
  })

  if (templates.isLoading) {
    return <Skeleton className='h-64 w-full' />
  }

  function handleSave() {
    const schema = JSON.parse(schemaText)
    const designer = JSON.parse(designerText)
    mutation.mutate({
      templateId,
      schemaJson: JSON.stringify(schema),
      designerJson: JSON.stringify(designer),
      fields: extractWorkRecordFields(schema),
    })
  }

  function handleSelectTemplate(value: string) {
    setTemplateId(value)
    const template = templates.data?.find((item) => item.id === value)
    setSchemaText(
      template?.schemaJson && template.schemaJson !== '{}'
        ? JSON.stringify(JSON.parse(template.schemaJson), null, 2)
        : defaultSchemaText
    )
    setDesignerText(
      template?.designerJson && template.designerJson !== '{}'
        ? JSON.stringify(JSON.parse(template.designerJson), null, 2)
        : defaultDesignerText
    )
  }

  return (
    <div className='grid gap-4'>
      <div className='flex flex-wrap items-center gap-2'>
        <Select value={templateId} onValueChange={handleSelectTemplate}>
          <SelectTrigger className='w-64'>
            <SelectValue placeholder='选择模板' />
          </SelectTrigger>
          <SelectContent>
            {(templates.data ?? []).map((template) => (
              <SelectItem key={template.id} value={template.id}>
                {template.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button disabled={!selected || mutation.isPending} onClick={handleSave}>
          {t('common.save')}
        </Button>
      </div>
      <div className='grid gap-4 @4xl/content:grid-cols-[minmax(0,1fr)_320px]'>
        <div className='grid gap-4'>
          <Textarea
            className='min-h-120 font-mono'
            value={schemaText}
            onChange={(event) => setSchemaText(event.target.value)}
          />
          <Textarea
            className='min-h-48 font-mono'
            value={designerText}
            onChange={(event) => setDesignerText(event.target.value)}
          />
        </div>
        <div className='rounded-md border p-4'>
          <h3 className='font-medium'>字段索引</h3>
          <div className='mt-3 grid gap-2 text-sm'>
            {fields.length ? (
              fields.map((field) => (
                <div key={field.fieldCode} className='rounded-md border p-2'>
                  <div className='font-medium'>{field.fieldName}</div>
                  <div className='text-muted-foreground'>
                    {field.fieldCode} / {field.fieldType}
                  </div>
                </div>
              ))
            ) : (
              <p className='text-muted-foreground'>暂无可同步字段</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

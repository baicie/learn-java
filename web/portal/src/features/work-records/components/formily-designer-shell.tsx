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

const defaultSchema = JSON.stringify(
  {
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
  },
  null,
  2
)

export function FormilyDesignerShell() {
  const { t } = useTranslation()
  const templates = useTemplates()
  const queryClient = useQueryClient()
  const [templateId, setTemplateId] = useState('')
  const selected = useMemo(
    () => templates.data?.find((template) => template.id === templateId),
    [templateId, templates.data]
  )
  const [schemaText, setSchemaText] = useState(defaultSchema)
  const fields = useMemo(() => {
    try {
      return extractWorkRecordFields(JSON.parse(schemaText))
    } catch {
      return []
    }
  }, [schemaText])

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
    mutation.mutate({
      templateId,
      schemaJson: JSON.stringify(schema),
      fields: extractWorkRecordFields(schema),
    })
  }

  return (
    <div className='grid gap-4'>
      <div className='flex flex-wrap items-center gap-2'>
        <Select
          value={templateId}
          onValueChange={(value) => {
            setTemplateId(value)
            const template = templates.data?.find((item) => item.id === value)
            setSchemaText(
              template?.schemaJson && template.schemaJson !== '{}'
                ? JSON.stringify(JSON.parse(template.schemaJson), null, 2)
                : defaultSchema
            )
          }}
        >
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
        <Textarea
          className='min-h-120 font-mono'
          value={schemaText}
          onChange={(event) => setSchemaText(event.target.value)}
        />
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

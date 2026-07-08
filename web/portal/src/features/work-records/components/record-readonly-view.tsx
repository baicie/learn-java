import { useMemo } from 'react'
import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import { Separator } from '@/components/ui/separator'
import { type DictItem } from '@/features/dictionaries/data/schema'
import { extractWorkRecordFields } from '../data/formily-schema'
import { type WorkRecord } from '../data/schema'
import { useRecordTemplate } from '../hooks/use-record-template'
import { useTemplateDictionaries } from '../hooks/use-template-dictionaries'

type RecordReadonlyViewProps = {
  record: WorkRecord
}

// Format value for display
function formatFieldValue(
  value: unknown,
  fieldType: string,
  dictCode: string | undefined,
  dictionaries: Record<string, DictItem[]>
): React.ReactNode {
  if (value === null || value === undefined) {
    return <span className='text-muted-foreground'>-</span>
  }

  switch (fieldType) {
    case 'boolean':
      return value ? (
        <Badge variant='outline' className='bg-green-50'>
          是
        </Badge>
      ) : (
        <Badge variant='outline' className='bg-gray-50'>
          否
        </Badge>
      )

    case 'select': {
      if (dictCode && dictionaries[dictCode]) {
        const item = dictionaries[dictCode].find((d) => d.itemValue === value)
        if (item) return item.itemLabel
      }
      return String(value)
    }

    case 'multi_select':
      if (!Array.isArray(value)) return String(value)
      return (
        <div className='flex flex-wrap gap-1'>
          {value.map((v, i) => (
            <Badge key={i} variant='secondary'>
              {String(v)}
            </Badge>
          ))}
        </div>
      )

    case 'date':
      return typeof value === 'string' ? value.slice(0, 10) : String(value)

    case 'datetime':
      if (typeof value === 'string') {
        return new Date(value).toLocaleString()
      }
      return String(value)

    case 'number':
      return typeof value === 'number' ? value.toString() : String(value)

    default:
      return String(value)
  }
}

// Render a single field row
function FieldRow({
  fieldName,
  fieldType,
  dictCode,
  value,
  dictionaries,
}: {
  fieldName: string
  fieldType: string
  dictCode: string | undefined
  value: unknown
  dictionaries: Record<string, DictItem[]>
}) {
  return (
    <div className='grid grid-cols-[140px_1fr] gap-4 py-2'>
      <dt className='text-sm font-medium text-muted-foreground'>{fieldName}</dt>
      <dd className='text-sm'>
        {formatFieldValue(value, fieldType, dictCode, dictionaries)}
      </dd>
    </div>
  )
}

// Status badge variant
function StatusBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  const variants: Record<string, 'default' | 'secondary' | 'outline'> = {
    draft: 'secondary',
    processing: 'default',
    done: 'outline',
    archived: 'outline',
  }
  const variant = variants[status] ?? 'outline'

  const labels: Record<string, string> = {
    draft: t('workRecords.status.draft'),
    processing: t('workRecords.status.processing'),
    done: t('workRecords.status.done'),
    archived: t('workRecords.status.archived'),
  }

  return <Badge variant={variant}>{labels[status] ?? status}</Badge>
}

export function RecordReadonlyView({ record }: RecordReadonlyViewProps) {
  const { t } = useTranslation()

  // Load template for schema
  const template = useRecordTemplate(record.templateId)

  // Load dictionaries with disabled items (for historical display)
  const { dictionaries, isLoading: isLoadingDicts } = useTemplateDictionaries(
    template.data,
    true // includeDisabled
  )

  // Parse custom data
  const customData = useMemo(() => {
    try {
      return JSON.parse(record.customDataJson || '{}')
    } catch {
      return {}
    }
  }, [record.customDataJson])

  // Extract fields from schema in order
  const schemaFields = useMemo(() => {
    const schemaJson = template.data?.schemaJson
    if (!schemaJson) return []
    try {
      const schema = JSON.parse(schemaJson)
      return extractWorkRecordFields(schema)
    } catch {
      return []
    }
  }, [template.data])

  // Known field codes from schema
  const knownFieldCodes = useMemo(
    () => new Set(schemaFields.map((f) => f.fieldCode)),
    [schemaFields]
  )

  // Split into known and legacy fields
  const legacyEntries = useMemo(() => {
    return Object.entries(customData).filter(
      ([key]) => !knownFieldCodes.has(key) && !isBuiltinField(key)
    )
  }, [customData, knownFieldCodes])

  if (template.isLoading || isLoadingDicts) {
    return <div className='h-64 w-full animate-pulse rounded-md bg-muted' />
  }

  return (
    <div className='grid max-w-4xl gap-4'>
      {/* Header section */}
      <div className='grid gap-3 rounded-md border p-4'>
        <div className='flex flex-wrap items-center justify-between gap-2'>
          <h3 className='text-lg font-semibold'>{record.title}</h3>
          <StatusBadge status={record.status} />
        </div>
        <Separator />
        <dl className='grid gap-3 text-sm @md/content:grid-cols-2'>
          <div>
            <dt className='text-muted-foreground'>
              {t('workRecords.detail.template')}
            </dt>
            <dd className='font-medium'>
              {template.data?.name ?? record.templateId}
            </dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>
              {t('workRecords.field.owner')}
            </dt>
            <dd>{record.ownerId ?? '-'}</dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>
              {t('workRecords.field.creator')}
            </dt>
            <dd>{record.creatorId}</dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>
              {t('workRecords.field.recordTime')}
            </dt>
            <dd>{new Date(record.recordTime).toLocaleString()}</dd>
          </div>
        </dl>
      </div>

      {/* Dynamic fields in schema order */}
      {schemaFields.length > 0 && (
        <div className='grid gap-1 rounded-md border p-4'>
          <h4 className='mb-2 text-sm font-semibold'>
            {t('workRecords.form.recordContent')}
          </h4>
          {schemaFields.map((field) => {
            const value = customData[field.fieldCode]
            // Skip undefined values
            if (value === undefined) return null
            return (
              <FieldRow
                key={field.fieldCode}
                fieldName={field.fieldName}
                fieldType={field.fieldType}
                dictCode={
                  field.optionSource === 'dict' ? field.dictCode : undefined
                }
                value={value}
                dictionaries={dictionaries}
              />
            )
          })}
        </div>
      )}

      {/* Legacy fields (collapsed by default) */}
      {legacyEntries.length > 0 && (
        <Collapsible>
          <CollapsibleTrigger className='flex w-full items-center justify-between rounded-md border p-3 text-sm font-medium hover:bg-muted/50'>
            <span>
              {t('workRecords.detail.legacyFields')} ({legacyEntries.length})
            </span>
            <ChevronDown className='size-4' />
          </CollapsibleTrigger>
          <CollapsibleContent>
            <div className='grid gap-1 rounded-md border p-4'>
              {legacyEntries.map(([key, value]) => (
                <div
                  key={key}
                  className='grid grid-cols-[140px_1fr] gap-4 py-2'
                >
                  <dt className='font-mono text-sm text-muted-foreground'>
                    {key}
                  </dt>
                  <dd className='font-mono text-sm'>
                    {typeof value === 'object'
                      ? JSON.stringify(value)
                      : String(value)}
                  </dd>
                </div>
              ))}
            </div>
          </CollapsibleContent>
        </Collapsible>
      )}
    </div>
  )
}

// Check if field is a builtin field (not stored in custom_data_json)
function isBuiltinField(code: string): boolean {
  return [
    'title',
    'templateId',
    'status',
    'ownerId',
    'recordTime',
    'creatorId',
  ].includes(code)
}

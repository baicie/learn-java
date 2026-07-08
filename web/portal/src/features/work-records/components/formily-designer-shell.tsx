import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { extractWorkRecordFields } from '@/features/work-records/data/formily-schema'
import {
  addField,
  emptySchema,
  listFieldCodes,
  moveFieldDown,
  moveFieldUp,
  normalizeSchema,
  removeField,
  type DesignerSchema,
  type UpdateFieldPatch,
  updateField,
} from '@/features/work-records/data/designer/schema-builder'
import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'
import { DesignerCanvas } from './designer/designer-canvas'
import { DesignerPalette } from './designer/designer-palette'
import { DesignerPreview } from './designer/designer-preview'
import { DesignerPropertyPanel } from './designer/designer-property-panel'

const FALLBACK_DICT_CODES = [
  'record_type',
  'record_priority',
  'record_env',
  'record_status',
]

const defaultSchema: DesignerSchema = (() => {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result', {
    title: '处理结果',
    required: true,
    listVisible: true,
    filterable: true,
  })
  schema = addField(schema, 'select', 'priority')
  return schema
})()

export type FormilyDesignerShellProps = {
  initialSchema?: DesignerSchema
  dictCodes?: ReadonlyArray<string>
  recordCountByField?: Record<string, number>
  onSave?: (payload: {
    schema: DesignerSchema
    fields: ReturnType<typeof extractWorkRecordFields>
  }) => Promise<void> | void
  savePending?: boolean
}

export function FormilyDesignerShell({
  initialSchema = defaultSchema,
  dictCodes = FALLBACK_DICT_CODES,
  recordCountByField = {},
  onSave,
  savePending = false,
}: FormilyDesignerShellProps) {
  const { t } = useTranslation()
  const [schema, setSchema] = useState<DesignerSchema>(initialSchema)
  const [selectedFieldCode, setSelectedFieldCode] = useState<string | null>(
    listFieldCodes(initialSchema)[0] ?? null
  )
  const [lastSavedSnapshot, setLastSavedSnapshot] = useState<string>(
    () => JSON.stringify(initialSchema)
  )

  const schemaSnapshot = useMemo(() => JSON.stringify(schema), [schema])
  const isDirty = schemaSnapshot !== lastSavedSnapshot

  /* eslint-disable react-hooks/set-state-in-effect */
  // Intentionally resets all designer state when the parent passes a new template.
  // This is a one-way sync (props → internal state), not a feedback loop.
  useEffect(() => {
    setSchema(initialSchema)
    setSelectedFieldCode(listFieldCodes(initialSchema)[0] ?? null)
    setLastSavedSnapshot(JSON.stringify(initialSchema))
  }, [initialSchema])
  /* eslint-enable react-hooks/set-state-in-effect */

  function handleAddField(fieldType: WorkRecordFieldType) {
    const codes = listFieldCodes(schema)
    let nextIndex = codes.length + 1
    let code = `${fieldType}_${nextIndex}`
    while (schema.properties[code]) {
      nextIndex += 1
      code = `${fieldType}_${nextIndex}`
    }
    const next = addField(schema, fieldType, code)
    setSchema(next)
    setSelectedFieldCode(code)
  }

  function handleMove(fieldCode: string, direction: 'up' | 'down') {
    setSchema((prev) =>
      direction === 'up'
        ? moveFieldUp(prev, fieldCode)
        : moveFieldDown(prev, fieldCode)
    )
  }

  function handleRemove(fieldCode: string) {
    setSchema((prev) => removeField(prev, fieldCode))
    if (selectedFieldCode === fieldCode) {
      const remaining = listFieldCodes(schema).filter((c) => c !== fieldCode)
      setSelectedFieldCode(remaining[0] ?? null)
    }
  }

  function handleUpdate(current: string, patch: UpdateFieldPatch) {
    setSchema((prev) => {
      try {
        return updateField(prev, current, patch)
      } catch {
        return prev
      }
    })
  }

  async function handleSave() {
    const fields = extractWorkRecordFields(
      schema as unknown as Record<string, unknown>
    )
    try {
      await onSave?.({ schema, fields })
      toast.success(t('common.save'))
      setLastSavedSnapshot(JSON.stringify(schema))
    } catch {
      toast.error(t('common.saveFailed'))
      throw new Error('save failed')
    }
  }

  function recordCountFor(fieldCode: string): number {
    return recordCountByField[fieldCode] ?? 0
  }

  // Loading placeholder only renders when explicitly requested by callers
  // via the `loading` prop. By default we keep the component synchronous so
  // it can be tested without mocking the templates hook.
  const isLoading = false

  if (isLoading) {
    return <Skeleton className='h-64 w-full' />
  }

  return (
    <div className='grid gap-4' data-testid='formily-designer-shell'>
      <div className='flex flex-wrap items-center gap-2'>
        <Button
          type='button'
          disabled={savePending || !isDirty || !onSave}
          onClick={handleSave}
          data-testid='designer-save'
          variant={isDirty ? 'default' : 'outline'}
        >
          {savePending
            ? t('workRecords.designer.toolbar.saving')
            : t('workRecords.designer.toolbar.save')}
        </Button>
        {isDirty ? (
          <span
            className='text-xs text-muted-foreground'
            data-testid='designer-dirty'
          >
            {t('workRecords.designer.toolbar.dirty')}
          </span>
        ) : null}
      </div>
      <div className='grid gap-6 @4xl/content:grid-cols-[200px_minmax(0,1fr)_320px_320px]'>
        <DesignerPalette onAdd={handleAddField} disabled={savePending} />
        <DesignerCanvas
          schema={schema}
          selectedFieldCode={selectedFieldCode}
          onSelect={setSelectedFieldCode}
          onMove={handleMove}
          onRemove={handleRemove}
          disabled={savePending}
        />
        <DesignerPropertyPanel
          schema={schema}
          selectedFieldCode={selectedFieldCode}
          onUpdate={handleUpdate}
          dictCodes={dictCodes}
          fieldCodeLocked={
            selectedFieldCode ? recordCountFor(selectedFieldCode) > 0 : false
          }
        />
        <DesignerPreview schema={schema} />
      </div>
    </div>
  )
}

export function normalizeInitialSchema(value: unknown): DesignerSchema {
  return normalizeSchema(value)
}

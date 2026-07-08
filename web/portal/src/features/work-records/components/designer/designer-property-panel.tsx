import { useTranslation } from 'react-i18next'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import {
  BooleanSetter,
  DictSetter,
  TextSetter,
} from '@/features/work-records/components/designer/setter'
import type {
  DesignerSchema,
  UpdateFieldPatch,
} from '@/features/work-records/data/designer/schema-builder'
import { isSelectLike } from '@/features/work-records/data/designer/field-types'
import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'

export type DesignerPropertyPanelProps = {
  schema: DesignerSchema
  selectedFieldCode: string | null
  onUpdate: (currentFieldCode: string, patch: UpdateFieldPatch) => void
  dictCodes: ReadonlyArray<string>
  fieldCodeLocked?: boolean
}

function readFieldType(
  property: Record<string, unknown>
): WorkRecordFieldType {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return (ext.fieldType as WorkRecordFieldType) ?? 'text'
}

function readOptionSource(property: Record<string, unknown>): 'static' | 'dict' {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return (ext.optionSource as 'static' | 'dict') ?? 'static'
}

function readDictCode(property: Record<string, unknown>): string | null {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  if (typeof ext.dictCode === 'string' && ext.dictCode.length > 0) {
    return ext.dictCode
  }
  return null
}

function readListVisible(property: Record<string, unknown>): boolean {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return Boolean(ext.listVisible)
}

function readFilterable(property: Record<string, unknown>): boolean {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return Boolean(ext.filterable)
}

function readStatistical(property: Record<string, unknown>): boolean {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return Boolean(ext.statistical)
}

function readRequired(property: Record<string, unknown>): boolean {
  return Boolean(property.required)
}

function readTitle(property: Record<string, unknown>): string {
  return (property.title as string) ?? ''
}

export function DesignerPropertyPanel({
  schema,
  selectedFieldCode,
  onUpdate,
  dictCodes,
  fieldCodeLocked = false,
}: DesignerPropertyPanelProps) {
  const { t } = useTranslation()

  if (!selectedFieldCode) {
    return (
      <div
        data-testid='designer-property-empty'
        className='rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground'
      >
        {t('workRecords.designer.property.empty')}
      </div>
    )
  }

  const property = schema.properties[selectedFieldCode]
  if (!property) {
    return null
  }

  const fieldType = readFieldType(property)
  const optionSource = readOptionSource(property)
  const dictCode = readDictCode(property)

  return (
    <div
      data-testid='designer-property-panel'
      className='grid gap-4'
      aria-label={t('workRecords.designer.property.region')}
    >
      <TextSetter
        label={t('workRecords.designer.property.title')}
        value={readTitle(property)}
        onChange={(title) => onUpdate(selectedFieldCode, { title })}
        required
      />

      <div className='grid gap-1.5'>
        <Label htmlFor={`field-code-${selectedFieldCode}`}>
          {t('workRecords.designer.property.fieldCode')}
        </Label>
        <input
          id={`field-code-${selectedFieldCode}`}
          data-testid='designer-property-field-code'
          value={selectedFieldCode}
          readOnly={fieldCodeLocked}
          disabled={fieldCodeLocked}
          className='rounded-md border bg-background px-3 py-1.5 text-sm disabled:cursor-not-allowed disabled:opacity-50'
          onChange={(event) => {
            if (fieldCodeLocked) return
            onUpdate(selectedFieldCode, { fieldCode: event.target.value })
          }}
        />
        {fieldCodeLocked ? (
          <p className='text-xs text-muted-foreground'>
            {t('workRecords.designer.property.fieldCodeLockedHint')}
          </p>
        ) : null}
      </div>

      <div className='grid gap-1.5'>
        <Label>
          {t('workRecords.designer.property.fieldType')}
        </Label>
        <p className='rounded-md border bg-muted px-3 py-1.5 text-sm'>
          {fieldType}
        </p>
      </div>

      {isSelectLike(fieldType) ? (
        <div className='grid gap-1.5'>
          <Label>
            {t('workRecords.designer.property.optionSource')}
          </Label>
          <div className='flex gap-2'>
            <button
              type='button'
              data-testid='designer-property-option-static'
              className={`flex-1 rounded-md border px-3 py-1.5 text-sm ${
                optionSource === 'static'
                  ? 'border-primary text-primary'
                  : 'border-input'
              }`}
              onClick={() =>
                onUpdate(selectedFieldCode, {
                  optionSource: 'static',
                  dictCode: undefined,
                })
              }
            >
              {t('workRecords.designer.property.optionSourceStatic')}
            </button>
            <button
              type='button'
              data-testid='designer-property-option-dict'
              className={`flex-1 rounded-md border px-3 py-1.5 text-sm ${
                optionSource === 'dict'
                  ? 'border-primary text-primary'
                  : 'border-input'
              }`}
              onClick={() =>
                onUpdate(selectedFieldCode, { optionSource: 'dict' })
              }
            >
              {t('workRecords.designer.property.optionSourceDict')}
            </button>
          </div>
        </div>
      ) : null}

      {isSelectLike(fieldType) && optionSource === 'dict' ? (
        <DictSetter
          label={t('workRecords.designer.property.dictCode')}
          value={dictCode}
          dictCodes={dictCodes}
          onChange={(next) => {
            onUpdate(selectedFieldCode, {
              optionSource: 'dict',
              dictCode: next ?? undefined,
            })
          }}
        />
      ) : null}

      <BooleanSetter
        label={t('workRecords.designer.property.required')}
        value={readRequired(property)}
        onChange={(required) => onUpdate(selectedFieldCode, { required })}
      />

      <div className='grid gap-2'>
        <Label>{t('workRecords.designer.property.indexes')}</Label>
        <label className='flex items-center gap-2 text-sm'>
          <Checkbox
            checked={readListVisible(property)}
            onCheckedChange={(checked) =>
              onUpdate(selectedFieldCode, { listVisible: Boolean(checked) })
            }
            data-testid='designer-property-list-visible'
          />
          {t('workRecords.designer.property.listVisible')}
        </label>
        <label className='flex items-center gap-2 text-sm'>
          <Checkbox
            checked={readFilterable(property)}
            onCheckedChange={(checked) =>
              onUpdate(selectedFieldCode, { filterable: Boolean(checked) })
            }
            data-testid='designer-property-filterable'
          />
          {t('workRecords.designer.property.filterable')}
        </label>
        <label className='flex items-center gap-2 text-sm'>
          <Checkbox
            checked={readStatistical(property)}
            onCheckedChange={(checked) =>
              onUpdate(selectedFieldCode, { statistical: Boolean(checked) })
            }
            data-testid='designer-property-statistical'
          />
          {t('workRecords.designer.property.statistical')}
        </label>
      </div>
    </div>
  )
}

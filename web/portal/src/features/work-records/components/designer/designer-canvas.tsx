import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  listFieldCodes,
  type DesignerSchema,
} from '@/features/work-records/data/designer/schema-builder'

export type DesignerCanvasProps = {
  schema: DesignerSchema
  selectedFieldCode: string | null
  onSelect: (fieldCode: string) => void
  onMove: (fieldCode: string, direction: 'up' | 'down') => void
  onRemove: (fieldCode: string) => void
  disabled?: boolean
}

function descriptorSummary(
  property: Record<string, unknown>
): { fieldType: string; required: boolean } {
  const ext = (property['x-work-record'] ?? {}) as Record<string, unknown>
  return {
    fieldType: (ext.fieldType as string) ?? 'text',
    required: Boolean(property.required),
  }
}

export function DesignerCanvas({
  schema,
  selectedFieldCode,
  onSelect,
  onMove,
  onRemove,
  disabled,
}: DesignerCanvasProps) {
  const { t } = useTranslation()
  const codes = listFieldCodes(schema)

  if (codes.length === 0) {
    return (
      <div
        data-testid='designer-canvas-empty'
        className='rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground'
      >
        {t('workRecords.designer.emptyHint')}
      </div>
    )
  }

  return (
    <ul
      data-testid='designer-canvas'
      className='grid gap-2'
      aria-label={t('workRecords.designer.canvas.region')}
    >
      {codes.map((code, index) => {
        const property = schema.properties[code]
        const summary = descriptorSummary(property)
        const isSelected = selectedFieldCode === code
        return (
          <li key={code}>
            <div
              role='button'
              tabIndex={0}
              aria-pressed={isSelected}
              data-testid={`designer-canvas-item-${code}`}
              data-selected={isSelected || undefined}
              className='flex items-center justify-between rounded-md border p-3'
              onClick={() => onSelect(code)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  onSelect(code)
                }
              }}
            >
              <div className='grid gap-0.5'>
                <span className='text-sm font-medium'>
                  {(property.title as string) ?? code}
                </span>
                <span className='text-xs text-muted-foreground'>
                  {code} · {summary.fieldType}
                  {summary.required ? ' · *' : ''}
                </span>
              </div>
              <div className='flex items-center gap-1'>
                <Button
                  type='button'
                  size='sm'
                  variant='ghost'
                  disabled={disabled || index === 0}
                  onClick={(event) => {
                    event.stopPropagation()
                    onMove(code, 'up')
                  }}
                  data-testid={`designer-canvas-up-${code}`}
                  aria-label={t('workRecords.designer.canvas.moveUp')}
                >
                  ↑
                </Button>
                <Button
                  type='button'
                  size='sm'
                  variant='ghost'
                  disabled={disabled || index === codes.length - 1}
                  onClick={(event) => {
                    event.stopPropagation()
                    onMove(code, 'down')
                  }}
                  data-testid={`designer-canvas-down-${code}`}
                  aria-label={t('workRecords.designer.canvas.moveDown')}
                >
                  ↓
                </Button>
                <Button
                  type='button'
                  size='sm'
                  variant='destructive'
                  disabled={disabled}
                  onClick={(event) => {
                    event.stopPropagation()
                    onRemove(code)
                  }}
                  data-testid={`designer-canvas-remove-${code}`}
                  aria-label={t('workRecords.designer.canvas.remove')}
                >
                  ✕
                </Button>
              </div>
            </div>
          </li>
        )
      })}
    </ul>
  )
}

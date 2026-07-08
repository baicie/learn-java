import { useTranslation } from 'react-i18next'
import { designerPalette } from '@/features/work-records/data/designer/palette'
import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'
import { Button } from '@/components/ui/button'

export type DesignerPaletteProps = {
  onAdd: (fieldType: WorkRecordFieldType) => void
  disabled?: boolean
}

export function DesignerPalette({ onAdd, disabled }: DesignerPaletteProps) {
  const { t } = useTranslation()

  return (
    <div
      className='grid gap-2'
      data-testid='designer-palette'
      aria-label={t('workRecords.designer.palette.region')}
    >
      <p className='text-sm font-medium'>
        {t('workRecords.designer.palette.region')}
      </p>
      <div className='grid gap-2'>
        {designerPalette.map((option) => (
          <Button
            key={option.fieldType}
            type='button'
            variant='outline'
            className='justify-between'
            disabled={disabled}
            data-testid={`designer-palette-${option.fieldType}`}
            onClick={() => onAdd(option.fieldType)}
          >
            <span className='flex flex-col items-start'>
              <span className='text-sm font-medium'>
                {t(option.i18nKey)}
              </span>
              <span className='text-xs text-muted-foreground'>
                {t(option.descriptionI18nKey)}
              </span>
            </span>
          </Button>
        ))}
      </div>
    </div>
  )
}

import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'
import {
  defaultFieldSchema,
  type FieldDescriptor,
} from '@/features/work-records/data/designer/field-types'

export type FieldTypeOption = {
  fieldType: WorkRecordFieldType
  i18nKey: string
  iconName: PaletteIconName
  descriptionI18nKey: string
}

export type PaletteIconName =
  | 'Type'
  | 'AlignLeft'
  | 'Hash'
  | 'Calendar'
  | 'Clock'
  | 'List'
  | 'ListChecks'
  | 'User'
  | 'ToggleLeft'

const palette: FieldTypeOption[] = [
  {
    fieldType: 'text',
    i18nKey: 'workRecords.designer.palette.text',
    iconName: 'Type',
    descriptionI18nKey: 'workRecords.designer.palette.textDescription',
  },
  {
    fieldType: 'textarea',
    i18nKey: 'workRecords.designer.palette.textarea',
    iconName: 'AlignLeft',
    descriptionI18nKey: 'workRecords.designer.palette.textareaDescription',
  },
  {
    fieldType: 'number',
    i18nKey: 'workRecords.designer.palette.number',
    iconName: 'Hash',
    descriptionI18nKey: 'workRecords.designer.palette.numberDescription',
  },
  {
    fieldType: 'date',
    i18nKey: 'workRecords.designer.palette.date',
    iconName: 'Calendar',
    descriptionI18nKey: 'workRecords.designer.palette.dateDescription',
  },
  {
    fieldType: 'datetime',
    i18nKey: 'workRecords.designer.palette.datetime',
    iconName: 'Clock',
    descriptionI18nKey: 'workRecords.designer.palette.datetimeDescription',
  },
  {
    fieldType: 'select',
    i18nKey: 'workRecords.designer.palette.select',
    iconName: 'List',
    descriptionI18nKey: 'workRecords.designer.palette.selectDescription',
  },
  {
    fieldType: 'multi_select',
    i18nKey: 'workRecords.designer.palette.multiSelect',
    iconName: 'ListChecks',
    descriptionI18nKey:
      'workRecords.designer.palette.multiSelectDescription',
  },
  {
    fieldType: 'user',
    i18nKey: 'workRecords.designer.palette.user',
    iconName: 'User',
    descriptionI18nKey: 'workRecords.designer.palette.userDescription',
  },
  {
    fieldType: 'boolean',
    i18nKey: 'workRecords.designer.palette.boolean',
    iconName: 'ToggleLeft',
    descriptionI18nKey: 'workRecords.designer.palette.booleanDescription',
  },
]

export const designerPalette: ReadonlyArray<FieldTypeOption> = palette

export function getFieldTypeOption(
  fieldType: WorkRecordFieldType
): FieldTypeOption {
  const option = palette.find((entry) => entry.fieldType === fieldType)
  if (!option) {
    throw new Error(`unknown palette fieldType: ${fieldType satisfies WorkRecordFieldType}`)
  }
  return option
}

export function defaultDescriptorFor(
  fieldType: WorkRecordFieldType,
  fieldCode: string,
  overrides: Partial<FieldDescriptor> = {}
): FieldDescriptor {
  const { fieldType: _ft, fieldCode: _fc, ...rest } = overrides
  const descriptor: FieldDescriptor = {
    fieldCode,
    fieldType,
    title: overrides.title ?? fieldCode,
    required: overrides.required ?? false,
    listVisible: overrides.listVisible ?? false,
    filterable: overrides.filterable ?? false,
    statistical: overrides.statistical ?? false,
    optionSource: overrides.optionSource ?? 'static',
    dictCode: overrides.dictCode,
    options: overrides.options,
    ...rest,
  }
  return descriptor
}

export function defaultFieldSchemaFromPalette(
  fieldType: WorkRecordFieldType,
  fieldCode: string,
  overrides: Partial<FieldDescriptor> = {}
) {
  const descriptor = defaultDescriptorFor(fieldType, fieldCode, overrides)
  return {
    descriptor,
    schema: defaultFieldSchema(fieldCode, fieldType, overrides),
  }
}

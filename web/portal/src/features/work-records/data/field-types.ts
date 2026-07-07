export const workRecordFieldTypes = [
  'text',
  'textarea',
  'number',
  'date',
  'datetime',
  'select',
  'multi_select',
  'user',
  'boolean',
] as const

export type WorkRecordFieldType = (typeof workRecordFieldTypes)[number]

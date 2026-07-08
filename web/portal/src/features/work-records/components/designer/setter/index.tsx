import type { WorkRecordFieldType } from '@/features/work-records/data/field-types'
import {
  defaultDescriptorFor,
} from '@/features/work-records/data/designer/palette'
import { SelectSetter } from './select-setter'
import { TextSetter } from './text-setter'
import { NumberSetter } from './number-setter'
import { BooleanSetter } from './boolean-setter'
import { DictSetter } from './dict-setter'

export { TextSetter } from './text-setter'
export { NumberSetter } from './number-setter'
export { BooleanSetter } from './boolean-setter'
export { SelectSetter } from './select-setter'
export { DictSetter } from './dict-setter'
export type { SetterProps } from './types'

export type FieldSetterKind =
  | 'text'
  | 'number'
  | 'boolean'
  | 'select'
  | 'dict'

export function isFieldSetterKind(value: unknown): value is FieldSetterKind {
  return (
    value === 'text' ||
    value === 'number' ||
    value === 'boolean' ||
    value === 'select' ||
    value === 'dict'
  )
}

export const STATIC_OPTIONS_PLACEHOLDER: {
  label: string
  value: string
}[] = [
  { label: '—', value: '' },
]

export function SetterByFieldType(props: {
  fieldType: WorkRecordFieldType
  optionSource: 'static' | 'dict'
  optionList: ReadonlyArray<{ label: string; value: string }>
  text: { value: string; onChange: (next: string) => void; label?: string }
  number: {
    value: number | null
    onChange: (next: number | null) => void
    label?: string
  }
  boolean: {
    value: boolean
    onChange: (next: boolean) => void
    label?: string
  }
  select: {
    value: string | null
    onChange: (next: string | null) => void
    label?: string
  }
  dict: {
    value: string | null
    onChange: (next: string | null) => void
    dictCodes: ReadonlyArray<string>
    label?: string
  }
}) {
  const { fieldType, optionSource } = props

  if (optionSource === 'dict') {
    return (
      <DictSetter
        value={props.dict.value}
        onChange={props.dict.onChange}
        dictCodes={props.dict.dictCodes}
        label={props.dict.label}
      />
    )
  }

  if (fieldType === 'boolean') {
    return (
      <BooleanSetter
        value={props.boolean.value}
        onChange={props.boolean.onChange}
        label={props.boolean.label}
      />
    )
  }

  if (fieldType === 'number') {
    return (
      <NumberSetter
        value={props.number.value}
        onChange={props.number.onChange}
        label={props.number.label}
      />
    )
  }

  if (fieldType === 'select' || fieldType === 'multi_select') {
    const options =
      props.optionList.length > 0 ? props.optionList : STATIC_OPTIONS_PLACEHOLDER
    return (
      <SelectSetter
        value={props.select.value}
        onChange={props.select.onChange}
        label={props.select.label}
        options={options}
      />
    )
  }

  return (
    <TextSetter
      value={props.text.value}
      onChange={props.text.onChange}
      label={props.text.label}
    />
  )
}

export { defaultDescriptorFor }

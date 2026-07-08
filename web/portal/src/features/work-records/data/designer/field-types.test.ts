import { describe, expect, it } from 'vitest'
import { workRecordFieldTypes } from '@/features/work-records/data/field-types'
import {
  defaultFieldDescriptor,
  defaultFieldSchema,
  defaultSchemaProperty,
  designerFieldTypes,
  isSelectLike,
} from '@/features/work-records/data/designer/field-types'

describe('defaultFieldDescriptor', () => {
  it('returns defaults for a text field', () => {
    const desc = defaultFieldDescriptor('process_result')
    expect(desc).toEqual({
      fieldCode: 'process_result',
      fieldType: 'text',
      title: 'process_result',
      required: false,
      listVisible: false,
      filterable: false,
      statistical: false,
      optionSource: 'static',
    })
  })

  it('trims whitespace from the field code', () => {
    const desc = defaultFieldDescriptor('  severity  ')
    expect(desc.fieldCode).toBe('severity')
  })

  it('rejects empty field codes', () => {
    expect(() => defaultFieldDescriptor('   ')).toThrow(
      'field code must not be empty'
    )
  })

  it('rejects reserved field codes', () => {
    expect(() => defaultFieldDescriptor('title')).toThrow('reserved fieldCode')
    expect(() =>
      defaultFieldDescriptor('custom_data_json', { fieldType: 'text' })
    ).toThrow('reserved fieldCode')
  })

  it('merges overrides without mutating the base', () => {
    const a = defaultFieldDescriptor('priority', {
      fieldType: 'select',
      optionSource: 'dict',
      dictCode: 'record_priority',
    })
    expect(a.fieldType).toBe('select')
    expect(a.dictCode).toBe('record_priority')

    const b = defaultFieldDescriptor('process_result')
    expect(b.fieldType).toBe('text')
    expect(b.dictCode).toBeUndefined()
  })

  it('keeps the explicit fieldCode override on top of overrides', () => {
    const desc = defaultFieldDescriptor('foo', {
      fieldCode: 'bar',
      fieldType: 'text',
    })
    expect(desc.fieldCode).toBe('foo')
  })
})

describe('defaultSchemaProperty', () => {
  it.each(workRecordFieldTypes)(
    'produces a schema for fieldType=%s',
    (fieldType) => {
      const property = defaultSchemaProperty(
        defaultFieldDescriptor('probe', { fieldType, title: 'Probe' })
      )
      expect(property.title).toBe('Probe')
      const ext = property['x-work-record'] as Record<string, unknown>
      expect(ext.fieldCode).toBe('probe')
      expect(ext.fieldType).toBe(fieldType)
    }
  )

  it('marks text and user fields as string', () => {
    expect(
      defaultSchemaProperty(
        defaultFieldDescriptor('owner', { fieldType: 'user' })
      ).type
    ).toBe('string')
    expect(
      defaultSchemaProperty(
        defaultFieldDescriptor('note', { fieldType: 'text' })
      ).type
    ).toBe('string')
  })

  it('emits textarea with x-component', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('detail', { fieldType: 'textarea' })
    )
    expect(property['x-component']).toBe('TextArea')
  })

  it('attaches format=date for date fields', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('occur_date', { fieldType: 'date' })
    )
    expect(property.format).toBe('date')
  })

  it('attaches format=date-time for datetime fields', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('occur_at', { fieldType: 'datetime' })
    )
    expect(property.format).toBe('date-time')
  })

  it('uses array type for multi_select and string for select', () => {
    expect(
      defaultSchemaProperty(
        defaultFieldDescriptor('tags', { fieldType: 'multi_select' })
      ).type
    ).toBe('array')
    expect(
      defaultSchemaProperty(
        defaultFieldDescriptor('priority', { fieldType: 'select' })
      ).type
    ).toBe('string')
  })

  it('emits enum when options are present', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('env', {
        fieldType: 'select',
        options: [
          { label: 'Prod', value: 'prod' },
          { label: 'Stage', value: 'stage' },
        ],
      })
    )
    expect(property.enum).toEqual([
      { label: 'Prod', value: 'prod' },
      { label: 'Stage', value: 'stage' },
    ])
  })

  it('omits enum when options are empty', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('env', { fieldType: 'select' })
    )
    expect(property.enum).toBeUndefined()
  })

  it('requires dictCode when optionSource=dict', () => {
    expect(() =>
      defaultSchemaProperty(
        defaultFieldDescriptor('priority', {
          fieldType: 'select',
          optionSource: 'dict',
        })
      )
    ).toThrow('dictCode is required for priority')
  })

  it('honors required flag', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('severity', { required: true })
    )
    expect(property.required).toBe(true)
  })

  it('propagates listVisible / filterable / statistical into the extension', () => {
    const property = defaultSchemaProperty(
      defaultFieldDescriptor('severity', {
        listVisible: true,
        filterable: true,
        statistical: true,
      })
    )
    const ext = property['x-work-record'] as Record<string, unknown>
    expect(ext.listVisible).toBe(true)
    expect(ext.filterable).toBe(true)
    expect(ext.statistical).toBe(true)
  })
})

describe('defaultFieldSchema convenience helper', () => {
  it('matches defaultFieldDescriptor + defaultSchemaProperty', () => {
    const fast = defaultFieldSchema('probe', 'number', { title: 'Probe' })
    const explicit = defaultSchemaProperty(
      defaultFieldDescriptor('probe', { fieldType: 'number', title: 'Probe' })
    )
    expect(fast).toEqual(explicit)
  })
})

describe('designerFieldTypes and isSelectLike', () => {
  it('exports the same set as workRecordFieldTypes', () => {
    expect(new Set(designerFieldTypes)).toEqual(
      new Set(workRecordFieldTypes)
    )
  })

  it.each([
    ['select', true],
    ['multi_select', true],
    ['text', false],
    ['number', false],
    ['boolean', false],
    ['date', false],
    ['datetime', false],
    ['textarea', false],
    ['user', false],
  ] as const)('isSelectLike(%s) === %s', (fieldType, expected) => {
    expect(isSelectLike(fieldType)).toBe(expected)
  })
})

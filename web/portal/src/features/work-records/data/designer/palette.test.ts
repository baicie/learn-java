import { describe, expect, it } from 'vitest'
import { workRecordFieldTypes } from '@/features/work-records/data/field-types'
import {
  defaultDescriptorFor,
  defaultFieldSchemaFromPalette,
  designerPalette,
  getFieldTypeOption,
} from '@/features/work-records/data/designer/palette'

describe('designerPalette', () => {
  it('contains exactly the 9 work-record field types', () => {
    expect(designerPalette).toHaveLength(9)
    expect(new Set(designerPalette.map((p) => p.fieldType))).toEqual(
      new Set(workRecordFieldTypes)
    )
  })

  it('has every option with a non-empty i18nKey and descriptionI18nKey', () => {
    for (const option of designerPalette) {
      expect(option.i18nKey).toMatch(/^workRecords\.designer\.palette\./)
      expect(option.descriptionI18nKey).toMatch(
        /^workRecords\.designer\.palette\.[a-zA-Z]+Description$/
      )
    }
  })

  it('keeps the order matching the field-types enum', () => {
    expect(designerPalette.map((p) => p.fieldType)).toEqual([
      ...workRecordFieldTypes,
    ])
  })
})

describe('getFieldTypeOption', () => {
  it.each(workRecordFieldTypes)('returns the option for %s', (fieldType) => {
    const option = getFieldTypeOption(fieldType)
    expect(option.fieldType).toBe(fieldType)
  })

  it('throws on unknown fieldType', () => {
    expect(() =>
      getFieldTypeOption('unknown' as (typeof workRecordFieldTypes)[number])
    ).toThrow('unknown palette fieldType')
  })
})

describe('defaultDescriptorFor', () => {
  it('returns the descriptor with the requested overrides applied', () => {
    const desc = defaultDescriptorFor('select', 'priority', {
      required: true,
      listVisible: true,
    })
    expect(desc).toMatchObject({
      fieldCode: 'priority',
      fieldType: 'select',
      required: true,
      listVisible: true,
    })
  })

  it('locks fieldType and fieldCode even when overrides include them', () => {
    const desc = defaultDescriptorFor('select', 'priority', {
      fieldType: 'text',
      fieldCode: 'something_else',
    })
    expect(desc.fieldType).toBe('select')
    expect(desc.fieldCode).toBe('priority')
  })
})

describe('defaultFieldSchemaFromPalette', () => {
  it('returns descriptor and matching schema', () => {
    const { descriptor, schema } = defaultFieldSchemaFromPalette(
      'number',
      'latency_ms',
      { title: 'Latency (ms)' }
    )
    expect(descriptor.fieldType).toBe('number')
    expect(schema.title).toBe('Latency (ms)')
    expect(schema.type).toBe('number')
  })

  it('keeps select schema consistent', () => {
    const { schema } = defaultFieldSchemaFromPalette('select', 'env')
    expect(schema.type).toBe('string')
  })
})

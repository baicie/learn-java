import { describe, expect, it } from 'vitest'
import { extractWorkRecordFields } from './formily-schema'

describe('extractWorkRecordFields', () => {
  it('extracts work record field metadata', () => {
    const fields = extractWorkRecordFields({
      type: 'object',
      properties: {
        priority: {
          type: 'string',
          title: '优先级',
          required: true,
          'x-work-record': {
            fieldCode: 'priority',
            fieldType: 'select',
            optionSource: 'dict',
            dictCode: 'record_priority',
            listVisible: true,
            filterable: true,
            statistical: false,
          },
        },
      },
    })

    expect(fields).toHaveLength(1)
    expect(fields[0]).toMatchObject({
      fieldCode: 'priority',
      fieldType: 'select',
      dictCode: 'record_priority',
      required: true,
    })
  })

  it('rejects reserved field codes', () => {
    expect(() =>
      extractWorkRecordFields({
        properties: {
          title: {
            title: '标题',
            'x-work-record': { fieldCode: 'title', fieldType: 'text' },
          },
        },
      })
    ).toThrow('reserved fieldCode')
  })
})

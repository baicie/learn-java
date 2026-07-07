import { describe, expect, it } from 'vitest'
import { injectDictionaryOptions } from './dict-schema-injector'

describe('injectDictionaryOptions', () => {
  it('injects dictionary enum into schema', () => {
    const schema = injectDictionaryOptions(
      {
        properties: {
          priority: {
            'x-work-record': {
              optionSource: 'dict',
              dictCode: 'record_priority',
            },
          },
        },
      },
      {
        record_priority: [{ itemLabel: 'P2', itemValue: 'P2' }],
      }
    )

    const priority = schema.properties.priority as { enum?: unknown[] }
    expect(priority.enum).toEqual([
      { label: 'P2', value: 'P2' },
    ])
  })
})

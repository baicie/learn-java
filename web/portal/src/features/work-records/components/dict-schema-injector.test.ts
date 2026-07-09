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
    expect(priority.enum).toEqual([{ label: 'P2', value: 'P2' }])
  })

  // designer-design §10.3a: missing dictMap entry yields an empty enum
  // (explicit design choice — see doc).
  it('writes an empty enum when dict code is missing from dictMap', () => {
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
      {}
    )

    const priority = schema.properties.priority as { enum?: unknown[] }
    expect(priority.enum).toEqual([])
  })

  // designer-design §10.3a: static fields are not touched by the injector.
  it('leaves static enum untouched even when dictMap is provided', () => {
    const staticEnum = [
      { label: 'A', value: 'a' },
      { label: 'B', value: 'b' },
    ]
    const schema = injectDictionaryOptions(
      {
        properties: {
          env: {
            type: 'string',
            enum: staticEnum,
            'x-work-record': {
              optionSource: 'static',
            },
          },
        },
      },
      { record_priority: [{ itemLabel: 'P2', itemValue: 'P2' }] }
    )

    const env = schema.properties.env as { enum?: unknown[] }
    expect(env.enum).toEqual(staticEnum)
  })

  // designer-design §10.3a: dict injector should never mutate the input.
  it('returns a new schema and does not mutate the input', () => {
    const input = {
      properties: {
        priority: {
          'x-work-record': {
            optionSource: 'dict',
            dictCode: 'record_priority',
          },
        },
      },
    }
    const before = JSON.stringify(input)
    injectDictionaryOptions(input, {
      record_priority: [{ itemLabel: 'P2', itemValue: 'P2' }],
    })
    expect(JSON.stringify(input)).toBe(before)
  })

  it('injects enum for each distinct dict code only once', () => {
    const schema = injectDictionaryOptions(
      {
        properties: {
          priority: {
            'x-work-record': {
              optionSource: 'dict',
              dictCode: 'record_priority',
            },
          },
          env: {
            'x-work-record': {
              optionSource: 'dict',
              dictCode: 'record_env',
            },
          },
        },
      },
      {
        record_priority: [{ itemLabel: 'P2', itemValue: 'P2' }],
        record_env: [{ itemLabel: 'prod', itemValue: 'prod' }],
      }
    )

    const priority = schema.properties.priority as unknown as {
      enum?: unknown[]
    }
    const env = schema.properties.env as unknown as { enum?: unknown[] }
    expect(priority.enum).toEqual([{ label: 'P2', value: 'P2' }])
    expect(env.enum).toEqual([{ label: 'prod', value: 'prod' }])
  })
})

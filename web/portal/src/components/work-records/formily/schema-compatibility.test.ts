import { describe, expect, it } from 'vitest'
import { normalizeRuntimeSchema } from './schema-compatibility'

describe('normalizeRuntimeSchema', () => {
  it('upgrades v1 with x-work-record metadata to v2 Formily schema', () => {
    const result = normalizeRuntimeSchema({
      'x-work-record-schema-version': 1,
      properties: {
        summary: {
          type: 'string',
          title: '工作总结',
          'x-work-record': { fieldType: 'textarea' },
        },
        priority: {
          type: 'string',
          title: '优先级',
          'x-work-record': {
            fieldType: 'select',
            optionSource: 'dict',
            dictCode: 'priority',
          },
        },
        hours: {
          type: 'number',
          title: '工作时长',
          'x-work-record': { fieldType: 'number' },
        },
      },
    }) as unknown as { properties?: Record<string, Record<string, unknown>> }

    expect(result.properties?.summary).toMatchObject({
      'x-component': 'Textarea',
      'x-decorator': 'FormItem',
    })
    expect(result.properties?.priority).toMatchObject({
      'x-component': 'DictSelect',
      'x-component-props': { dictCode: 'priority' },
    })
    expect(result.properties?.hours).toMatchObject({
      'x-component': 'NumberInput',
    })
  })

  it('passes v2 through unchanged', () => {
    const v2 = {
      type: 'object',
      'x-work-record-schema-version': 2,
      properties: {
        foo: {
          type: 'string',
          title: 'Foo',
          'x-decorator': 'FormItem',
          'x-component': 'Input',
        },
      },
    }
    const result = normalizeRuntimeSchema(v2) as Record<string, unknown>
    expect(result).toEqual(v2)
  })

  it('throws on unsupported versions', () => {
    expect(() =>
      normalizeRuntimeSchema({
        'x-work-record-schema-version': 99,
        properties: {},
      })
    ).toThrow(/unsupported work-record schema version/)
  })
})

import { describe, expect, it } from 'vitest'
import { extractWorkRecordFields } from '@/features/work-records/data/formily-schema'
import {
  SchemaBuilderError,
  addField,
  emptySchema,
  listFieldCodes,
  moveField,
  moveFieldDown,
  moveFieldUp,
  normalizeSchema,
  removeField,
  setDictionaryCode,
  updateField,
} from '@/features/work-records/data/designer/schema-builder'

function seed(): ReturnType<typeof emptySchema> {
  let schema = emptySchema()
  schema = addField(schema, 'text', 'process_result')
  schema = addField(schema, 'select', 'env', {
    options: [{ label: 'Prod', value: 'prod' }],
  })
  schema = addField(schema, 'number', 'severity')
  return schema
}

describe('normalizeSchema and emptySchema', () => {
  it('produces an empty schema from nullish input', () => {
    const schema = normalizeSchema(null)
    expect(schema.type).toBe('object')
    expect(schema.properties).toEqual({})
  })

  it('validates non-object input', () => {
    expect(() => addField(null as unknown as never, 'text', 'foo')).toThrow(
      SchemaBuilderError
    )
    expect(() =>
      addField([] as unknown as never, 'text', 'foo')
    ).toThrow(SchemaBuilderError)
    expect(() =>
      addField(123 as unknown as never, 'text', 'foo')
    ).toThrow(SchemaBuilderError)
  })

  it('rejects schemas whose type is not object on write paths', () => {
    expect(() => addField({ type: 'string' } as never, 'text', 'foo')).toThrow(
      SchemaBuilderError
    )
  })

  it('normalizeSchema accepts missing/non-object type as object', () => {
    const schema = normalizeSchema({ type: 'string', properties: { foo: {} } })
    expect(schema.type).toBe('object')
    expect(schema.properties).toEqual({ foo: {} })
  })

  it('preserves extra keys at the root', () => {
    const schema = normalizeSchema({
      type: 'object',
      title: 'Probe',
      properties: { foo: {} },
    })
    expect(schema.title).toBe('Probe')
  })
})

describe('addField', () => {
  it('appends a new field at the end', () => {
    const seeded = seed()
    const schema = addField(seeded, 'boolean', 'is_resolved')
    expect(listFieldCodes(schema)).toEqual([
      'process_result',
      'env',
      'severity',
      'is_resolved',
    ])
    const inserted = schema.properties.is_resolved
    expect(inserted.type).toBe('boolean')
    expect(inserted['x-work-record']).toMatchObject({
      fieldCode: 'is_resolved',
      fieldType: 'boolean',
      optionSource: 'static',
    })
  })

  it('rejects duplicates', () => {
    const seeded = seed()
    expect(() => addField(seeded, 'text', 'process_result')).toThrow(
      'fieldCode already exists'
    )
  })

  it('rejects empty codes and reserved codes', () => {
    const seeded = seed()
    expect(() => addField(seeded, 'text', '   ')).toThrow(
      'field code must not be empty'
    )
    expect(() => addField(seeded, 'text', 'title')).toThrow(
      'reserved fieldCode'
    )
  })

  it('attaches enum for select with options', () => {
    const schema = addField(emptySchema(), 'select', 'priority', {
      options: [{ label: 'P0', value: 'P0' }],
    })
    expect(schema.properties.priority.enum).toEqual([
      { label: 'P0', value: 'P0' },
    ])
  })

  it('treats dictCode as required when optionSource=dict', () => {
    expect(() =>
      addField(emptySchema(), 'select', 'priority', {
        optionSource: 'dict',
      })
    ).toThrow('dictCode is required')
  })
})

describe('removeField', () => {
  it('removes the field and keeps the rest in original order', () => {
    const seeded = seed()
    const schema = removeField(seeded, 'env')
    expect(listFieldCodes(schema)).toEqual(['process_result', 'severity'])
  })

  it('throws when the field does not exist', () => {
    expect(() => removeField(seed(), 'missing')).toThrow(
      'fieldCode not found'
    )
  })
})

describe('reorder / moveField helpers', () => {
  it('moves a field to a specific target index', () => {
    const seeded = seed()
    const moved = moveField(seeded, 'severity', 0)
    expect(listFieldCodes(moved)).toEqual([
      'severity',
      'process_result',
      'env',
    ])
  })

  it('moveFieldUp moves the field one slot up', () => {
    const seeded = seed()
    const moved = moveFieldUp(seeded, 'env')
    expect(listFieldCodes(moved)).toEqual([
      'env',
      'process_result',
      'severity',
    ])
  })

  it('moveFieldDown moves the field one slot down', () => {
    const seeded = seed()
    const moved = moveFieldDown(seeded, 'env')
    expect(listFieldCodes(moved)).toEqual([
      'process_result',
      'severity',
      'env',
    ])
  })

  it('rejects out-of-range moveField', () => {
    const seeded = seed()
    expect(() => moveField(seeded, 'severity', 5)).toThrow(
      'targetIndex out of range'
    )
  })

  it('moveField no-op keeps order and yields a structurally equal schema', () => {
    const seeded = seed()
    const moved = moveField(seeded, 'env', 1)
    expect(listFieldCodes(moved)).toEqual(listFieldCodes(seeded))
    expect(moved).toEqual(seeded)
  })

  it('moveFieldUp on the first field keeps order', () => {
    const seeded = seed()
    const moved = moveFieldUp(seeded, 'process_result')
    expect(moved).toEqual(seeded)
    expect(listFieldCodes(moved)).toEqual(['process_result', 'env', 'severity'])
  })

  it('moveFieldDown on the last field keeps order', () => {
    const seeded = seed()
    const moved = moveFieldDown(seeded, 'severity')
    expect(moved).toEqual(seeded)
    expect(listFieldCodes(moved)).toEqual(['process_result', 'env', 'severity'])
  })
})

describe('updateField', () => {
  it('patches the partial descriptor without touching other fields', () => {
    const seeded = seed()
    const schema = updateField(seeded, 'env', {
      title: 'Environment',
      required: true,
      listVisible: true,
    })

    const env = schema.properties.env
    expect(env.title).toBe('Environment')
    expect(env.required).toBe(true)
    expect(env['x-work-record']).toMatchObject({
      fieldType: 'select',
      listVisible: true,
    })

    // other fields are intact
    expect(schema.properties.process_result.title).toBe('process_result')
    expect(schema.properties.severity).toEqual(seeded.properties.severity)
  })

  it('renames a field and locks the new name if the field has no records', () => {
    const seeded = seed()
    const schema = updateField(seeded, 'process_result', {
      fieldCode: 'result',
      title: 'Result',
    })
    expect(listFieldCodes(schema)).toEqual(['env', 'severity', 'result'])
    expect(schema.properties.result['x-work-record']).toMatchObject({
      fieldCode: 'result',
    })
  })

  it('rejects renaming to an existing fieldCode', () => {
    const seeded = seed()
    expect(() =>
      updateField(seeded, 'process_result', { fieldCode: 'env' })
    ).toThrow(/cannot rename/)
  })

  it('rejects renaming to a reserved code', () => {
    const seeded = seed()
    expect(() =>
      updateField(seeded, 'process_result', { fieldCode: 'title' })
    ).toThrow('reserved fieldCode')
  })
})

describe('setDictionaryCode', () => {
  it('attaches dictCode for a select field', () => {
    const seeded = seed()
    const schema = setDictionaryCode(seeded, 'env', 'record_env')
    const ext = schema.properties.env['x-work-record'] as Record<string, unknown>
    expect(ext.optionSource).toBe('dict')
    expect(ext.dictCode).toBe('record_env')
  })

  it('attaches dictCode for a multi_select field', () => {
    const seeded = seed()
    const seeded2 = addField(seeded, 'multi_select', 'tags')
    const schema = setDictionaryCode(seeded2, 'tags', 'record_tags')
    expect(
      (schema.properties.tags['x-work-record'] as Record<string, unknown>)
        .dictCode
    ).toBe('record_tags')
  })

  it('clears dictCode when set to null', () => {
    const seeded = seed()
    const attached = setDictionaryCode(seeded, 'env', 'record_env')
    const cleared = setDictionaryCode(attached, 'env', null)
    const ext = cleared.properties.env['x-work-record'] as Record<string, unknown>
    expect(ext.optionSource).toBe('static')
    expect(ext.dictCode).toBeUndefined()
  })

  it('rejects dictCode on a non-select field', () => {
    const seeded = seed()
    expect(() => setDictionaryCode(seeded, 'severity', 'record_severity')).toThrow(
      /select or multi_select/
    )
  })
})

describe('immutability', () => {
  it('returns a new object for every mutation', () => {
    const seeded = seed()
    const snapshot = JSON.stringify(seeded)

    const a = addField(seeded, 'text', 'note')
    expect(JSON.stringify(seeded)).toBe(snapshot)
    expect(a).not.toBe(seeded)
    expect(a.properties).not.toBe(seeded.properties)

    const b = removeField(seeded, 'env')
    expect(JSON.stringify(seeded)).toBe(snapshot)
    expect(b).not.toBe(seeded)

    const c = updateField(seeded, 'env', { title: 'Stage' })
    expect(JSON.stringify(seeded)).toBe(snapshot)
    expect(c).not.toBe(seeded)
    expect(c.properties).not.toBe(seeded.properties)
  })
})

describe('integration with extractWorkRecordFields', () => {
  it('built schema can be consumed by the runtime pipeline', () => {
    const schema = addField(emptySchema(), 'select', 'priority', {
      optionSource: 'dict',
      dictCode: 'record_priority',
      listVisible: true,
      filterable: true,
      required: true,
    })
    const fields = extractWorkRecordFields(schema)
    expect(fields).toHaveLength(1)
    expect(fields[0]).toMatchObject({
      fieldCode: 'priority',
      fieldType: 'select',
      dictCode: 'record_priority',
      required: true,
    })
  })

  it('throws on dictCode without source', () => {
    expect(() =>
      addField(emptySchema(), 'select', 'priority', {
        optionSource: 'dict',
      })
    ).toThrow(SchemaBuilderError)
  })
})

import { describe, expect, it } from 'vitest'
import {
  buildWorkRecordSchema,
  diffFields,
  mergePublishedLocks,
  newDesignerField,
  parseDraftSchema,
  validateDesignerFields,
} from './schema'
import type { DesignerField, WorkRecordVersionField } from './types'

describe('work record designer schema', () => {
  it('builds nested x-work-record schema', () => {
    const field: DesignerField = {
      ...newDesignerField('select', 0),
      fieldName: '优先级',
      fieldCode: 'priority',
      optionSource: 'dict',
      dictCode: 'record_priority',
      listVisible: true,
      filterable: true,
      exportable: true,
      statistical: true,
    }

    const schema = buildWorkRecordSchema([field]) as {
      [key: string]: unknown
      properties: Record<string, { [key: string]: unknown }>
    }

    expect(schema['x-work-record-schema-version']).toBe(1)
    expect(schema.properties.priority['x-work-record']).toMatchObject({
      fieldCode: 'priority',
      fieldType: 'select',
      optionSource: 'dict',
      dictCode: 'record_priority',
      listVisible: true,
      filterable: true,
      exportable: true,
      statistical: true,
    })
  })

  it('validates field code and dict binding', () => {
    const badCode: DesignerField = {
      ...newDesignerField('text', 0),
      fieldCode: '1bad',
    }

    expect(validateDesignerFields([badCode]).join('\n')).toContain(
      '字段编码不符合规则'
    )

    const dictMissing: DesignerField = {
      ...newDesignerField('select', 0),
      fieldCode: 'priority',
      optionSource: 'dict',
      dictCode: '',
    }

    expect(validateDesignerFields([dictMissing]).join('\n')).toContain(
      '没有选择字典'
    )
  })

  it('parses draft schema into designer fields', () => {
    const fields = parseDraftSchema(
      JSON.stringify({
        type: 'object',
        required: ['priority'],
        properties: {
          priority: {
            title: '优先级',
            'x-work-record': {
              fieldCode: 'priority',
              fieldType: 'select',
              optionSource: 'dict',
              dictCode: 'record_priority',
              listVisible: true,
              filterable: true,
              exportable: true,
              statistical: false,
              sortOrder: 0,
            },
          },
        },
      })
    )

    expect(fields).toHaveLength(1)
    expect(fields[0]).toMatchObject({
      fieldName: '优先级',
      fieldCode: 'priority',
      fieldType: 'select',
      required: true,
      optionSource: 'dict',
      dictCode: 'record_priority',
    })
  })

  it('locks published fields and appends missing published fields as disabled', () => {
    const draft: DesignerField[] = [
      {
        ...newDesignerField('textarea', 0),
        fieldCode: 'content',
        fieldName: '内容',
      },
    ]

    const published: WorkRecordVersionField[] = [
      versionField('priority', 'select'),
    ]

    const merged = mergePublishedLocks(draft, published, 3)

    expect(merged.some((field) => field.fieldCode === 'priority')).toBe(true)
    expect(
      merged.find((field) => field.fieldCode === 'priority')?.enabled
    ).toBe(false)
    expect(merged.find((field) => field.fieldCode === 'priority')?.locked).toBe(
      true
    )
  })

  it('diffs added and changed fields', () => {
    const previous = [versionField('priority', 'select')]
    const next: DesignerField[] = [
      {
        ...newDesignerField('text', 0),
        fieldCode: 'priority',
        fieldType: 'text',
      },
      {
        ...newDesignerField('textarea', 1),
        fieldCode: 'content',
      },
    ]

    const diff = diffFields(previous, next)

    expect(diff.map((item) => item.type)).toContain('type_changed')
    expect(diff.map((item) => item.type)).toContain('added')
  })
})

function versionField(
  code: string,
  fieldType: WorkRecordVersionField['fieldType']
): WorkRecordVersionField {
  return {
    id: `f-${code}`,
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    fieldName: code,
    fieldCode: code,
    fieldType,
    required: false,
    defaultValue: null,
    optionSource: 'static',
    dictCode: null,
    optionsJson: '[]',
    schemaPath: `.properties.${code}`,
    listVisible: true,
    filterable: true,
    exportable: true,
    statistical: false,
    sortOrder: 0,
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

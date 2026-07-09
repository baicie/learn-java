import { describe, expect, it } from 'vitest'
import {
  buildInitialFormValue,
  fieldDisplayValue,
  setCustomValue,
  validateRuntimeForm,
} from './schema'
import type { RuntimeDictOptions, WorkRecordField } from './types'

describe('work record runtime schema', () => {
  it('builds initial value from record', () => {
    const value = buildInitialFormValue({
      record: {
        id: 'r1',
        tenantId: 't1',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        title: '日报',
        status: 'draft',
        ownerId: 'u1',
        creatorId: 'u1',
        recordTime: '2026-01-01T00:00:00Z',
        builtinDataJson: '{}',
        customDataJson: '{"content":"hello"}',
        rowVersion: 1,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        deletedAt: null,
      },
    })

    expect(value.title).toBe('日报')
    expect(value.customData.content).toBe('hello')
  })

  it('validates required dynamic fields', () => {
    const result = validateRuntimeForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'draft',
        ownerId: '',
        recordTime: '2026-01-01T00:00',
        customData: {},
      },
      [field('content', 'textarea', true)]
    )

    expect(result.valid).toBe(false)
    expect(result.errors.join('\n')).toContain('内容 不能为空')
  })

  it('sets custom values immutably', () => {
    const next = setCustomValue(
      {
        title: '',
        templateId: '',
        templateVersionId: '',
        status: 'draft',
        ownerId: '',
        recordTime: '',
        customData: {},
      },
      'content',
      'hello'
    )

    expect(next.customData.content).toBe('hello')
  })

  it('renders disabled dict item for history display', () => {
    const dictOptions: RuntimeDictOptions = {
      record_priority: [
        {
          id: 'p1',
          itemLabel: 'P1',
          itemValue: 'P1',
          color: null,
          enabled: false,
        },
      ],
    }

    const text = fieldDisplayValue(
      {
        ...field('priority', 'select', false),
        optionSource: 'dict',
        dictCode: 'record_priority',
      },
      'P1',
      dictOptions
    )

    expect(text).toBe('P1（已禁用）')
  })
})

function field(
  code: string,
  fieldType: WorkRecordField['fieldType'],
  required: boolean
): WorkRecordField {
  return {
    id: `f-${code}`,
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    fieldName: code === 'content' ? '内容' : code,
    fieldCode: code,
    fieldType,
    required,
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

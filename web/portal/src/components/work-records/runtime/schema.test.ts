import { describe, expect, it } from 'vitest'
import { validateRecordForm } from './record-form-validation'
import {
  buildInitialFormValue,
  fieldDisplayValue,
  hasMeaningfulCustomData,
  sanitizeCustomDataForSubmit,
  setCustomValue,
} from './schema'
import type { RuntimeDictOptions, WorkRecordField } from './types'

describe('work record runtime schema', () => {
  it('chooses the first available template for a new record', () => {
    const value = buildInitialFormValue({
      templates: [
        { id: 'tpl-first', currentVersionId: 'v1' },
        { id: 'tpl-second', currentVersionId: 'v2' },
      ],
    })

    expect(value.templateId).toBe('tpl-first')
    expect(value.templateVersionId).toBe('v1')
  })

  it('prefers the tenant default template for a new record', () => {
    const value = buildInitialFormValue({
      templates: [
        { id: 'tpl-first', currentVersionId: 'v1', isDefault: false },
        { id: 'tpl-default', currentVersionId: 'v2', isDefault: true },
      ],
    })

    expect(value.templateId).toBe('tpl-default')
    expect(value.templateVersionId).toBe('v2')
  })

  it('only treats non-empty dynamic values as meaningful', () => {
    expect(hasMeaningfulCustomData({ content: '', tags: [], note: null })).toBe(
      false
    )
    expect(hasMeaningfulCustomData({ content: '已填写' })).toBe(true)
  })

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
    const result = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'draft',
        ownerId: '',
        recordTime: '2026-01-01T00:00',
        customData: {},
      },
      [field('content', 'textarea', true)],
      'done'
    )

    expect(result['custom.content']).toBe('请填写内容')
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

  it('rejects invalid record time', () => {
    const result = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'draft',
        ownerId: '',
        recordTime: 'bad-time',
        customData: {},
      },
      [],
      'done'
    )

    expect(result.recordTime).toBe('记录时间格式无效')
  })

  it('sanitizes disabled and unknown custom data before submit', () => {
    const value = sanitizeCustomDataForSubmit(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'draft',
        ownerId: '',
        recordTime: '2026-01-01T00:00',
        customData: {
          content: 'hello',
          disabledField: 'old',
          unknown: 'bad',
        },
      },
      [
        {
          id: 'f-content',
          tenantId: 't1',
          templateId: 'tpl1',
          templateVersionId: 'v1',
          fieldName: '内容',
          fieldCode: 'content',
          fieldType: 'textarea',
          required: false,
          defaultValue: null,
          optionSource: 'static',
          dictCode: null,
          optionsJson: '[]',
          schemaPath: '.properties.content',
          listVisible: true,
          filterable: true,
          exportable: true,
          statistical: false,
          sortOrder: 0,
          enabled: true,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
        {
          id: 'f-disabled',
          tenantId: 't1',
          templateId: 'tpl1',
          templateVersionId: 'v1',
          fieldName: '旧字段',
          fieldCode: 'disabledField',
          fieldType: 'text',
          required: false,
          defaultValue: null,
          optionSource: 'static',
          dictCode: null,
          optionsJson: '[]',
          schemaPath: '.properties.disabledField',
          listVisible: true,
          filterable: true,
          exportable: true,
          statistical: false,
          sortOrder: 1,
          enabled: false,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ]
    )

    expect(value.customData).toEqual({ content: 'hello' })
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

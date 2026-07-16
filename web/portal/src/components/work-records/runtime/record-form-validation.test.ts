import { describe, expect, it } from 'vitest'
import { validateRecordForm } from './record-form-validation'
import type { WorkRecordField, WorkRecordRuntimeFormValue } from './types'

function buildFields(): WorkRecordField[] {
  return [
    {
      id: 'f-summary',
      tenantId: 't1',
      templateId: 'tpl1',
      templateVersionId: 'v1',
      fieldName: '工作总结',
      fieldCode: 'summary',
      fieldType: 'textarea',
      required: true,
      defaultValue: null,
      optionSource: 'static',
      dictCode: null,
      optionsJson: '[]',
      schemaPath: '.properties.summary',
      listVisible: true,
      filterable: false,
      exportable: true,
      statistical: false,
      sortOrder: 0,
      enabled: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ]
}

describe('validateRecordForm', () => {
  it('allows incomplete dynamic fields when saving as draft', () => {
    const errors = validateRecordForm(
      {
        title: '草稿标题',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'draft',
        ownerId: '',
        recordTime: '2026-07-10T10:00',
        customData: {},
      } satisfies WorkRecordRuntimeFormValue,
      buildFields(),
      'draft'
    )

    expect(errors['custom.summary']).toBeUndefined()
    expect(errors.title).toBeUndefined()
  })

  it('blocks submission when required fields are missing', () => {
    const errors = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'done',
        ownerId: '',
        recordTime: '2026-07-10T10:00',
        customData: {},
      } satisfies WorkRecordRuntimeFormValue,
      buildFields(),
      'done'
    )

    expect(errors['custom.summary']).toBe('请填写工作总结')
  })

  it('rejects invalid datetime and empty builtin values', () => {
    const errors = validateRecordForm(
      {
        title: '',
        templateId: '',
        templateVersionId: '',
        status: 'done',
        ownerId: '',
        recordTime: 'bad-time',
        customData: {},
      } satisfies WorkRecordRuntimeFormValue,
      [],
      'done'
    )

    expect(errors).toEqual(
      expect.objectContaining({
        templateId: '请选择记录模板',
        templateVersionId: '请选择模板版本',
        title: '请输入记录标题',
        recordTime: '记录时间格式无效',
      })
    )
  })

  it('validates runtime value types', () => {
    const errors = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'done',
        ownerId: '',
        recordTime: '2026-07-10T10:00',
        customData: {
          numberField: 'not-a-number',
          boolField: 'not-a-bool',
          isoDate: 'bad-date',
          offsetDate: '2026-01-01T00:00:00+08:00',
          multiField: 'not-an-array',
        },
      } satisfies WorkRecordRuntimeFormValue,
      [
        {
          ...buildFields()[0],
          fieldCode: 'numberField',
          fieldName: '数字',
          fieldType: 'number',
          required: false,
        },
        {
          ...buildFields()[0],
          fieldCode: 'boolField',
          fieldName: '开关',
          fieldType: 'boolean',
          required: false,
        },
        {
          ...buildFields()[0],
          fieldCode: 'isoDate',
          fieldName: '日期',
          fieldType: 'date',
          required: false,
        },
        {
          ...buildFields()[0],
          fieldCode: 'offsetDate',
          fieldName: '日期时间',
          fieldType: 'datetime',
          required: false,
        },
        {
          ...buildFields()[0],
          fieldCode: 'multiField',
          fieldName: '多选',
          fieldType: 'multi_select',
          required: false,
        },
      ],
      'done'
    )

    expect(errors['custom.numberField']).toBe('数字必须是数字')
    expect(errors['custom.boolField']).toBe('开关必须是布尔值')
    expect(errors['custom.isoDate']).toBe('日期必须是有效日期')
    expect(errors['custom.offsetDate']).toBeUndefined()
    expect(errors['custom.multiField']).toBe('多选格式无效')
  })

  it('applies versioned text validation rules', () => {
    const errors = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'done',
        ownerId: '',
        recordTime: '2026-07-10T10:00',
        customData: { summary: 'ab' },
      },
      [
        {
          ...buildFields()[0],
          validationJson: JSON.stringify({ minLength: 3, maxLength: 10 }),
        },
      ],
      'done'
    )

    expect(errors['custom.summary']).toBe('工作总结不能少于 3 个字符')
  })

  it('applies versioned number range validation rules', () => {
    const errors = validateRecordForm(
      {
        title: '日报',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        status: 'done',
        ownerId: '',
        recordTime: '2026-07-10T10:00',
        customData: { summary: 25 },
      },
      [
        {
          ...buildFields()[0],
          fieldType: 'number',
          validationJson: JSON.stringify({ minimum: 0, maximum: 24 }),
        },
      ],
      'done'
    )

    expect(errors['custom.summary']).toBe('工作总结不能大于 24')
  })
})

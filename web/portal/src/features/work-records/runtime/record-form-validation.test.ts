import { describe, expect, it } from 'vitest'
import {
  type RecordFormValue,
  type ValidatableRecordField,
  validateRecordForm,
} from './record-form-validation'

const fields: ValidatableRecordField[] = [
  {
    fieldCode: 'summary',
    fieldName: '工作总结',
    fieldType: 'textarea',
    required: true,
    enabled: true,
  },
]

describe('validateRecordForm', () => {
  it('allows incomplete dynamic fields for draft', () => {
    const errors = validateRecordForm(
      {
        templateId: 'tpl1',
        templateVersionId: 'v1',
        title: '草稿',
        status: 'draft',
        recordTime: '2026-07-10T10:00:00+08:00',
        customData: {},
      } satisfies RecordFormValue,
      fields
    )

    expect(errors['custom.summary']).toBeUndefined()
  })

  it('requires dynamic fields outside draft', () => {
    const errors = validateRecordForm(
      {
        templateId: 'tpl1',
        templateVersionId: 'v1',
        title: '日报',
        status: 'done',
        recordTime: '2026-07-10T10:00:00+08:00',
        customData: {},
      } satisfies RecordFormValue,
      fields
    )

    expect(errors['custom.summary']).toBe('请填写工作总结')
  })

  it('validates builtin values', () => {
    const errors = validateRecordForm(
      {
        templateId: '',
        templateVersionId: '',
        title: '',
        status: 'done',
        recordTime: '',
        customData: {},
      } satisfies RecordFormValue,
      []
    )

    expect(errors).toEqual(
      expect.objectContaining({
        templateId: '请选择记录模板',
        templateVersionId: '请选择模板版本',
        title: '请输入记录标题',
        recordTime: '请选择记录时间',
      })
    )
  })
})

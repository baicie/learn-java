import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { RecordRuntimeForm } from './record-runtime-form'
import type {
  RuntimeDictOptions,
  WorkRecordField,
  WorkRecordRuntimeFormValue,
  WorkRecordTemplate,
} from './types'

describe('RecordRuntimeForm', () => {
  it('renders base fields and dynamic fields', async () => {
    const screen = await render(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('content', 'textarea', true)]}
        dictOptions={{}}
        value={value({ content: 'hello' })}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await expect.element(screen.getByText('新建工作记录')).toBeVisible()
    await expect.element(screen.getByText('标题 *')).toBeVisible()
    await expect.element(screen.getByText('内容')).toBeVisible()
  })

  it('shows validation errors before submit', async () => {
    const screen = await render(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('content', 'textarea', true)]}
        dictOptions={{}}
        value={value({})}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await expect.element(screen.getByText('提交前校验失败')).toBeVisible()
    await expect.element(screen.getByText('内容 不能为空')).toBeVisible()
  })

  it('renders dict options including disabled items', async () => {
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

    const screen = await render(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[
          {
            ...field('priority', 'select', false),
            optionSource: 'dict',
            dictCode: 'record_priority',
          },
        ]}
        dictOptions={dictOptions}
        value={value({ priority: 'P1' })}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    const options = screen.getByRole('option', { name: 'P1（已禁用）' })
    await expect.element(options).toBeInTheDocument()
  })
})

function value(
  customData: Record<string, unknown>
): WorkRecordRuntimeFormValue {
  return {
    title: '日报',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    status: 'draft',
    ownerId: 'u1',
    recordTime: '2026-01-01T00:00',
    customData,
  }
}

function template(): WorkRecordTemplate {
  return {
    id: 'tpl1',
    tenantId: 't1',
    code: 'daily',
    name: '日报模板',
    description: null,
    status: 'published',
    enabled: true,
    currentVersionId: 'v1',
    draftSchemaJson: '{}',
    draftDesignerJson: '{}',
    createdBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }
}

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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { RecordRuntimeForm } from './record-runtime-form'
import type {
  RuntimeDictOptions,
  WorkRecordField,
  WorkRecordRuntimeFormValue,
  WorkRecordTemplate,
} from './types'

function withProviders(node: React.ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <ConfirmProvider>{node}</ConfirmProvider>
      </I18nextProvider>
    </QueryClientProvider>
  )
}

describe('RecordRuntimeForm', () => {
  it('renders base fields and dynamic fields', async () => {
    const screen = await withProviders(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('content', 'textarea', true)]}
        dictOptions={{}}
        value={value({ content: 'hello' })}
        errors={{}}
        dirty={false}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await expect.element(screen.getByText('新建记录')).toBeVisible()
    await expect.element(screen.getByText('工作内容')).toBeVisible()
  })

  it('renders summary error card and per-field errors', async () => {
    const screen = await withProviders(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('content', 'textarea', true)]}
        dictOptions={{}}
        value={value({})}
        errors={{
          'custom.content': '请填写工作内容',
          title: '请输入记录标题',
        }}
        dirty
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await expect.element(screen.getByText('请修正以下内容')).toBeVisible()
    await expect
      .element(screen.getByText('请修正表单中的错误后再提交'))
      .toBeVisible()
  })

  it('hides disabled dictionary items when creating a record', async () => {
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

    const screen = await withProviders(
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
        value={value({})}
        errors={{}}
        dirty={false}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await screen.getByRole('combobox', { name: 'priority' }).click()
    await expect
      .element(screen.getByRole('option', { name: 'P1（已禁用）' }))
      .not.toBeInTheDocument()
  })

  it('does not expose the select placeholder as an option', async () => {
    const screen = await withProviders(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('priority', 'select', false)]}
        dictOptions={{}}
        value={value({})}
        errors={{}}
        dirty={false}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    await screen.getByRole('combobox', { name: 'priority' }).click()
    await expect
      .element(screen.getByRole('option', { name: '请选择' }))
      .not.toBeInTheDocument()
  })

  it('separately enables save draft and submit done', async () => {
    const onSaveDraft = vi.fn()
    const onSubmitDone = vi.fn()

    const screen = await withProviders(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[field('content', 'textarea', true)]}
        dictOptions={{}}
        value={value({})}
        errors={{ 'custom.content': '请填写工作内容' }}
        dirty
        submitting={false}
        onChange={vi.fn()}
        onSaveDraft={onSaveDraft}
        onSubmitDone={onSubmitDone}
        onCancel={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '保存草稿' }).click()
    expect(onSaveDraft).toHaveBeenCalled()

    await screen.getByRole('button', { name: '提交' }).click()
    expect(onSubmitDone).toHaveBeenCalled()
  })

  it('renders half-width fields in a responsive two-column grid', async () => {
    const screen = await withProviders(
      <RecordRuntimeForm
        mode='create'
        templates={[template()]}
        fields={[
          { ...field('summary', 'text', false), columnSpan: 1 },
          { ...field('hours', 'number', false), columnSpan: 1 },
        ]}
        dictOptions={{}}
        value={value({})}
        errors={{}}
        dirty={false}
        onChange={vi.fn()}
        onSaveDraft={vi.fn()}
        onSubmitDone={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    const summary = screen
      .getByText('summary')
      .element()
      .closest('[data-field-id]')
    expect(summary?.className).toContain('md:col-span-1')
    expect(summary?.parentElement?.className).toContain('md:grid-cols-2')
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
    fieldName: code === 'content' ? '工作内容' : code,
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

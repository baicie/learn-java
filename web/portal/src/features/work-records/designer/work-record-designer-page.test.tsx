import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordDesignerPage } from './work-record-designer-page'

vi.mock('./api', () => ({
  listTemplates: async () => [
    {
      id: 'tpl1',
      tenantId: 't1',
      code: 'daily',
      name: '日报',
      description: null,
      status: 'draft',
      enabled: true,
      currentVersionId: null,
      draftSchemaJson: JSON.stringify({
        type: 'object',
        properties: {
          content: {
            title: '工作内容',
            'x-work-record': {
              fieldCode: 'content',
              fieldType: 'textarea',
              optionSource: 'static',
              listVisible: true,
              filterable: true,
              exportable: true,
              statistical: false,
              sortOrder: 0,
            },
          },
        },
      }),
      draftDesignerJson: '{}',
      createdBy: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      deletedAt: null,
    },
  ],
  listTemplateVersionFields: async () => [],
  saveTemplateDraft: async (
    _templateId: string,
    input: { schemaJson: string; designerJson: string }
  ) => ({
    id: 'tpl1',
    tenantId: 't1',
    code: 'daily',
    name: '日报',
    description: null,
    status: 'draft',
    enabled: true,
    currentVersionId: null,
    draftSchemaJson: input.schemaJson,
    draftDesignerJson: input.designerJson,
    createdBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }),
  validateTemplatePublish: async () => ({
    valid: true,
    schemaVersion: 1,
    fieldCount: 1,
    referencedRecordCount: 0,
    errors: [],
    warnings: [],
  }),
  publishTemplate: async () => ({
    id: 'v1',
    tenantId: 't1',
    templateId: 'tpl1',
    versionNo: 1,
    versionName: 'v1',
    schemaJson: '{}',
    designerJson: '{}',
    fieldIndexJson: '[]',
    publishedBy: 'u1',
    publishedAt: '2026-01-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
  }),
}))

vi.mock('@/features/dictionaries/api', () => ({
  listDictTypes: async () => [
    {
      id: 'dict1',
      tenantId: 't1',
      dictCode: 'record_priority',
      dictName: '记录优先级',
      description: null,
      systemBuiltin: true,
      enabled: true,
      sortOrder: 0,
      createdBy: 'system',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ],
}))

describe('WorkRecordDesignerPage', () => {
  it('renders designer layout', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <WorkRecordDesignerPage />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('工作记录表单设计器')).toBeVisible()
    await expect
      .element(screen.getByText('字段库', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('表单画布', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('属性面板', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('实时预览', { exact: true }))
      .toBeVisible()
  })
})

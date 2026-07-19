import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { notify } from '@/components/feedback/app-toaster'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { WorkRecordDesignerPage } from './work-record-designer-page'

const calls: string[] = []

vi.mock('@/components/feedback/app-toaster', () => ({
  notify: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/api/work-records/templates', () => ({
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
  ) => {
    calls.push('save')
    return {
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
    }
  },
  validateTemplatePublish: async () => {
    calls.push('validate')
    return {
      valid: true,
      schemaVersion: 1,
      fieldCount: 1,
      referencedRecordCount: 0,
      errors: [],
      warnings: [],
    }
  },
  publishTemplate: async () => {
    calls.push('publish')
    return {
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
    }
  },
}))

vi.mock('@/api/dictionaries', () => ({
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

vi.mock('@/hooks/use-unsaved-changes-guard', () => ({
  useUnsavedChangesGuard: () => undefined,
}))

vi.mock('@tanstack/react-router', () => ({
  useBlocker: () => ({ status: 'idle', proceed: vi.fn(), reset: vi.fn() }),
  useNavigate: () => vi.fn(async () => undefined),
  Link: ({
    children,
    ...props
  }: {
    children: React.ReactNode
  } & React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a {...props}>{children}</a>
  ),
}))

describe('WorkRecordDesignerPage', () => {
  it('does not fall back to the first template when the route id is unknown', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WorkRecordDesignerPage templateId='missing' />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('暂无模板')).toBeVisible()
  })

  it('renders designer layout', async () => {
    calls.length = 0
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WorkRecordDesignerPage templateId='tpl1' />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('表单设计')).toBeVisible()
    await expect
      .element(screen.getByText('字段库', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('字段画布', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('字段属性', { exact: true }))
      .toBeVisible()
    await expect.element(screen.getByText('运行时预览（只读）')).toBeVisible()
  })

  it('publishes by saving current draft first', async () => {
    calls.length = 0

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WorkRecordDesignerPage templateId='tpl1' />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('表单设计')).toBeVisible()
    await screen.getByText('发布', { exact: true }).click()

    await vi.waitFor(() => {
      expect(calls).toEqual(['save', 'validate', 'publish'])
      expect(notify.success).toHaveBeenCalledWith('模板发布成功')
    })
  })
})

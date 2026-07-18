import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { WorkRecordTemplatesPage } from './templates'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(async () => undefined),
  Link: ({
    children,
    params,
  }: {
    children: React.ReactNode
    params: { templateId: string }
  }) => (
    <a href={`/work-records/templates/${params.templateId}/designer`}>
      {children}
    </a>
  ),
}))

vi.mock('@/api/work-records/templates', () => ({
  listTemplates: async () => [
    {
      id: 'tpl-1',
      tenantId: 'tenant-1',
      code: 'daily',
      name: '日报模板',
      status: 'published',
      enabled: true,
      currentVersionId: 'version-1',
      draftSchemaJson: '{}',
      draftDesignerJson: '{}',
      createdBy: 'user-1',
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
    },
  ],
  listTemplateVersions: async () => [],
  listTemplateVersionFields: async () => [],
  createTemplate: vi.fn(),
  updateTemplate: vi.fn(),
  copyTemplate: vi.fn(),
  enableTemplate: vi.fn(),
  disableTemplate: vi.fn(),
  archiveTemplate: vi.fn(),
  setDefaultTemplate: vi.fn(),
}))

describe('WorkRecordTemplatesPage', () => {
  it('lists templates and exposes management actions', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'writer',
      tenantId: 'tenant-1',
      username: 'writer',
      displayName: 'Writer',
      roles: [],
      permissions: ['work-record:template:read', 'work-record:template:write'],
      dataScopes: {},
    })
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WorkRecordTemplatesPage />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect
      .element(screen.getByRole('heading', { name: '模板管理' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('cell', { name: '日报模板' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '新建模板' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '编辑' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '复制' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '版本' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '设为默认' }))
      .toBeVisible()
    const designerLink = screen.getByRole('link', { name: '设计' })
    await expect.element(designerLink).toBeVisible()
    await expect
      .element(designerLink)
      .toHaveAttribute('href', '/work-records/templates/tpl-1/designer')
  })

  it('hides the designer entry from template readers', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'reader',
      tenantId: 'tenant-1',
      username: 'reader',
      displayName: 'Reader',
      roles: [],
      permissions: ['work-record:template:read'],
      dataScopes: {},
    })

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmProvider>
          <WorkRecordTemplatesPage />
        </ConfirmProvider>
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('日报模板')).toBeVisible()
    await expect
      .element(screen.getByRole('link', { name: '设计' }))
      .not.toBeInTheDocument()
    await expect
      .element(screen.getByRole('button', { name: '版本' }))
      .toBeVisible()
  })
})

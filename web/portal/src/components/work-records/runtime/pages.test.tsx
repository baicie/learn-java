import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { DetailRecordPage } from './detail-record-page'
import { EditRecordPage } from './edit-record-page'
import { NewRecordPage } from './new-record-page'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(async () => undefined),
  useParams: () => ({ recordId: 'r1' }),
  useBlocker: () => ({ status: 'idle', proceed: vi.fn(), reset: vi.fn() }),
}))

vi.mock('@/api/work-records/records', () => ({
  listPublishedTemplates: async () => [
    {
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
    },
  ],
  listTemplateVersionFields: async () => [
    {
      id: 'f-content',
      tenantId: 't1',
      templateId: 'tpl1',
      templateVersionId: 'v1',
      fieldName: '工作内容',
      fieldCode: 'content',
      fieldType: 'textarea',
      required: true,
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
  ],
  getWorkRecord: async () => ({
    id: 'r1',
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    title: '日报',
    status: 'done',
    ownerId: 'u1',
    creatorId: 'u1',
    recordTime: '2026-01-01T00:00:00Z',
    builtinDataJson: '{}',
    customDataJson: '{"content":"hello"}',
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }),
  createWorkRecord: async () => ({
    id: 'r1',
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    title: '日报',
    status: 'done',
    ownerId: 'u1',
    creatorId: 'u1',
    recordTime: '2026-01-01T00:00:00Z',
    builtinDataJson: '{}',
    customDataJson: '{"content":"hello"}',
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }),
  updateWorkRecord: async () => ({
    id: 'r1',
    tenantId: 't1',
    templateId: 'tpl1',
    templateVersionId: 'v1',
    title: '日报',
    status: 'done',
    ownerId: 'u1',
    creatorId: 'u1',
    recordTime: '2026-01-01T00:00:00Z',
    builtinDataJson: '{}',
    customDataJson: '{"content":"hello"}',
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }),
  listWorkRecordHistory: async () => [],
  parseCustomData: (record?: { customDataJson?: string }) => {
    if (!record?.customDataJson) return {}
    try {
      const parsed = JSON.parse(record.customDataJson)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed
        : {}
    } catch {
      return {}
    }
  },
  toLocalDateTimeInput: () => '2026-01-01T00:00',
  toOffsetDateTime: () => '2026-01-01T00:00:00.000Z',
}))

vi.mock('@/api/dictionaries', () => ({
  listDictItems: async () => [],
  listDictTypes: async () => [],
}))

function renderWithClient(node: React.ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <ConfirmProvider>{node}</ConfirmProvider>
      </I18nextProvider>
    </QueryClientProvider>
  )
}

describe('record runtime pages', () => {
  it('new record page renders form', async () => {
    const screen = await renderWithClient(<NewRecordPage />)
    await expect.element(screen.getByText('新建记录')).toBeVisible()
    await expect.element(screen.getByText('选择模板')).toBeVisible()
  })

  it('edit record page renders form with current title', async () => {
    const screen = await renderWithClient(<EditRecordPage />)
    await expect.element(screen.getByText('编辑记录')).toBeVisible()
    const titleLocator = screen.getByLabelText('标题')
    await vi.waitFor(() => {
      const node = titleLocator.element() as HTMLInputElement
      expect(node.value).toBe('日报')
    })
    await expect.element(screen.getByText('日报模板')).toBeVisible()
    await expect.element(screen.getByText('tpl1')).not.toBeInTheDocument()
  })

  it('detail record page renders readonly view', async () => {
    const screen = await renderWithClient(<DetailRecordPage />)
    await expect.element(screen.getByText('日报')).toBeVisible()
    await expect.element(screen.getByText('工作内容')).toBeVisible()
  })
})

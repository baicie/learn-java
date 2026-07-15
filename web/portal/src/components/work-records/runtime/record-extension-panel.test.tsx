import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { RecordExtensionPanel } from './record-extension-panel'

const api = vi.hoisted(() => ({
  createComment: vi.fn(async () => ({})),
  createRelation: vi.fn(async () => ({})),
  downloadAttachment: vi.fn(async () => ({ url: 'https://example.test/file' })),
  listAttachments: vi.fn(async () => [
    {
      id: 'file-1',
      recordId: 'record-1',
      fileName: 'evidence.txt',
      contentType: 'text/plain',
      sizeBytes: 1024,
      status: 'ready',
      uploadedBy: 'user-1',
      createdAt: '2026-07-14T00:00:00Z',
    },
  ]),
  listComments: vi.fn(async () => [
    {
      id: 'comment-1',
      recordId: 'record-1',
      content: '交接信息已确认',
      mentionUserIds: [],
      createdBy: 'user-1',
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
      rowVersion: 0,
    },
  ]),
  listRelations: vi.fn(async () => [
    {
      id: 'relation-1',
      recordId: 'record-1',
      relationType: 'incident',
      targetId: 'incident-1',
      targetTitle: '数据库故障',
      targetStatus: 'open',
      snapshotJson: '{}',
      createdBy: 'user-1',
      createdAt: '2026-07-14T00:00:00Z',
    },
  ]),
  listRecordAiGenerations: vi.fn(async () => [
    {
      id: 'ai-1',
      generationType: 'record_summary',
      resourceType: 'record',
      resourceId: 'record-1',
      status: 'success',
      outputMarkdown: '## 自动总结',
      provider: 'deterministic',
      model: 'fallback',
      requestedBy: 'user-1',
      reviewedBy: null,
      createdAt: '2026-07-14T00:00:00Z',
      finishedAt: '2026-07-14T00:00:01Z',
    },
  ]),
  listRecordSla: vi.fn(async () => [
    {
      id: 'sla-1',
      recordId: 'record-1',
      policyName: '完成时限',
      status: 'running',
      startedAt: '2026-07-14T00:00:00Z',
      dueAt: '2026-07-14T09:00:00Z',
      stoppedAt: null,
      breachedAt: null,
      severity: 'warning',
    },
  ]),
  requestRecordAiSummary: vi.fn(async () => ({})),
  reviewAiGeneration: vi.fn(async () => ({})),
  uploadAttachment: vi.fn(async () => ({})),
}))

vi.mock('@/api/work-records/extensions', () => api)

function authorize() {
  const principal: AuthorizationPrincipal = {
    userId: 'user-1',
    tenantId: 'tenant-1',
    username: 'alice',
    displayName: 'Alice',
    roles: ['record_admin'],
    permissions: [
      'work-record:read:all',
      'work-record:comment',
      'work-record:attachment',
      'work-record:relation',
      'work-record:ai:generate',
      'work-record:ai:review',
    ],
    dataScopes: {},
  }
  useAuthStore.getState().auth.setPrincipal(principal)
}

describe('RecordExtensionPanel', () => {
  beforeEach(() => {
    authorize()
    vi.clearAllMocks()
  })

  it('renders collaboration, AI and SLA data and submits a comment', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <RecordExtensionPanel recordId='record-1' />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('交接信息已确认')).toBeVisible()
    await screen.getByLabelText('新增评论').fill('补充说明')
    await screen.getByRole('button', { name: '提交评论' }).click()
    expect(api.createComment).toHaveBeenCalledWith('record-1', '补充说明')

    await screen.getByRole('tab', { name: '附件' }).click()
    await expect.element(screen.getByText('evidence.txt')).toBeVisible()

    await screen.getByRole('tab', { name: '关联' }).click()
    await expect.element(screen.getByText('数据库故障')).toBeVisible()

    await screen.getByRole('tab', { name: 'AI 总结' }).click()
    await expect.element(screen.getByText('## 自动总结')).toBeVisible()
    await screen.getByRole('button', { name: '采纳' }).click()
    expect(api.reviewAiGeneration).toHaveBeenCalledWith('ai-1', true)

    await screen.getByRole('tab', { name: 'SLA' }).click()
    await expect.element(screen.getByText('完成时限')).toBeVisible()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import {
  actOnApprovalTask,
  createComment,
  getStatistics,
  listComments,
} from './extensions'

vi.mock('@/lib/api-client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

const envelope = (data: unknown) => ({
  data: { success: true, data, timestamp: '2026-07-14T00:00:00Z' },
})

const comment = {
  id: 'comment-1',
  recordId: 'record-1',
  content: '已确认',
  mentionUserIds: [],
  createdBy: 'user-1',
  createdAt: '2026-07-14T00:00:00Z',
  updatedAt: '2026-07-14T00:00:00Z',
  rowVersion: 0,
}

describe('work-record extension api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('parses analytics and collaboration responses', async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(
        envelope({
          totalRecords: 4,
          completedRecords: 3,
          distinctOwners: 2,
          series: [],
          fieldAggregate: null,
        })
      )
      .mockResolvedValueOnce(envelope([comment]))

    expect(
      await getStatistics('2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z')
    ).toMatchObject({ totalRecords: 4 })
    expect(await listComments('record-1')).toEqual([comment])
  })

  it('submits comment and approval actions', async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce(envelope(comment))
      .mockResolvedValueOnce(envelope({ status: 'approved' }))

    expect(await createComment('record-1', '已确认')).toEqual(comment)
    expect(await actOnApprovalTask('task-1', true, '同意')).toEqual({
      status: 'approved',
    })
  })
})

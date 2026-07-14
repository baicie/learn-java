import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import * as templates from './templates'

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

const template = {
  id: 'tpl-1',
  tenantId: 'tenant-1',
  code: 'daily',
  name: '日报',
  status: 'draft',
  enabled: true,
  currentVersionId: null,
  draftSchemaJson: '{}',
  draftDesignerJson: '{}',
  createdBy: 'user-1',
  createdAt: '2026-07-14T00:00:00Z',
  updatedAt: '2026-07-14T00:00:00Z',
}

const response = (data: unknown) => ({
  data: {
    success: true,
    data,
    timestamp: '2026-07-14T00:00:00Z',
  },
})

describe('template management api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('exposes create, edit, copy and lifecycle endpoints', async () => {
    expect(typeof templates.createTemplate).toBe('function')
    expect(typeof templates.updateTemplate).toBe('function')
    expect(typeof templates.copyTemplate).toBe('function')
    expect(typeof templates.enableTemplate).toBe('function')
    expect(typeof templates.disableTemplate).toBe('function')
    expect(typeof templates.archiveTemplate).toBe('function')

    vi.mocked(apiClient.post).mockResolvedValue(response(template))
    vi.mocked(apiClient.put).mockResolvedValue(response(template))

    await templates.createTemplate({ code: 'daily', name: '日报' })
    expect(apiClient.post).toHaveBeenCalledWith('/api/work-record/templates', {
      code: 'daily',
      name: '日报',
      description: undefined,
      schemaJson: '{}',
      designerJson: '{}',
    })

    await templates.updateTemplate('tpl-1', { name: '新日报' })
    expect(apiClient.put).toHaveBeenCalledWith(
      '/api/work-record/templates/tpl-1',
      { name: '新日报' }
    )

    await templates.copyTemplate('tpl-1', {
      targetCode: 'daily-copy',
      targetName: '日报副本',
    })
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/work-record/templates/tpl-1/copy',
      { targetCode: 'daily-copy', targetName: '日报副本' }
    )

    await templates.enableTemplate('tpl-1')
    await templates.disableTemplate('tpl-1')
    await templates.archiveTemplate('tpl-1')
    expect(vi.mocked(apiClient.post).mock.calls.slice(-3)).toEqual([
      ['/api/work-record/templates/tpl-1/enable'],
      ['/api/work-record/templates/tpl-1/disable'],
      ['/api/work-record/templates/tpl-1/archive'],
    ])
  })
})

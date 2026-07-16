import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import { previewAssetImport } from './assets-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: { post: vi.fn() },
}))

describe('asset api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uploads CSV with an explicit source instance', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        success: true,
        data: {
          jobId: 'import-1',
          fileName: 'assets.csv',
          sourceInstanceId: 'inventory-csv',
          status: 'previewed',
          totalRows: 1,
          validRows: 1,
          invalidRows: 0,
          conflictRows: 0,
          createdRows: 0,
          updatedRows: 0,
          createdAt: '2026-07-16T00:00:00Z',
        },
      },
    })
    const file = new File(['external_id,name\nhost-1,api'], 'assets.csv')

    const result = await previewAssetImport(file, 'inventory-csv')

    expect(result.jobId).toBe('import-1')
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/assets/imports/preview',
      expect.any(FormData)
    )
    const form = vi.mocked(apiClient.post).mock.calls[0]?.[1] as FormData
    expect(form.get('file')).toBe(file)
    expect(form.get('sourceInstanceId')).toBe('inventory-csv')
  })
})

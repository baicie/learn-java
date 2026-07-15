import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import { importWorkRecords } from './imports'

describe('importWorkRecords', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uploads xlsx before submitting the asynchronous import job', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({
        data: {
          success: true,
          data: {
            uploadId: 'upload-1',
            uploadUrl: 'https://storage.example/upload',
            expiresAt: '2026-07-15T12:00:00Z',
          },
        },
      })
      .mockResolvedValueOnce({
        data: { success: true, data: { jobId: 'job-1' } },
      })
    const upload = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 200 }))
    const file = new File(['xlsx'], 'records.xlsx')

    const jobId = await importWorkRecords(file, {
      templateId: 'tpl-1',
      templateVersionId: 'version-1',
      defaultStatus: 'draft',
      stopOnError: false,
    })

    expect(jobId).toBe('job-1')
    expect(upload).toHaveBeenCalledWith(
      'https://storage.example/upload',
      expect.objectContaining({ method: 'PUT', body: file })
    )
    expect(post).toHaveBeenLastCalledWith(
      '/api/work-record/imports',
      expect.objectContaining({ uploadId: 'upload-1', templateId: 'tpl-1' })
    )
  })
})

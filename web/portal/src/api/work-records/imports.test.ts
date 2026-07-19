import { describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import {
  downloadImportTemplate,
  downloadWorkRecordImportTemplate,
  importWorkRecords,
} from './imports'

describe('importWorkRecords', () => {
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

    try {
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
    } finally {
      upload.mockRestore()
      post.mockRestore()
    }
  })

  it('downloads the template for the selected form version', async () => {
    const blob = new Blob(['xlsx'])
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: blob,
      headers: {
        'content-disposition':
          "attachment; filename*=UTF-8''daily-record-v3.xlsx",
      },
    })

    try {
      const result = await downloadWorkRecordImportTemplate(
        'template-1',
        'version-3'
      )

      expect(get).toHaveBeenCalledWith('/api/work-record/imports/template', {
        params: {
          templateId: 'template-1',
          templateVersionId: 'version-3',
        },
        responseType: 'blob',
      })
      expect(result).toEqual({ blob, fileName: 'daily-record-v3.xlsx' })
    } finally {
      get.mockRestore()
    }
  })

  it('saves a downloaded template with the server file name', () => {
    const createObjectUrl = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:template')
    const revokeObjectUrl = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => undefined)
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)

    try {
      downloadImportTemplate({
        blob: new Blob(['xlsx']),
        fileName: 'daily-record-v3.xlsx',
      })

      expect(click).toHaveBeenCalledOnce()
      expect(revokeObjectUrl).toHaveBeenCalledWith('blob:template')
    } finally {
      click.mockRestore()
      revokeObjectUrl.mockRestore()
      createObjectUrl.mockRestore()
    }
  })
})

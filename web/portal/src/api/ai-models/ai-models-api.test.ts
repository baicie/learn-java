import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import { createAiModel, listAiModels, setDefaultAiModel } from './ai-models-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('ai models api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('parses a DeepSeek model without exposing an API key', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        success: true,
        data: [
          {
            id: 'model-1',
            provider: 'deepseek',
            name: '生产模型',
            modelName: 'deepseek-chat',
            baseUrl: 'https://api.deepseek.com/v1',
            enabled: true,
            defaultModel: true,
            apiKeyConfigured: true,
            createdAt: '2026-07-18T00:00:00Z',
            updatedAt: '2026-07-18T00:00:00Z',
          },
        ],
      },
    })

    const models = await listAiModels()

    expect(models).toHaveLength(1)
    expect(models[0]).not.toHaveProperty('apiKey')
  })

  it('posts DeepSeek-only create input and can set the default', async () => {
    const model = {
      id: 'model-1',
      provider: 'deepseek' as const,
      name: '生产模型',
      modelName: 'deepseek-chat',
      baseUrl: 'https://api.deepseek.com/v1',
      enabled: true,
      defaultModel: false,
      apiKeyConfigured: true,
      createdAt: '2026-07-18T00:00:00Z',
      updatedAt: '2026-07-18T00:00:00Z',
    }
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { success: true, data: model },
    })

    await createAiModel({
      provider: 'deepseek',
      name: '生产模型',
      modelName: 'deepseek-chat',
      baseUrl: 'https://api.deepseek.com/v1',
      apiKey: 'sk-secret',
      enabled: true,
    })
    await setDefaultAiModel('model-1')

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      '/api/ai/models',
      expect.any(Object)
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      '/api/ai/models/model-1/default'
    )
  })
})

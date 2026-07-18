import { describe, expect, it } from 'vitest'
import {
  createAiModelFormSchema,
  updateAiModelFormSchema,
} from './ai-model-form-schema'

const valid = {
  provider: 'deepseek' as const,
  name: '生产模型',
  modelName: 'deepseek-chat',
  baseUrl: 'https://api.deepseek.com/v1',
  apiKey: 'sk-secret',
  enabled: true,
}

describe('AI model form schema', () => {
  it('accepts only DeepSeek and requires an API key on create', () => {
    expect(createAiModelFormSchema.safeParse(valid).success).toBe(true)
    expect(
      createAiModelFormSchema.safeParse({ ...valid, provider: 'openai' })
        .success
    ).toBe(false)
    expect(
      createAiModelFormSchema.safeParse({ ...valid, apiKey: '' }).success
    ).toBe(false)
  })

  it('allows a blank API key on update to preserve the stored secret', () => {
    expect(
      updateAiModelFormSchema.safeParse({ ...valid, apiKey: '' }).success
    ).toBe(true)
  })
})

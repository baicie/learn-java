import { z } from 'zod'

export const aiModelSchema = z.object({
  id: z.string(),
  provider: z.literal('deepseek'),
  name: z.string(),
  modelName: z.string(),
  baseUrl: z.url(),
  enabled: z.boolean(),
  defaultModel: z.boolean(),
  apiKeyConfigured: z.boolean(),
  lastTestStatus: z.enum(['success', 'failed']).nullish(),
  lastTestMessage: z.string().nullish(),
  lastTestedAt: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export type AiModel = z.infer<typeof aiModelSchema>

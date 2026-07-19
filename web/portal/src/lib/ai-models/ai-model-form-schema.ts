import { z } from 'zod'

const fields = {
  provider: z.literal('deepseek'),
  name: z.string().trim().min(1, '请输入配置名称').max(160),
  modelName: z.string().trim().min(1, '请输入模型标识').max(160),
  baseUrl: z
    .url('请输入有效的 API 地址')
    .refine(
      (value) => new URL(value).hostname === 'api.deepseek.com',
      '当前仅支持 DeepSeek 官方 API 地址'
    ),
  enabled: z.boolean(),
}

export const createAiModelFormSchema = z.object({
  ...fields,
  apiKey: z.string().trim().min(1, '请输入 API Key').max(512),
})

export const updateAiModelFormSchema = z.object({
  ...fields,
  apiKey: z.string().trim().max(512),
})

export type AiModelFormValues = z.infer<typeof createAiModelFormSchema>

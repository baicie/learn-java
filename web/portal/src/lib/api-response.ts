import { z } from 'zod'

export const apiResponseSchema = <T extends z.ZodType>(schema: T) =>
  z.object({
    success: z.boolean().optional(),
    data: schema,
    errorCode: z.string().nullable().optional(),
    message: z.string().nullable().optional(),
    timestamp: z.string().optional(),
  })

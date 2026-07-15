import { z } from 'zod'

export const apiResponseSchema = <T extends z.ZodType>(schema: T) =>
  z.object({
    success: z.boolean().optional(),
    data: schema,
    errorCode: z.string().nullable().optional(),
    message: z.string().nullable().optional(),
    timestamp: z.string().optional(),
  })

/**
 * Transitional parser for endpoints deployed both before and after the
 * standard response-envelope rollout. The payload itself is still validated
 * strictly in either case.
 */
export function parseApiResponseData<T extends z.ZodType>(
  schema: T,
  payload: unknown
): z.output<T> {
  const wrapped = apiResponseSchema(z.unknown())
    .extend({ success: z.literal(true) })
    .safeParse(payload)
  if (wrapped.success) return schema.parse(wrapped.data.data)
  return schema.parse(payload)
}

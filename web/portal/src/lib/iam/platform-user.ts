import { z } from 'zod'

const platformUserStatusSchema = z.enum([
  'active',
  'disabled',
  'locked',
  'pending',
])
const roleRefSchema = z.object({
  code: z.string(),
  name: z.string(),
})
export const platformUserSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  username: z.string(),
  displayName: z.string(),
  email: z.string().nullable(),
  status: platformUserStatusSchema,
  roles: z.array(roleRefSchema),
  dataScopes: z.record(z.string(), z.string()),
  lastLoginAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  rowVersion: z.number().int(),
})
export type PlatformUser = z.infer<typeof platformUserSchema>

export const platformUserPageSchema = z.object({
  items: z.array(platformUserSchema),
  page: z.number().int(),
  pageSize: z.number().int(),
  total: z.number().int(),
})
export type PlatformUserPage = z.infer<typeof platformUserPageSchema>

export const createPlatformUserSchema = z.object({
  username: z.string().min(3).max(64),
  displayName: z.string().min(1).max(128),
  email: z.string().email().nullable().optional(),
  initialPassword: z.string().min(8).max(128),
  status: platformUserStatusSchema.default('active'),
  roleCodes: z.array(z.string()).default([]),
})
export type CreatePlatformUserInput = z.infer<typeof createPlatformUserSchema>

export const replaceUserRolesInputSchema = z.object({
  roleCodes: z.array(z.string()),
  reason: z.string().optional(),
  rowVersion: z.number().int(),
})
export type ReplaceUserRolesInput = z.infer<typeof replaceUserRolesInputSchema>

export const changeUserStatusInputSchema = z.object({
  status: platformUserStatusSchema,
  reason: z.string().min(1).max(500),
  rowVersion: z.number().int(),
})
export type ChangeUserStatusInput = z.infer<typeof changeUserStatusInputSchema>

export type PlatformUserQuery = {
  page: number
  pageSize: number
  keyword?: string
  status?: z.infer<typeof platformUserStatusSchema>
  statuses?: Array<z.infer<typeof platformUserStatusSchema>>
  roleCodes?: string[]
  sortBy?: string
  sortDir?: 'asc' | 'desc'
}

const iamApiErrorCodeSchema = z.enum([
  'platform.user.not_found',
  'platform.role.not_found',
  'platform.permission.not_found',
  'platform.permission.directory.incomplete',
  'platform.user.username_conflict',
  'platform.role.permission_removed_for_active_role',
  'platform.role.has_active_users',
  'platform.user.version_conflict',
  'platform.role.version_conflict',
  'platform.role.protected',
  'platform.permission.denied',
  'platform.validation.failed',
])
export const iamApiErrorEnvelopeSchema = z.object({
  ok: z.literal(false),
  code: iamApiErrorCodeSchema,
  httpStatus: z.number().int(),
  message: z.string(),
  details: z.record(z.string(), z.unknown()).default({}),
})

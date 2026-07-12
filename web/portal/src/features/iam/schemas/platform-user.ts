import { z } from 'zod'

export const platformUserStatusSchema = z.enum([
  'active',
  'disabled',
  'locked',
  'pending',
])
export type PlatformUserStatus = z.infer<typeof platformUserStatusSchema>

export const roleRefSchema = z.object({
  code: z.string(),
  name: z.string(),
})
export type RoleRef = z.infer<typeof roleRefSchema>

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

export const updatePlatformUserSchema = z.object({
  displayName: z.string().min(1).max(128).optional(),
  email: z.string().email().nullable().optional(),
  roleCodes: z.array(z.string()).optional(),
})
export type UpdatePlatformUserInput = z.infer<typeof updatePlatformUserSchema>

export const platformUserQuerySchema = z.object({
  page: z.number().int().min(1).default(1),
  pageSize: z.number().int().min(1).max(200).default(20),
  keyword: z.string().optional(),
  status: platformUserStatusSchema.optional(),
  statuses: z.array(platformUserStatusSchema).optional(),
  roleCodes: z.array(z.string()).optional(),
  sortBy: z.string().optional(),
  sortDir: z.enum(['asc', 'desc']).optional(),
})
export type PlatformUserQuery = z.infer<typeof platformUserQuerySchema>

export const iamApiErrorCodeSchema = z.enum([
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
export type IamApiErrorCode = z.infer<typeof iamApiErrorCodeSchema>

export const iamApiErrorEnvelopeSchema = z.object({
  ok: z.literal(false),
  code: iamApiErrorCodeSchema,
  httpStatus: z.number().int(),
  message: z.string(),
  details: z.record(z.string(), z.unknown()).default({}),
})
export type IamApiErrorEnvelope = z.infer<typeof iamApiErrorEnvelopeSchema>
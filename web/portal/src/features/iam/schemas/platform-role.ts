import { z } from 'zod'

export const permissionRiskSchema = z.enum([
  'normal',
  'sensitive',
  'high',
  'critical',
])
export type PermissionRisk = z.infer<typeof permissionRiskSchema>

export const permissionDefinitionSchema = z.object({
  code: z.string(),
  moduleCode: z.string(),
  moduleName: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  riskLevel: permissionRiskSchema,
  dependencies: z.array(z.string()),
})
export type PermissionDefinition = z.infer<typeof permissionDefinitionSchema>

export const roleDataScopeSchema = z.object({
  resourceCode: z.string(),
  scopeType: z.enum(['ALL', 'SELF', 'DEPARTMENT', 'CUSTOM']),
  detail: z.record(z.string(), z.unknown()),
})
export type RoleDataScope = z.infer<typeof roleDataScopeSchema>

export const platformRoleSchema = z.object({
  roleCode: z.string(),
  roleName: z.string(),
  description: z.string().nullable(),
  enabled: z.boolean(),
  system: z.boolean(),
  userCount: z.number().int(),
  permissions: z.array(z.string()),
  dataScopes: z.record(z.string(), roleDataScopeSchema),
  rowVersion: z.number().int(),
})
export type PlatformRole = z.infer<typeof platformRoleSchema>

export const permissionModuleSchema = z.object({
  moduleCode: z.string(),
  moduleName: z.string(),
  children: z.array(permissionDefinitionSchema),
})
export type PermissionModule = z.infer<typeof permissionModuleSchema>

export const createRoleInputSchema = z.object({
  code: z
    .string()
    .min(2)
    .max(64)
    .regex(/^[a-z][a-z0-9-]*$/, 'role code must be lowercase kebab'),
  name: z.string().min(1).max(128),
  description: z.string().max(500).nullable().optional(),
  system: z.boolean().default(false),
  enabled: z.boolean().default(true),
  permissionCodes: z.array(z.string()).default([]),
})
export type CreateRoleInput = z.infer<typeof createRoleInputSchema>

export const updateRoleInputSchema = z.object({
  name: z.string().min(1).max(128).optional(),
  description: z.string().max(500).nullable().optional(),
  enabled: z.boolean().optional(),
  permissionCodes: z.array(z.string()).optional(),
})
export type UpdateRoleInput = z.infer<typeof updateRoleInputSchema>

export const replaceRolePermissionsInputSchema = z.object({
  permissionCodes: z.array(z.string()),
  confirmation: z.object({
    reason: z.string().min(1),
    dangerousAcknowledged: z.boolean().default(false),
  }),
})
export type ReplaceRolePermissionsInput = z.infer<
  typeof replaceRolePermissionsInputSchema
>

export const replaceRoleDataScopesInputSchema = z.object({
  scopes: z.array(roleDataScopeSchema),
  rowVersion: z.number().int(),
})
export type ReplaceRoleDataScopesInput = z.infer<
  typeof replaceRoleDataScopesInputSchema
>
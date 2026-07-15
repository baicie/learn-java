import { z } from 'zod'

const permissionRiskSchema = z.enum(['normal', 'sensitive', 'high', 'critical'])
export type PermissionRisk = z.infer<typeof permissionRiskSchema>

const permissionDefinitionSchema = z.object({
  code: z.string(),
  moduleCode: z.string(),
  moduleName: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  riskLevel: permissionRiskSchema,
  dependencies: z.array(z.string()),
})
export type PermissionDefinition = z.infer<typeof permissionDefinitionSchema>

export const roleDataScopeSchema = z.object({
  resourceCode: z.string(),
  scopeType: z.enum(['ALL', 'SELF', 'DEPARTMENT', 'CUSTOM']),
  detail: z.record(z.string(), z.unknown()),
})
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

export const createPlatformRoleSchema = z.object({
  code: z
    .string()
    .min(2)
    .max(64)
    .regex(/^[a-z][a-z0-9_-]*$/),
  name: z.string().min(1).max(128),
  description: z.string().max(500).nullable(),
  system: z.literal(false).default(false),
  enabled: z.boolean().default(true),
  permissionCodes: z.array(z.string()).default([]),
})
export type CreatePlatformRoleInput = z.infer<typeof createPlatformRoleSchema>

export const permissionModuleSchema = z.object({
  moduleCode: z.string(),
  moduleName: z.string(),
  children: z.array(permissionDefinitionSchema),
})
export type PermissionModule = z.infer<typeof permissionModuleSchema>

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

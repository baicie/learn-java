import { z } from 'zod'

const permissionRiskSchema = z.enum(['normal', 'sensitive', 'high', 'critical'])
export type PermissionRisk = z.infer<typeof permissionRiskSchema>

const permissionDefinitionSchema = z.object({
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

import { describe, expect, it } from 'vitest'
import {
  platformRoleSchema,
  permissionModuleSchema,
  replaceRolePermissionsInputSchema,
  roleDataScopeSchema,
} from './platform-role'

const roleBase = {
  roleCode: 'ops-viewer',
  roleName: 'Ops Viewer',
  description: 'View-only operator',
  enabled: true,
  system: false,
  userCount: 4,
  permissions: ['platform:user:read'],
  dataScopes: {},
  rowVersion: 2,
}

describe('platformRoleSchema', () => {
  it('parses a valid role payload', () => {
    const parsed = platformRoleSchema.parse(roleBase)
    expect(parsed.roleCode).toBe('ops-viewer')
    expect(parsed.permissions).toEqual(['platform:user:read'])
  })

  it('flags data scope types', () => {
    const parsed = roleDataScopeSchema.parse({
      resourceCode: 'incident',
      scopeType: 'DEPARTMENT',
      detail: { regionIds: ['cn-north-1'] },
    })
    expect(parsed.scopeType).toBe('DEPARTMENT')
  })
})

describe('permissionModuleSchema', () => {
  it('groups permissions by module', () => {
    const parsed = permissionModuleSchema.parse({
      moduleCode: 'platform-user',
      moduleName: 'User',
      children: [
        {
          code: 'platform:user:read',
          moduleCode: 'platform-user',
          moduleName: 'User',
          name: '查看用户',
          description: null,
          riskLevel: 'sensitive',
          dependencies: [],
        },
      ],
    })
    expect(parsed.children).toHaveLength(1)
  })
})

describe('replaceRolePermissionsInputSchema', () => {
  it('requires a non-empty reason', () => {
    expect(() =>
      replaceRolePermissionsInputSchema.parse({
        permissionCodes: ['platform:user:read'],
        confirmation: { reason: '', dangerousAcknowledged: true },
      })
    ).toThrow()
  })
})

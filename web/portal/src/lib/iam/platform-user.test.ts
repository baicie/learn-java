import { describe, expect, it } from 'vitest'
import {
  platformUserSchema,
  platformUserPageSchema,
  createPlatformUserSchema,
  iamApiErrorEnvelopeSchema,
} from './platform-user'

const userBase = {
  id: 'u1',
  tenantId: 't1',
  username: 'alice',
  displayName: 'Alice',
  email: '[email protected]',
  status: 'active',
  roles: [{ code: 'ops-viewer', name: 'Ops Viewer' }],
  dataScopes: { incident: 'SELF' },
  lastLoginAt: '2026-07-12T00:00:00Z',
  createdAt: '2026-07-12T00:00:00Z',
  updatedAt: '2026-07-12T00:00:00Z',
  rowVersion: 3,
}

describe('platformUserSchema', () => {
  it('parses a valid user payload', () => {
    const parsed = platformUserSchema.parse(userBase)
    expect(parsed.username).toBe('alice')
    expect(parsed.roles).toHaveLength(1)
  })

  it('normalizes the uppercase status returned by the IAM API', () => {
    expect(
      platformUserSchema.parse({ ...userBase, status: 'ACTIVE' }).status
    ).toBe('active')
  })

  it('rejects unknown status values', () => {
    expect(() =>
      platformUserSchema.parse({ ...userBase, status: 'ghost' })
    ).toThrow()
  })

  it('accepts nullable email', () => {
    const parsed = platformUserSchema.parse({ ...userBase, email: null })
    expect(parsed.email).toBeNull()
  })

  it('accepts an omitted last login time from the IAM API', () => {
    const { lastLoginAt: _lastLoginAt, ...payload } = userBase
    const parsed = platformUserSchema.parse(payload)
    expect(parsed.lastLoginAt).toBeUndefined()
  })
})

describe('platformUserPageSchema', () => {
  it('round-trips a one-row page', () => {
    const parsed = platformUserPageSchema.parse({
      items: [userBase],
      page: 1,
      pageSize: 20,
      total: 1,
    })
    expect(parsed.items[0].username).toBe('alice')
    expect(parsed.total).toBe(1)
  })
})

describe('createPlatformUserSchema', () => {
  it('defaults status to active and roleCodes to []', () => {
    const parsed = createPlatformUserSchema.parse({
      username: 'bob',
      displayName: 'Bob',
      initialPassword: 'changeMe-9!',
    })
    expect(parsed.status).toBe('active')
    expect(parsed.roleCodes).toEqual([])
  })

  it('rejects short initial password', () => {
    expect(() =>
      createPlatformUserSchema.parse({
        username: 'bob',
        displayName: 'Bob',
        initialPassword: 'short',
      })
    ).toThrow()
  })
})

describe('iamApiErrorEnvelopeSchema', () => {
  it('recognizes the standard 409 conflict envelope', () => {
    const parsed = iamApiErrorEnvelopeSchema.parse({
      ok: false,
      code: 'platform.user.username_conflict',
      httpStatus: 409,
      message: 'username taken',
      details: { username: 'alice' },
    })
    expect(parsed.code).toBe('platform.user.username_conflict')
    expect(parsed.httpStatus).toBe(409)
  })
})

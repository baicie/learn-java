import { describe, expect, it } from 'vitest'
import { principalSchema, responseSchema } from './authorization-api'

describe('authorization-api responseSchema', () => {
  it('accepts the real /api/auth/me success payload (NON_NULL trimmed fields)', () => {
    // Spring Boot 的 ObjectMapper 配了 NON_NULL：成功响应里 errorCode / message
    // 会从 JSON 中省略（变成 undefined）。schema 必须容忍，不能写成 z.null()。
    const realPayload = {
      success: true,
      data: {
        userId: '9be00862-fbdf-4341-bb3f-dab21a1c4c76',
        tenantId: 'b4ea8fde-329b-4e7f-b9be-ab550aaccc6d',
        username: 'admin',
        displayName: 'Admin',
        roles: ['system_admin'],
        permissions: ['incident:read', 'incident:write'],
        dataScopes: {},
      },
      timestamp: '2026-07-12T12:00:00+08:00',
      requestId: 'req_abc',
    }

    const parsed = responseSchema.parse(realPayload)
    expect(parsed.success).toBe(true)
    expect(parsed.data.username).toBe('admin')
    expect(parsed.errorCode).toBeUndefined()
    expect(parsed.message).toBeUndefined()
  })

  it('accepts a payload that explicitly carries null errorCode / message', () => {
    const payload = {
      success: true,
      data: {
        userId: 'u1',
        tenantId: 't1',
        username: 'u',
        displayName: 'U',
        roles: [],
        permissions: [],
        dataScopes: {},
      },
      errorCode: null,
      message: null,
      timestamp: '2026-07-12T12:00:00+08:00',
    }

    const parsed = responseSchema.parse(payload)
    expect(parsed.errorCode).toBeNull()
    expect(parsed.message).toBeNull()
  })

  it('rejects an error payload (success=false)', () => {
    const payload = {
      success: false,
      errorCode: 'UNAUTHORIZED',
      message: 'token expired',
      timestamp: '2026-07-12T12:00:00+08:00',
    }

    expect(() => responseSchema.parse(payload)).toThrow()
  })

  it('principalSchema enforces required principal fields', () => {
    expect(() =>
      principalSchema.parse({
        userId: 'u',
        tenantId: 't',
        username: 'u',
        displayName: 'U',
        roles: [],
        permissions: [],
        // dataScopes 缺失
      } as unknown)
    ).toThrow()
  })
})

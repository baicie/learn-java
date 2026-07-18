import { isRedirect } from '@tanstack/react-router'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '@/stores/auth-store'
import { Route as DetailRoute } from './$recordId'
import { Route as EditRoute } from './$recordId.edit'
import { Route as NewRoute } from './new'
import { Route as DesignerRoute } from './templates.$templateId.designer'

function principal(permissions: string[]) {
  useAuthStore.getState().auth.setPrincipal({
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles: [],
    permissions,
    dataScopes: {},
  })
}

async function callBeforeLoad(route: typeof DetailRoute) {
  return route.options.beforeLoad?.({} as never)
}

describe('work record route permissions', () => {
  it('requires read permission for the detail route', async () => {
    principal(['work-record:write'])

    let caught: unknown = null
    try {
      await callBeforeLoad(DetailRoute)
    } catch (error) {
      caught = error
    }
    expect(isRedirect(caught)).toBe(true)
  })

  it('requires write permission for new and edit routes', async () => {
    principal(['work-record:read:self'])

    for (const route of [NewRoute, EditRoute]) {
      let caught: unknown = null
      try {
        await callBeforeLoad(route as unknown as typeof DetailRoute)
      } catch (error) {
        caught = error
      }
      expect(isRedirect(caught)).toBe(true)
    }
  })

  it('requires template write permission for the designer route', async () => {
    principal(['work-record:template:read'])

    let caught: unknown = null
    try {
      await callBeforeLoad(DesignerRoute as unknown as typeof DetailRoute)
    } catch (error) {
      caught = error
    }
    expect(isRedirect(caught)).toBe(true)
  })
})

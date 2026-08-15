import { isRedirect } from '@tanstack/react-router'
import '@/routeTree.gen'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '@/stores/auth-store'
import { Route as DetailRoute } from './$recordId'
import { Route as EditRoute } from './$recordId_.edit'
import { Route as NewRoute } from './new'
import { Route as OperationsRoute } from './operations'
import { Route as DesignerRoute } from './templates.$templateId.designer'

function principal(
  permissions: string[],
  workRecordDataScope?: 'SELF' | 'ALL'
) {
  const auth = useAuthStore.getState().auth
  auth.setAccessToken('test-token')
  auth.setPrincipal({
    userId: 'u1',
    tenantId: 't1',
    username: 'u1',
    displayName: 'U1',
    roles: [],
    permissions,
    dataScopes: workRecordDataScope
      ? { 'work-record': workRecordDataScope }
      : {},
  })
  auth.setAuthorizationLoaded(true)
}

async function callBeforeLoad(route: typeof DetailRoute) {
  return route.options.beforeLoad?.({} as never)
}

describe('work record route permissions', () => {
  it('keeps the edit route outside the detail route component', () => {
    expect(EditRoute.options.getParentRoute?.()).not.toBe(DetailRoute)
  })

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

  it('requires ALL data scope for AI-only operations access', async () => {
    principal(['work-record:ai:review', 'work-record:read:all'], 'SELF')

    let caught: unknown = null
    try {
      await callBeforeLoad(OperationsRoute as unknown as typeof DetailRoute)
    } catch (error) {
      caught = error
    }
    expect(isRedirect(caught)).toBe(true)

    principal(['work-record:ai:review', 'work-record:read:all'], 'ALL')
    await expect(
      callBeforeLoad(OperationsRoute as unknown as typeof DetailRoute)
    ).resolves.toMatchObject({ userId: 'u1' })
  })
})

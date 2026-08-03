import { requireAnyPermission } from '@/auth/permission'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Route as AlertsRoute } from '../alerts/index'
import { Route as IncidentDetailRoute } from './$incidentId'
import { Route as IncidentsRoute } from './index'

vi.mock('@/auth/permission', () => ({
  requireAnyPermission: vi.fn(),
}))

describe('incident center route permissions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('guards the alert list with alert read permission', async () => {
    await AlertsRoute.options.beforeLoad?.({} as never)

    expect(requireAnyPermission).toHaveBeenCalledWith(['alert:read'])
  })

  it('guards both incident routes with incident read permission', async () => {
    await IncidentsRoute.options.beforeLoad?.({} as never)
    await IncidentDetailRoute.options.beforeLoad?.({} as never)

    expect(requireAnyPermission).toHaveBeenNthCalledWith(1, ['incident:read'])
    expect(requireAnyPermission).toHaveBeenNthCalledWith(2, ['incident:read'])
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import type { Datasource } from '@/lib/datasources/datasource'
import { DatasourcesTable } from './datasources-table'

const datasource: Datasource = {
  id: 'ds_zabbix_1',
  tenantId: 'tenant_1',
  type: 'zabbix',
  name: '生产 Zabbix',
  endpoint: 'https://zabbix.example/api_jsonrpc.php',
  status: 'active',
  createdAt: '2026-07-18T00:00:00Z',
  updatedAt: '2026-07-18T00:00:00Z',
  lastSyncAt: null,
}

function grantDatasourceWritePermission() {
  const principal: AuthorizationPrincipal = {
    userId: 'user_1',
    tenantId: 'tenant_1',
    username: 'operator',
    displayName: 'Operator',
    roles: [],
    permissions: ['datasource:write'],
    dataScopes: {},
  }
  useAuthStore.getState().auth.setPrincipal(principal)
  useAuthStore.getState().auth.setAuthorizationLoaded(true)
}

afterEach(() => {
  useAuthStore.getState().auth.reset()
})

describe('DatasourcesTable', () => {
  it('opens webhook configuration for a Zabbix datasource', async () => {
    grantDatasourceWritePermission()
    const onWebhook = vi.fn()
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DatasourcesTable
          items={[datasource]}
          onTest={vi.fn()}
          onSync={vi.fn()}
          onEdit={vi.fn()}
          onWebhook={onWebhook}
          pending={false}
        />
      </QueryClientProvider>
    )

    await screen.getByRole('button', { name: 'Webhook' }).click()
    expect(onWebhook).toHaveBeenCalledWith(datasource)
  })

  it('does not show webhook configuration for other datasource types', async () => {
    grantDatasourceWritePermission()
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DatasourcesTable
          items={[{ ...datasource, type: 'kubernetes' }]}
          onTest={vi.fn()}
          onSync={vi.fn()}
          onEdit={vi.fn()}
          onWebhook={vi.fn()}
          pending={false}
        />
      </QueryClientProvider>
    )

    expect(screen.getByRole('button', { name: 'Webhook' }).query()).toBeNull()
  })

  it('disables webhook configuration for an inactive Zabbix datasource', async () => {
    grantDatasourceWritePermission()
    const onWebhook = vi.fn()
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DatasourcesTable
          items={[{ ...datasource, status: 'inactive' }]}
          onTest={vi.fn()}
          onSync={vi.fn()}
          onEdit={vi.fn()}
          onWebhook={onWebhook}
          pending={false}
        />
      </QueryClientProvider>
    )

    const webhookButton = screen.getByRole('button', { name: 'Webhook' })
    await expect.element(webhookButton).toBeDisabled()
    expect(onWebhook).not.toHaveBeenCalled()
  })

  it('disables manual sync until the datasource connection is active', async () => {
    grantDatasourceWritePermission()
    const onSync = vi.fn()
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DatasourcesTable
          items={[{ ...datasource, status: 'inactive' }]}
          onTest={vi.fn()}
          onSync={onSync}
          onEdit={vi.fn()}
          onWebhook={vi.fn()}
          pending={false}
        />
      </QueryClientProvider>
    )

    const syncButton = screen.getByRole('button', { name: '同步' })
    await expect.element(syncButton).toBeDisabled()
    expect(onSync).not.toHaveBeenCalled()
  })

  it('opens editing for the selected datasource', async () => {
    grantDatasourceWritePermission()
    const onEdit = vi.fn()
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DatasourcesTable
          items={[datasource]}
          onTest={vi.fn()}
          onSync={vi.fn()}
          onEdit={onEdit}
          onWebhook={vi.fn()}
          pending={false}
        />
      </QueryClientProvider>
    )

    await screen.getByRole('button', { name: '编辑' }).click()
    expect(onEdit).toHaveBeenCalledWith(datasource)
  })
})

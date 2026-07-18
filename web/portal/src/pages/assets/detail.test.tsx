import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { AssetDetailPage } from './detail'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(async () => undefined),
  Link: ({ children }: { children: React.ReactNode }) => (
    <a href='/assets'>{children}</a>
  ),
}))

vi.mock('@/hooks/assets/use-assets', () => ({
  useArchiveAsset: () => ({ isPending: false, mutate: vi.fn() }),
  useAssetDetail: () => ({
    isLoading: false,
    isError: false,
    data: {
      asset: {
        id: 'asset-1',
        assetType: 'host',
        name: 'web-01',
        displayName: 'Web 01',
        description: null,
        environment: 'production',
        ip: '10.0.0.10',
        site: 'shanghai',
        ownerTeam: 'platform',
        criticality: 'high',
        tags: { env: 'production', service: 'checkout' },
        status: 'active',
        sourceCount: 1,
        lastSeenAt: '2026-07-18T08:00:00Z',
        createdAt: '2026-07-18T08:00:00Z',
        updatedAt: '2026-07-18T08:00:00Z',
        version: 1,
      },
      sources: [
        {
          id: 'source-1',
          sourceType: 'zabbix',
          sourceInstanceId: 'zabbix-prod',
          datasourceId: 'datasource-1',
          externalId: 'host-10084',
          ingestionChannel: 'sync',
          syncStatus: 'synced',
          firstSeenAt: '2026-07-18T08:00:00Z',
          lastSeenAt: '2026-07-18T08:00:00Z',
        },
      ],
      identities: [],
      relations: [],
    },
    refetch: vi.fn(),
  }),
}))

vi.mock('@/components/assets/asset-form-dialog', () => ({
  AssetFormDialog: () => null,
}))
vi.mock('@/components/feedback/confirm-provider', () => ({
  useConfirm: () => vi.fn(async () => false),
}))
vi.mock('@/components/layout/header', () => ({
  Header: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))
vi.mock('@/components/layout/main', () => ({
  Main: ({ children }: { children: React.ReactNode }) => (
    <main>{children}</main>
  ),
}))
vi.mock('@/components/permission-gate', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}))
vi.mock('@/components/profile-dropdown', () => ({
  ProfileDropdown: () => null,
}))
vi.mock('@/components/search', () => ({ Search: () => null }))
vi.mock('@/components/theme-switch', () => ({ ThemeSwitch: () => null }))

describe('AssetDetailPage', () => {
  it('renders tags as badges and expands source details on demand', async () => {
    const screen = await render(<AssetDetailPage assetId='asset-1' />)

    await expect
      .element(screen.getByText('env=production'))
      .toHaveAttribute('data-slot', 'badge')
    await expect
      .element(screen.getByText('service=checkout'))
      .toHaveAttribute('data-slot', 'badge')

    const sourceTrigger = screen.getByRole('button', { name: /Zabbix/ })
    await expect
      .element(sourceTrigger)
      .toHaveAttribute('aria-expanded', 'false')
    await expect.element(screen.getByText('host-10084')).not.toBeInTheDocument()

    await sourceTrigger.click()

    await expect.element(sourceTrigger).toHaveAttribute('aria-expanded', 'true')
    await expect.element(screen.getByText('host-10084')).toBeVisible()
  })
})

import { type ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { PlatformUsersPage } from './platform-users-page'

const queryState = vi.hoisted(() => ({
  isPending: true,
  isError: false,
  error: null as Error | null,
  data: undefined as { total: number } | undefined,
  refetch: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  getRouteApi: () => ({
    useSearch: () => ({}),
    useNavigate: () => vi.fn(),
  }),
}))

vi.mock('@/hooks/iam/use-platform-users', () => ({
  usePlatformUsers: () => queryState,
}))

vi.mock('@/auth/permission-gate', () => ({
  PermissionGate: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('./platform-user-toolbar', () => ({
  PlatformUserToolbar: () => <div>user toolbar</div>,
}))

vi.mock('./platform-user-table', () => ({
  PlatformUserTable: () => <div>user table</div>,
}))

vi.mock('./platform-user-create-dialog', () => ({
  PlatformUserCreateDialog: () => null,
}))

describe('PlatformUsersPage async states', () => {
  it('uses the shared table loading state while loading', async () => {
    queryState.isPending = true
    queryState.isError = false
    queryState.error = null
    queryState.data = undefined

    await render(<PlatformUsersPage />)

    expect(
      document.querySelector('[data-slot="table-loading-state"]')
    ).not.toBeNull()
  })

  it('uses the shared error state and retries the query', async () => {
    queryState.isPending = false
    queryState.isError = true
    queryState.error = new Error('network unavailable')
    queryState.data = undefined
    queryState.refetch.mockClear()

    const screen = await render(<PlatformUsersPage />)

    await screen.getByRole('button', { name: '重新加载' }).click()

    expect(queryState.refetch).toHaveBeenCalledOnce()
    expect(document.querySelector('[data-slot="error-state"]')).not.toBeNull()
  })

  it('uses the shared empty state when there are no users', async () => {
    queryState.isPending = false
    queryState.isError = false
    queryState.error = null
    queryState.data = { total: 0 }

    const screen = await render(<PlatformUsersPage />)

    await expect.element(screen.getByText('暂无用户')).toBeVisible()
    expect(document.querySelector('[data-slot="empty-state"]')).not.toBeNull()
  })
})

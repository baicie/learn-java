import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, type RenderResult } from 'vitest-browser-react'
import { type Locator, userEvent } from 'vitest/browser'
import { UserAuthForm } from './user-auth-form'

const {
  navigate,
  setAccessTokenMock,
  resetMock,
  loginApiMock,
  ensureAuthorizationLoadedMock,
} = vi.hoisted(() => ({
  navigate: vi.fn(),
  setAccessTokenMock: vi.fn(),
  resetMock: vi.fn(),
  loginApiMock: vi.fn(),
  ensureAuthorizationLoadedMock: vi.fn(),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: { auth: unknown }) => unknown) =>
    selector({
      auth: {
        accessToken: '',
        setAccessToken: setAccessTokenMock,
        reset: resetMock,
      },
    }),
}))

vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-router')>()
  return {
    ...actual,
    useNavigate: () => navigate,
    Link: ({
      children,
      to,
      className,
      ...rest
    }: {
      children?: React.ReactNode
      to: string
      className?: string
    }) => (
      <a href={to} className={className} {...rest}>
        {children}
      </a>
    ),
  }
})

vi.mock('@/features/auth/login-api', () => ({
  login: loginApiMock,
}))

vi.mock('@/features/auth/authorization-session', () => ({
  ensureAuthorizationLoaded: ensureAuthorizationLoadedMock,
}))

const SUCCESS_LOGIN = {
  token: 'mock-access-token',
  user: {
    id: 'user-id-1',
    tenantId: 'tenant-1',
    username: 'admin',
    displayName: '管理员',
    roles: ['system_admin'],
  },
}

const buildPrincipal = () => ({
  userId: SUCCESS_LOGIN.user.id,
  tenantId: SUCCESS_LOGIN.user.tenantId,
  username: SUCCESS_LOGIN.user.username,
  displayName: SUCCESS_LOGIN.user.displayName,
  roles: SUCCESS_LOGIN.user.roles,
  permissions: [],
  dataScopes: {},
})

describe('UserAuthForm', () => {
  let screen: RenderResult
  let usernameInput: Locator
  let passwordInput: Locator
  let signInButton: Locator

  beforeEach(async () => {
    await cleanup()
    vi.clearAllMocks()
    loginApiMock.mockResolvedValue(SUCCESS_LOGIN)
    ensureAuthorizationLoadedMock.mockResolvedValue(buildPrincipal())
    screen = await render(<UserAuthForm />)
    usernameInput = screen.getByLabelText(/^用户名$/)
    passwordInput = screen.getByLabelText(/^密码$/)
    signInButton = screen.getByRole('button', { name: /登录/ })
  })

  it('renders fields and submit button', async () => {
    await expect.element(usernameInput).toBeInTheDocument()
    await expect.element(passwordInput).toBeInTheDocument()
    await expect.element(signInButton).toBeInTheDocument()
  })

  it('shows validation messages when submitting empty form', async () => {
    await userEvent.click(signInButton)
    await expect.element(screen.getByText('请输入用户名')).toBeInTheDocument()
    await expect.element(screen.getByText('请输入密码')).toBeInTheDocument()
  })

  it('authenticates and navigates to default route on success', async () => {
    await userEvent.fill(usernameInput, 'admin')
    await userEvent.fill(passwordInput, 'admin123')
    await userEvent.click(signInButton)

    await vi.waitFor(() => expect(loginApiMock).toHaveBeenCalledOnce())
    expect(loginApiMock).toHaveBeenCalledWith('admin', 'admin123')
    expect(setAccessTokenMock).toHaveBeenCalledOnce()
    expect(setAccessTokenMock).toHaveBeenCalledWith('mock-access-token')
    expect(ensureAuthorizationLoadedMock).toHaveBeenCalledWith(true)
    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: '/', replace: true })
    )
  })

  it('navigates to redirectTo when provided', async () => {
    await cleanup()
    vi.clearAllMocks()
    loginApiMock.mockResolvedValue(SUCCESS_LOGIN)
    ensureAuthorizationLoadedMock.mockResolvedValue(buildPrincipal())

    const result = await render(<UserAuthForm redirectTo='/settings' />)
    await userEvent.fill(result.getByLabelText(/^用户名$/), 'admin')
    await userEvent.fill(result.getByLabelText(/^密码$/), 'admin123')
    await userEvent.click(result.getByRole('button', { name: /登录/ }))

    await vi.waitFor(() => expect(setAccessTokenMock).toHaveBeenCalledOnce())
    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: '/settings',
        replace: true,
      })
    )
  })

  it('falls back to default error text and resets auth on failure', async () => {
    loginApiMock.mockRejectedValueOnce(
      new Error('Request failed with status code 404')
    )

    await userEvent.fill(usernameInput, 'admin')
    await userEvent.fill(passwordInput, 'wrong-password')
    await userEvent.click(signInButton)

    await vi.waitFor(() => expect(loginApiMock).toHaveBeenCalledOnce())
    await vi.waitFor(() => expect(resetMock).toHaveBeenCalledOnce())
    expect(setAccessTokenMock).not.toHaveBeenCalled()
    expect(navigate).not.toHaveBeenCalled()
  })
})

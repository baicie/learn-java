import { setLanguage } from '@/i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { SidebarProvider } from '@/components/ui/sidebar'
import { NavUser } from './nav-user'

vi.mock('@/auth/use-authorization', () => ({
  useAuthorization: () => ({ username: 'admin', displayName: 'Admin' }),
}))

vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-router')>()
  return {
    ...actual,
    Link: ({
      children,
      to,
      ...props
    }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) => (
      <a href={to} {...props}>
        {children}
      </a>
    ),
    useNavigate: () => vi.fn(),
    useLocation: () => ({ href: '/' }),
  }
})

describe('NavUser', () => {
  beforeEach(() => setLanguage('zh-CN'))
  afterEach(() => setLanguage('zh-CN'))

  it('shows the signed-in user and useful account links', async () => {
    const screen = await render(
      <SidebarProvider>
        <NavUser />
      </SidebarProvider>
    )

    await userEvent.click(screen.getByRole('button', { name: /Admin/ }))
    await expect
      .element(screen.getByRole('menuitem', { name: '个人资料' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('menuitem', { name: '账户与语言' }))
      .toBeVisible()
    await expect
      .element(screen.getByText('Upgrade to Pro'))
      .not.toBeInTheDocument()
  })
})

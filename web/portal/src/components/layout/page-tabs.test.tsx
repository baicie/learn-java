import { setLanguage } from '@/i18n'
import { clearCookies } from '@/test-utils/cookies'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { setCookie } from '@/lib/cookies'
import { LayoutProvider } from '@/context/layout-provider'
import { PageTabs } from './page-tabs'
import { PAGE_TABS_STORAGE_KEY } from './page-tabs-model'

const navigate = vi.fn()
let currentHref = '/assets'

vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-router')>()
  return {
    ...actual,
    useNavigate: () => navigate,
    useLocation: ({
      select,
    }: {
      select: (location: { href: string }) => string
    }) => select({ href: currentHref }),
  }
})

async function renderPageTabs(enabled = true) {
  if (enabled) setCookie('layout_page_tabs', 'true')
  return render(
    <LayoutProvider>
      <PageTabs />
    </LayoutProvider>
  )
}

describe('PageTabs', () => {
  beforeEach(() => {
    clearCookies()
    sessionStorage.clear()
    navigate.mockReset()
    currentHref = '/assets'
    setLanguage('en-US')
  })

  afterEach(() => setLanguage('zh-CN'))

  it('adds the current page and marks it active', async () => {
    const screen = await renderPageTabs()

    await expect
      .element(screen.getByRole('tab', { name: /^Asset Center$/ }))
      .toHaveAttribute('aria-selected', 'true')
    expect(
      JSON.parse(sessionStorage.getItem(PAGE_TABS_STORAGE_KEY) ?? '[]')
    ).toEqual([{ href: '/assets', title: 'Asset Center' }])
  })

  it('navigates when another tab is selected', async () => {
    sessionStorage.setItem(
      PAGE_TABS_STORAGE_KEY,
      JSON.stringify([
        { href: '/', title: 'Dashboard' },
        { href: '/assets', title: 'Assets' },
      ])
    )
    const screen = await renderPageTabs()

    await userEvent.click(screen.getByRole('tab', { name: /^Dashboard$/ }))

    expect(navigate).toHaveBeenCalledWith({ to: '/' })
  })

  it('closes an inactive tab without navigating', async () => {
    sessionStorage.setItem(
      PAGE_TABS_STORAGE_KEY,
      JSON.stringify([
        { href: '/', title: 'Dashboard' },
        { href: '/assets', title: 'Assets' },
      ])
    )
    const screen = await renderPageTabs()

    await userEvent.click(
      screen.getByRole('button', { name: /^Close Dashboard$/ })
    )

    await expect
      .element(screen.getByRole('tab', { name: /^Dashboard$/ }))
      .not.toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
  })

  it('closes the active tab and navigates to its neighbor', async () => {
    sessionStorage.setItem(
      PAGE_TABS_STORAGE_KEY,
      JSON.stringify([
        { href: '/', title: 'Dashboard' },
        { href: '/assets', title: 'Assets' },
        { href: '/platform/users', title: 'Users' },
      ])
    )
    const screen = await renderPageTabs()

    await userEvent.click(
      screen.getByRole('button', { name: /^Close Asset Center$/ })
    )

    expect(navigate).toHaveBeenCalledWith({ to: '/platform/users' })
    expect(navigate).toHaveBeenCalledTimes(1)
  })

  it('does not render when tabs layout is disabled', async () => {
    const screen = await renderPageTabs(false)

    await expect
      .element(screen.getByRole('tablist', { name: /open pages/i }))
      .not.toBeInTheDocument()
  })
})

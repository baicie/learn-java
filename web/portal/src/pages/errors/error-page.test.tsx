import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRouter,
} from '@tanstack/react-router'
import { i18n } from '@/i18n'
import { beforeEach, describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ForbiddenError } from './forbidden'
import { GeneralError } from './general-error'
import { MaintenanceError } from './maintenance-error'
import { NotFoundError } from './not-found-error'
import { UnauthorisedError } from './unauthorized-error'

async function renderErrorPage(component: React.ComponentType) {
  const Component = component
  const rootRoute = createRootRoute({ component: () => <Component /> })
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/'] }),
  })

  return render(<RouterProvider router={router} />)
}

describe('error pages', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
  })

  it('renders the forbidden page in the active language', async () => {
    const screen = await renderErrorPage(ForbiddenError)

    await expect
      .element(screen.getByRole('heading', { name: '无权访问' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '返回上一页' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '返回首页' }))
      .toBeVisible()
  })

  it.each([
    [UnauthorisedError, '需要登录', '前往登录'],
    [NotFoundError, '页面不存在', '返回首页'],
    [GeneralError, '服务暂时异常', '返回首页'],
    [MaintenanceError, '服务维护中', '重新加载'],
  ] as const)(
    'renders each status with localized content',
    async (Component, title, action) => {
      const screen = await renderErrorPage(Component)

      await expect
        .element(screen.getByRole('heading', { name: title }))
        .toBeVisible()
      await expect
        .element(screen.getByRole('button', { name: action }))
        .toBeVisible()
    }
  )

  it('updates content when the active language changes', async () => {
    await i18n.changeLanguage('en-US')
    const screen = await renderErrorPage(ForbiddenError)

    await expect
      .element(screen.getByRole('heading', { name: 'Access forbidden' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Go back' }))
      .toBeVisible()
  })
})

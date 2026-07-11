import { expect, test, type Browser } from '@playwright/test'
import { readScenarioState } from '../support/scenario-state'

const portalBaseUrl = requiredEnv('E2E_PORTAL_BASE_URL')

test.describe.configure({
  mode: 'serial',
})

test.describe('Phase 18 企业验收', () => {
  const state = readScenarioState()

  test('历史记录仍按 v1 展示禁用字典 label', async ({ browser }) => {
    const session = await authenticatedPage(browser, state.adminToken)

    try {
      await session.page.goto(`/work-records/${state.oldRecordId}`)

      await expect(
        session.page.getByText(state.oldTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText('工作总结', { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText('中（已禁用）', { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText('明日计划', { exact: true })
      ).not.toBeVisible()
    } finally {
      await session.context.close()
    }
  })

  test('新记录按 v2 展示新增字段', async ({ browser }) => {
    const session = await authenticatedPage(browser, state.adminToken)

    try {
      await session.page.goto(`/work-records/${state.newRecordId}`)

      await expect(
        session.page.getByText(state.newTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText('明日计划', { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText('继续完善 E2E', { exact: true })
      ).toBeVisible()
    } finally {
      await session.context.close()
    }
  })

  test('普通用户列表不能看到其他用户记录', async ({ browser }) => {
    const session = await authenticatedPage(browser, state.userAToken)

    try {
      await session.page.goto('/work-records')

      await expect(
        session.page.getByText(state.oldTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText(state.newTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText(state.otherTitle, { exact: true })
      ).not.toBeVisible()
    } finally {
      await session.context.close()
    }
  })

  test('管理员列表可以看到全部记录', async ({ browser }) => {
    const session = await authenticatedPage(browser, state.adminToken)

    try {
      await session.page.goto('/work-records')

      await expect(
        session.page.getByText(state.oldTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText(state.newTitle, { exact: true })
      ).toBeVisible()

      await expect(
        session.page.getByText(state.otherTitle, { exact: true })
      ).toBeVisible()
    } finally {
      await session.context.close()
    }
  })
})

async function authenticatedPage(browser: Browser, token: string) {
  const context = await browser.newContext()

  await context.addCookies([
    {
      name: 'thisisjustarandomstring',
      value: JSON.stringify(token),
      url: portalBaseUrl,
    },
  ])

  return {
    context,
    page: await context.newPage(),
  }
}

function requiredEnv(name: string) {
  const value = process.env[name]

  if (!value) {
    throw new Error(`${name} is required`)
  }

  return value
}

import {
  expect,
  test,
} from '../fixtures/enterprise.fixture'
import type { Browser } from '@playwright/test'

const portalBaseUrl = requiredEnv(
  'E2E_PORTAL_BASE_URL'
)

test.describe.configure({
  mode: 'serial',
})

test.describe('Phase 18 企业验收', () => {
  test(
    '历史记录仍按 v1 展示禁用字典 label',
    async ({
      browser,
      scenario,
    }) => {
      const session = await authenticatedPage(
        browser,
        scenario.adminToken
      )

      try {
        await session.page.goto(
          `/work-records/${scenario.oldRecordId}`
        )

        await expect(
          session.page.getByText(
            scenario.oldTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            '工作总结',
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            '中（已禁用）',
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            '明日计划',
            {
              exact: true,
            }
          )
        ).not.toBeVisible()
      } finally {
        await session.context.close()
      }
    }
  )

  test(
    '普通用户通过页面按 v2 填写并提交记录',
    async ({
      browser,
      scenario,
    }) => {
      const session = await authenticatedPage(
        browser,
        scenario.userAToken
      )

      try {
        await session.page.goto(
          '/work-records/new'
        )

        await session.page
          .getByLabel('模板')
          .selectOption({
            label: scenario.templateName,
          })

        await session.page
          .getByLabel('标题')
          .fill(scenario.newTitle)

        await session.page
          .getByLabel('工作总结')
          .fill('完成新版本记录')

        await session.page
          .getByLabel('优先级')
          .selectOption('P1')

        await session.page
          .getByLabel('工作时长')
          .fill('8')

        await session.page
          .getByLabel('明日计划')
          .fill('继续完善 E2E')

        await session.page
          .getByRole('button', {
            name: '提交',
            exact: true,
          })
          .click()

        await expect(
          session.page
        ).toHaveURL(
          /\/work-records\/[^/?]+$/
        )

        await expect(
          session.page.getByText(
            scenario.newTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            '明日计划',
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            '继续完善 E2E',
            {
              exact: true,
            }
          )
        ).toBeVisible()
      } finally {
        await session.context.close()
      }
    }
  )

  test(
    '普通用户列表只能看到当前场景自己的记录',
    async ({
      browser,
      scenario,
    }) => {
      const session = await authenticatedPage(
        browser,
        scenario.userAToken
      )

      try {
        await session.page.goto(
          `/work-records?keyword=${encodeURIComponent(
            scenario.runId
          )}`
        )

        await expect(
          session.page.getByText(
            scenario.oldTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            scenario.newTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            scenario.otherTitle,
            {
              exact: true,
            }
          )
        ).not.toBeVisible()
      } finally {
        await session.context.close()
      }
    }
  )

  test(
    '管理员列表可以看到当前场景全部记录',
    async ({
      browser,
      scenario,
    }) => {
      const session = await authenticatedPage(
        browser,
        scenario.adminToken
      )

      try {
        await session.page.goto(
          `/work-records?keyword=${encodeURIComponent(
            scenario.runId
          )}`
        )

        await expect(
          session.page.getByText(
            scenario.oldTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            scenario.newTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()

        await expect(
          session.page.getByText(
            scenario.otherTitle,
            {
              exact: true,
            }
          )
        ).toBeVisible()
      } finally {
        await session.context.close()
      }
    }
  )
})

async function authenticatedPage(
  browser: Browser,
  token: string
) {
  const context =
    await browser.newContext()

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

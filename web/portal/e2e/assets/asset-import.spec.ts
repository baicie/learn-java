import { expect, request, test, type Browser } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const portalBaseUrl = requiredEnv('E2E_PORTAL_BASE_URL')

test('管理员通过 Portal 下载模板、预检并导入 CSV 资源', async ({
  browser,
}) => {
  const token = await adminToken()
  const session = await authenticatedPage(browser, token)
  const runId = randomUUID().replaceAll('-', '').slice(0, 12)
  const assetName = `e2e-csv-host-${runId}`
  const machineId = `machine-${runId}`

  try {
    await session.page.goto('/assets')
    await session.page.getByRole('button', { name: '导入 CSV' }).click()

    const downloadPromise = session.page.waitForEvent('download')
    await session.page.getByRole('button', { name: '下载模板' }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toBe('asset-import-template.csv')

    const csv =
      'external_id,asset_type,name,display_name,environment,site,owner_team,criticality,ip,machine_id,cloud_instance_id,k8s_uid,tags\n' +
      `${assetName},host,${assetName},${assetName},production,shanghai,ops,normal,10.23.45.67,${machineId},,,role=e2e\n`
    await session.page.locator('input[type=file]').setInputFiles({
      name: `${assetName}.csv`,
      mimeType: 'text/csv',
      buffer: Buffer.from(csv, 'utf8'),
    })
    await session.page.getByRole('button', { name: '开始校验' }).click()

    await expect(session.page.getByText('预计新增')).toBeVisible()
    await expect(session.page.getByText(assetName, { exact: true })).toBeVisible()
    await session.page
      .getByRole('button', { name: '确认导入 1 条' })
      .click()
    await expect(session.page.getByRole('heading', { name: '导入完成' })).toBeVisible()
    await session.page.getByRole('button', { name: '完成', exact: true }).click()

    const search = session.page.getByPlaceholder('搜索名称、显示名称或 IP')
    await search.fill(assetName)
    await search.press('Enter')
    await session.page.getByRole('link', { name: assetName, exact: true }).click()

    await expect(session.page.getByRole('heading', { name: assetName })).toBeVisible()
    await expect(session.page.getByText('machine_id', { exact: true })).toBeVisible()
    await expect(session.page.getByText(machineId, { exact: true })).toBeVisible()
    await expect(session.page.getByText('CSV', { exact: true })).toBeVisible()
  } finally {
    await session.context.close()
  }
})

async function adminToken() {
  const api = await request.newContext({
    baseURL: requiredEnv('E2E_API_BASE_URL'),
  })
  try {
    const response = await api.post('/api/auth/login', {
      data: {
        username: requiredEnv('E2E_ADMIN_USERNAME'),
        password: requiredEnv('E2E_ADMIN_PASSWORD'),
      },
    })
    expect(response.ok()).toBeTruthy()
    const body = (await response.json()) as {
      data: { token: string }
    }
    return body.data.token
  } finally {
    await api.dispose()
  }
}

async function authenticatedPage(browser: Browser, token: string) {
  const context = await browser.newContext()
  await context.addCookies([
    {
      name: 'thisisjustarandomstring',
      value: JSON.stringify(token),
      url: portalBaseUrl,
    },
  ])
  return { context, page: await context.newPage() }
}

function requiredEnv(name: string) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

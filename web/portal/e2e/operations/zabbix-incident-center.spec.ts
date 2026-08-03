import { expect, test, type Response } from '@playwright/test'

type IncidentListResponse = {
  data: Array<{
    id: string
    title: string
  }>
}

type AuthorizationResponse = {
  data: {
    tenantId: string
    permissions: string[]
  }
}

const DEFAULT_Z9_INCIDENT_ID = 'inc_0b9d0aca0e24492ebe4e8f26f57422bb'

test.use({ storageState: { cookies: [], origins: [] } })

test('管理员登录后查看 Zabbix 告警和 Incident 详情 @smoke @z9', async ({
  page,
}) => {
  const consoleErrors: string[] = []
  const pageErrors: string[] = []
  const apiErrors: string[] = []

  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      apiErrors.push(
        `${response.status()} ${response.request().method()} ${response.url()}`
      )
    }
  })

  await page.goto('/sign-in')
  await page.getByLabel('账号').fill(requiredEnv('E2E_ADMIN_USERNAME'))
  await page.getByLabel('密码').fill(requiredEnv('E2E_ADMIN_PASSWORD'))

  const loginResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/auth/login') &&
      response.request().method() === 'POST'
  )
  const authorizationResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/auth/me') &&
      response.request().method() === 'GET'
  )
  const dashboardAlertsResponse = page.waitForResponse(isAlertsResponse)
  const dashboardIncidentsResponse = page.waitForResponse(isIncidentsResponse)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expectPageResponseOk(await loginResponse)
  const authorization = await authorizationResponse
  expectPageResponseOk(authorization)
  const principal = (await authorization.json()) as AuthorizationResponse
  expect(principal.data.tenantId).toBe('default')
  expect(principal.data.permissions).toEqual(
    expect.arrayContaining(['alert:read', 'incident:read'])
  )

  expectPageResponseOk(await dashboardAlertsResponse)
  const incidentListResponse = await dashboardIncidentsResponse
  expectPageResponseOk(incidentListResponse)
  const incidentList =
    (await incidentListResponse.json()) as IncidentListResponse
  expect(incidentList.data.length).toBeGreaterThan(0)

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('AIOps 故障态势', { exact: true })).toBeVisible()
  await expect(page.getByText('开放告警', { exact: true })).toBeVisible()
  await expect(page.getByText('活动 Incident', { exact: true })).toBeVisible()

  await page
    .getByRole('main')
    .getByRole('link', { name: '告警中心', exact: true })
    .click()
  await expect(page).toHaveURL(/\/alerts(?:\?|$)/)
  await expect(
    page.getByRole('heading', { name: '告警中心', exact: true })
  ).toBeVisible()
  await expect(page.getByRole('table')).toBeVisible()

  const representativeIncidentId =
    process.env.E2E_INCIDENT_ID ?? DEFAULT_Z9_INCIDENT_ID
  const incident = incidentList.data.find(
    ({ id }) => id === representativeIncidentId
  )
  expect(
    incident,
    `Representative Incident ${representativeIncidentId} was not returned by /api/incidents`
  ).toBeDefined()
  if (!incident) throw new Error('Representative Incident is required')

  await page.getByRole('link', { name: 'Incident', exact: true }).click()
  await expect(page).toHaveURL(/\/incidents(?:\?|$)/)
  await expect(
    page.getByRole('heading', { name: 'Incident 中心', exact: true })
  ).toBeVisible()
  await page.locator(`a[href='/incidents/${incident.id}']`).click()
  await expect(page).toHaveURL(new RegExp(`/incidents/${incident.id}$`))
  await expect(
    page.getByRole('heading', { name: incident.title, exact: true })
  ).toBeVisible()

  for (const name of [
    '概览',
    '时间线',
    'AI 诊断',
    'Runbook',
    '自动化日志',
    '复盘',
  ]) {
    await expect(page.getByRole('tab', { name, exact: true })).toBeVisible()
  }
  await expect(
    page.getByRole('tab', { name: 'Runbook', exact: true })
  ).toBeDisabled()
  await expect(
    page.getByRole('tab', { name: '自动化日志', exact: true })
  ).toBeDisabled()

  await page.getByRole('tab', { name: '时间线', exact: true }).click()
  await expect(
    page.getByRole('tabpanel').getByText('按时间排序的 Incident 证据事件。')
  ).toBeVisible()
  await page.getByRole('tab', { name: 'AI 诊断', exact: true }).click()
  await expect(
    page.getByRole('tabpanel').getByText('基于实际证据生成的诊断摘要。')
  ).toBeVisible()
  await page.getByRole('tab', { name: '复盘', exact: true }).click()
  await expect(
    page.getByRole('tabpanel').getByText('Incident 复盘报告的最新版本。')
  ).toBeVisible()

  expect(consoleErrors, `Console errors:\n${consoleErrors.join('\n')}`).toEqual(
    []
  )
  expect(pageErrors, `Page errors:\n${pageErrors.join('\n')}`).toEqual([])
  expect(apiErrors, `API error responses:\n${apiErrors.join('\n')}`).toEqual([])
})

function requiredEnv(name: string) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

function isAlertsResponse(response: Response) {
  return (
    response.url().endsWith('/api/alerts') &&
    response.request().method() === 'GET'
  )
}

function isIncidentsResponse(response: Response) {
  return (
    response.url().endsWith('/api/incidents') &&
    response.request().method() === 'GET'
  )
}

function expectPageResponseOk(response: Response) {
  expect(
    response.ok(),
    `${response.request().method()} ${response.url()} returned ${response.status()}`
  ).toBe(true)
}

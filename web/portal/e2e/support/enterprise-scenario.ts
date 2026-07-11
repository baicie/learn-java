import {
  expect,
  request,
  type APIRequestContext,
  type APIResponse,
} from '@playwright/test'
import type { EnterpriseScenarioState } from './scenario-state'

type Envelope<T> = {
  success: boolean
  data: T
  errorCode?: string
  message?: string
}

type Entity = {
  id: string
}

type LoginData = {
  token: string
}

type Version = {
  id: string
  versionNo: number
}

export async function seedEnterpriseScenario(): Promise<EnterpriseScenarioState> {
  const api = await request.newContext({
    baseURL: requiredEnv('E2E_API_BASE_URL'),
  })

  try {
    const adminToken = await login(
      api,
      requiredEnv('E2E_ADMIN_USERNAME'),
      requiredEnv('E2E_ADMIN_PASSWORD')
    )

    const userAToken = await login(
      api,
      requiredEnv('E2E_USER_A_USERNAME'),
      requiredEnv('E2E_USER_A_PASSWORD')
    )

    const userBToken = await login(
      api,
      requiredEnv('E2E_USER_B_USERNAME'),
      requiredEnv('E2E_USER_B_PASSWORD')
    )

    const suffix = Date.now().toString(36)
    const dictCode = `e2e_priority_${suffix}`

    await post<Entity>(api, '/api/platform/dictionaries', adminToken, {
      dictCode,
      dictName: 'E2E 优先级',
      enabled: true,
      sortOrder: 10,
    })

    await post<Entity>(
      api,
      `/api/platform/dictionaries/${dictCode}/items`,
      adminToken,
      {
        itemLabel: '高',
        itemValue: 'P1',
        enabled: true,
        sortOrder: 10,
        extraJson: '{}',
      }
    )

    const p2 = await post<Entity>(
      api,
      `/api/platform/dictionaries/${dictCode}/items`,
      adminToken,
      {
        itemLabel: '中',
        itemValue: 'P2',
        enabled: true,
        sortOrder: 20,
        extraJson: '{}',
      }
    )

    const template = await post<Entity>(
      api,
      '/api/work-record/templates',
      adminToken,
      {
        code: `e2e_daily_${suffix}`,
        name: 'E2E 企业日报',
        description: 'Phase 18 Playwright 场景',
        schemaJson: schemaV1(dictCode),
        designerJson: '{"version":1}',
      }
    )

    const v1 = await post<Version>(
      api,
      `/api/work-record/templates/${template.id}/publish`,
      adminToken,
      { versionName: 'v1' }
    )

    const oldTitle = `E2E 用户 A v1 ${suffix}`
    const otherTitle = `E2E 用户 B v1 ${suffix}`

    const oldRecord = await createRecord(
      api,
      userAToken,
      template.id,
      v1.id,
      oldTitle,
      {
        summary: '完成旧版本记录',
        priority: 'P2',
        hours: 7.5,
      }
    )

    const otherRecord = await createRecord(
      api,
      userBToken,
      template.id,
      v1.id,
      otherTitle,
      {
        summary: '其他用户记录',
        priority: 'P1',
        hours: 3,
      }
    )

    await put<Entity>(
      api,
      `/api/work-record/templates/${template.id}/draft`,
      adminToken,
      {
        name: 'E2E 企业日报 v2',
        description: '增加明日计划',
        schemaJson: schemaV2(dictCode),
        designerJson: '{"version":2}',
      }
    )

    const v2 = await post<Version>(
      api,
      `/api/work-record/templates/${template.id}/publish`,
      adminToken,
      { versionName: 'v2' }
    )

    const newTitle = `E2E 用户 A v2 ${suffix}`

    const newRecord = await createRecord(
      api,
      userAToken,
      template.id,
      v2.id,
      newTitle,
      {
        summary: '完成新版本记录',
        priority: 'P1',
        hours: 8,
        nextPlan: '继续完善 E2E',
      }
    )

    await remove(
      api,
      `/api/platform/dictionaries/${dictCode}/items/${p2.id}`,
      adminToken
    )

    return {
      adminToken,
      userAToken,
      userBToken,
      oldRecordId: oldRecord.id,
      newRecordId: newRecord.id,
      otherRecordId: otherRecord.id,
      oldTitle,
      newTitle,
      otherTitle,
    }
  } finally {
    await api.dispose()
  }
}

async function login(
  api: APIRequestContext,
  username: string,
  password: string
) {
  const response = await api.post('/api/auth/login', {
    data: { username, password },
  })

  return unwrap<LoginData>(response).then((data) => data.token)
}

async function createRecord(
  api: APIRequestContext,
  token: string,
  templateId: string,
  templateVersionId: string,
  title: string,
  customData: Record<string, unknown>
) {
  return post<Entity>(api, '/api/work-record/records', token, {
    templateId,
    templateVersionId,
    title,
    status: 'done',
    recordTime: new Date().toISOString(),
    builtinDataJson: '{}',
    customDataJson: JSON.stringify(customData),
  })
}

async function post<T>(
  api: APIRequestContext,
  path: string,
  token: string,
  data: unknown
) {
  return unwrap<T>(
    await api.post(path, {
      headers: auth(token),
      data,
    })
  )
}

async function put<T>(
  api: APIRequestContext,
  path: string,
  token: string,
  data: unknown
) {
  return unwrap<T>(
    await api.put(path, {
      headers: auth(token),
      data,
    })
  )
}

async function remove(api: APIRequestContext, path: string, token: string) {
  await unwrap<unknown>(
    await api.delete(path, {
      headers: auth(token),
    })
  )
}

async function unwrap<T>(response: APIResponse): Promise<T> {
  expect(response.ok()).toBeTruthy()

  const envelope = (await response.json()) as Envelope<T>

  expect(envelope.success).toBe(true)
  return envelope.data
}

function auth(token: string) {
  return {
    Authorization: `Bearer ${token}`,
  }
}

function requiredEnv(name: string) {
  const value = process.env[name]

  if (!value) {
    throw new Error(`${name} is required`)
  }

  return value
}

function schemaV1(dictCode: string) {
  return schema(dictCode, false)
}

function schemaV2(dictCode: string) {
  return schema(dictCode, true)
}

function schema(dictCode: string, includeNextPlan: boolean) {
  const required: string[] = ['summary', 'priority', 'hours']

  const properties: Record<string, unknown> = {
    summary: {
      type: 'string',
      title: '工作总结',
      'x-work-record': field('summary', 'textarea', 10),
    },
    priority: {
      type: 'string',
      title: '优先级',
      'x-work-record': {
        ...field('priority', 'select', 20),
        optionSource: 'dict',
        dictCode,
      },
    },
    hours: {
      type: 'number',
      title: '工作时长',
      'x-work-record': field('hours', 'number', 30),
    },
  }

  if (includeNextPlan) {
    required.push('nextPlan')

    properties.nextPlan = {
      type: 'string',
      title: '明日计划',
      'x-work-record': field('nextPlan', 'textarea', 40),
    }
  }

  return JSON.stringify({
    type: 'object',
    'x-work-record-schema-version': 1,
    required,
    properties,
  })
}

function field(fieldCode: string, fieldType: string, sortOrder: number) {
  return {
    fieldCode,
    fieldType,
    optionSource: 'static',
    listVisible: true,
    filterable: true,
    exportable: true,
    statistical: false,
    sortOrder,
  }
}

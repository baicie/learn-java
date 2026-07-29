import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { useDownloadZabbixWebhookTemplate } from './use-datasources'

const webhookToken = 'zwh_mutation-cache-secret'

vi.mock('@/api/datasources/datasources-api', () => ({
  createDatasource: vi.fn(),
  getZabbixWebhookToken: vi.fn(async () => ({ token: webhookToken })),
  listDatasources: vi.fn(),
  listSyncRuns: vi.fn(),
  syncDatasource: vi.fn(),
  testDatasource: vi.fn(),
  updateDatasource: vi.fn(),
}))

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useDownloadZabbixWebhookTemplate', () => {
  it('downloads the template without retaining its token in MutationCache', async () => {
    const client = new QueryClient({
      defaultOptions: {
        mutations: { retry: false },
      },
    })
    const createObjectURL = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:zabbix-template')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(
      () => undefined
    )
    const { result } = await renderHook(
      () => useDownloadZabbixWebhookTemplate(),
      {
        wrapper: ({ children }) =>
          createElement(QueryClientProvider, { client }, children),
      }
    )

    await result.current.mutateAsync('ds_zabbix_1')

    expect(result.current.data).toBeUndefined()
    expect(
      JSON.stringify(
        client
          .getMutationCache()
          .getAll()
          .map((mutation) => mutation.state.data)
      )
    ).not.toContain(webhookToken)
    expect(createObjectURL).toHaveBeenCalledOnce()
    const template = createObjectURL.mock.calls[0][0] as Blob
    await expect(template.text()).resolves.toContain(webhookToken)
  })
})

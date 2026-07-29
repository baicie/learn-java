import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Datasource } from '@/lib/datasources/datasource'
import { notify } from '@/components/feedback/app-toaster'
import { ZabbixWebhookDialog } from './zabbix-webhook-dialog'

const tokenMutate = vi.fn(
  (
    _datasourceId: string,
    options?: {
      onError?: (error: Error) => void
    }
  ) => {
    void options
  }
)

vi.mock('@/hooks/datasources/use-datasources', () => ({
  useDownloadZabbixWebhookTemplate: () => ({
    mutate: tokenMutate,
    isPending: false,
  }),
}))

vi.mock('@/components/feedback/app-toaster', () => ({
  notify: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

const datasource: Datasource = {
  id: 'ds_zabbix_1',
  tenantId: 'tenant_1',
  type: 'zabbix',
  name: '生产 Zabbix',
  endpoint: 'https://zabbix.example/api_jsonrpc.php',
  status: 'active',
  createdAt: '2026-07-18T00:00:00Z',
  updatedAt: '2026-07-18T00:00:00Z',
  lastSyncAt: null,
}

afterEach(() => {
  vi.restoreAllMocks()
})

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ZabbixWebhookDialog', () => {
  it('shows the complete URL, copy action, template download and setup guide', async () => {
    const screen = await render(
      <ZabbixWebhookDialog
        datasource={datasource}
        open
        onOpenChange={vi.fn()}
      />
    )

    await expect
      .element(screen.getByLabelText('Webhook 地址', { exact: true }))
      .toHaveValue(
        `${window.location.origin}/api/integrations/zabbix/events?datasourceId=ds_zabbix_1`
      )
    await expect
      .element(screen.getByRole('button', { name: '复制 Webhook 地址' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '下载配置模板' }))
      .toBeVisible()
    await expect.element(screen.getByText(/Alerts → Media types/)).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Webhook 配置说明' }))
      .toBeVisible()
  })

  it('copies the complete webhook URL', async () => {
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockResolvedValue()
    const screen = await render(
      <ZabbixWebhookDialog
        datasource={datasource}
        open
        onOpenChange={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '复制 Webhook 地址' }).click()

    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}/api/integrations/zabbix/events?datasourceId=ds_zabbix_1`
    )
    expect(notify.success).toHaveBeenCalledWith('Webhook 地址已复制')
  })

  it('requests a Zabbix 7.0 media type template download', async () => {
    const screen = await render(
      <ZabbixWebhookDialog
        datasource={datasource}
        open
        onOpenChange={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '下载配置模板' }).click()

    expect(tokenMutate).toHaveBeenCalledWith(
      'ds_zabbix_1',
      expect.objectContaining({
        onError: expect.any(Function),
      })
    )
  })

  it('does not create a template when token retrieval fails', async () => {
    const error = new Error('request failed')
    tokenMutate.mockImplementationOnce((_datasourceId, options) => {
      options?.onError?.(error)
    })
    const createObjectURL = vi.spyOn(URL, 'createObjectURL')
    const screen = await render(
      <ZabbixWebhookDialog
        datasource={datasource}
        open
        onOpenChange={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '下载配置模板' }).click()

    expect(createObjectURL).not.toHaveBeenCalled()
    expect(notify.error).toHaveBeenCalledWith(error, '获取 Webhook Token 失败')
  })
})

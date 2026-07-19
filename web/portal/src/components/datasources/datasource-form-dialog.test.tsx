import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import type { Datasource } from '@/lib/datasources/datasource'
import { DatasourceFormDialog } from './datasource-form-dialog'

const createMutate = vi.fn()
const updateMutate = vi.fn()

vi.mock('@/hooks/datasources/use-datasources', () => ({
  useCreateDatasource: () => ({ mutate: createMutate, isPending: false }),
  useUpdateDatasource: () => ({ mutate: updateMutate, isPending: false }),
}))

vi.mock('@/components/feedback/app-toaster', () => ({
  notify: { success: vi.fn(), error: vi.fn() },
}))

const datasource: Datasource = {
  id: 'ds-1',
  tenantId: 'tenant-1',
  type: 'zabbix',
  name: '生产 Zabbix',
  endpoint: 'https://old.example/api_jsonrpc.php',
  status: 'active',
  createdAt: '2026-07-16T00:00:00Z',
  updatedAt: '2026-07-16T00:00:00Z',
  lastSyncAt: null,
}

describe('DatasourceFormDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('edits public fields while allowing stored credentials to stay unchanged', async () => {
    const screen = await render(
      <DatasourceFormDialog
        open
        datasource={datasource}
        onOpenChange={vi.fn()}
      />
    )

    expect(screen.getByLabelText('类型')).toBeDisabled()
    await userEvent.fill(screen.getByLabelText('名称'), '新名称')
    await userEvent.fill(
      screen.getByLabelText('Endpoint'),
      'https://new.example/api_jsonrpc.php'
    )
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }))

    expect(updateMutate).toHaveBeenCalledWith(
      {
        id: 'ds-1',
        input: {
          name: '新名称',
          zabbix: {
            endpoint: 'https://new.example/api_jsonrpc.php',
          },
        },
      },
      expect.any(Object)
    )
    expect(createMutate).not.toHaveBeenCalled()
  })
})

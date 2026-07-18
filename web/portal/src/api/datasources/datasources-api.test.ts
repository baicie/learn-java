import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import {
  listDatasources,
  syncDatasource,
  testDatasource,
  updateDatasource,
} from './datasources-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}))

describe('datasource api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('accepts NON_NULL list payloads with omitted optional fields', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        success: true,
        data: [
          {
            id: 'ds-1',
            tenantId: 'tenant-1',
            type: 'zabbix',
            name: '生产 Zabbix',
            status: 'active',
            createdAt: '2026-07-16T00:00:00Z',
            updatedAt: '2026-07-16T00:00:00Z',
          },
        ],
      },
    })

    await expect(listDatasources()).resolves.toHaveLength(1)
  })

  it('parses the asynchronous sync acknowledgement', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        success: true,
        data: { runId: 'run-1', status: 'pending' },
      },
    })

    await expect(syncDatasource('ds-1')).resolves.toEqual({
      runId: 'run-1',
      status: 'pending',
    })
    expect(apiClient.post).toHaveBeenCalledWith('/api/datasources/ds-1/sync')
  })

  it('parses the backend connection test contract', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        success: true,
        data: {
          ok: true,
          message: 'zabbix connection succeeded',
          version: '6.0.47',
        },
      },
    })

    await expect(testDatasource('ds-1')).resolves.toEqual({
      ok: true,
      message: 'zabbix connection succeeded',
      version: '6.0.47',
    })
  })

  it('updates a datasource without requiring a replacement secret', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({
      data: {
        success: true,
        data: {
          id: 'ds-1',
          tenantId: 'tenant-1',
          type: 'zabbix',
          name: '新名称',
          endpoint: 'https://new.example/api_jsonrpc.php',
          status: 'inactive',
          createdAt: '2026-07-16T00:00:00Z',
          updatedAt: '2026-07-18T00:00:00Z',
        },
      },
    })

    await expect(
      updateDatasource('ds-1', {
        name: '新名称',
        zabbix: {
          endpoint: 'https://new.example/api_jsonrpc.php',
        },
      })
    ).resolves.toMatchObject({ id: 'ds-1', name: '新名称' })
    expect(apiClient.put).toHaveBeenCalledWith('/api/datasources/ds-1', {
      name: '新名称',
      zabbix: {
        endpoint: 'https://new.example/api_jsonrpc.php',
      },
    })
  })
})

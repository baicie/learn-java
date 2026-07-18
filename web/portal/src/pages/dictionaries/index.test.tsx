import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { DictionariesPage } from './index'

vi.mock('@/api/dictionaries', () => ({
  listDictTypes: async () => [
    {
      id: 'dict-1',
      dictCode: 'severity',
      dictName: '严重级别',
      description: '',
      sortOrder: 1,
      enabled: true,
      systemBuiltin: false,
    },
  ],
  listDictItems: async () => [
    {
      id: 'item-1',
      itemLabel: '严重',
      itemValue: 'critical',
      description: '',
      sortOrder: 1,
      enabled: true,
    },
  ],
  createDictType: vi.fn(),
  updateDictType: vi.fn(),
  disableDictType: vi.fn(),
  createDictItem: vi.fn(),
  updateDictItem: vi.fn(),
  disableDictItem: vi.fn(),
}))

describe('DictionariesPage permissions', () => {
  it('keeps read-only actions but hides every write action from readers', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'reader',
      tenantId: 'tenant-1',
      username: 'reader',
      displayName: 'Reader',
      roles: [],
      permissions: ['platform:dict:read'],
      dataScopes: {},
    })

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DictionariesPage />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('严重级别').first()).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '导出' }))
      .toBeVisible()
    for (const name of [
      '新增',
      '编辑 严重级别',
      '导入',
      '禁用字典',
      '新增字段',
      '编辑',
      '禁用',
    ]) {
      await expect
        .element(screen.getByRole('button', { name }))
        .not.toBeInTheDocument()
    }
  })
})

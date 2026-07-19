import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { deleteDictItem, updateDictType } from '@/api/dictionaries'
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
    {
      id: 'dict-2',
      dictCode: 'status',
      dictName: '状态',
      description: '',
      sortOrder: 2,
      enabled: false,
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
  updateDictType: vi.fn().mockResolvedValue(undefined),
  disableDictType: vi.fn(),
  createDictItem: vi.fn(),
  updateDictItem: vi.fn(),
  deleteDictItem: vi.fn(),
}))

describe('DictionariesPage permissions', () => {
  it('enables a disabled option set without opening the editor', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'writer',
      tenantId: 'tenant-1',
      username: 'writer',
      displayName: 'Writer',
      roles: [],
      permissions: ['platform:dict:write'],
      dataScopes: {},
    })

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DictionariesPage />
      </QueryClientProvider>
    )

    await screen.getByRole('button', { name: /^状态/ }).click()
    await screen.getByRole('button', { name: '启用字典' }).click()

    await vi.waitFor(() => {
      expect(updateDictType).toHaveBeenCalledWith('status', { enabled: true })
    })
  })

  it('confirms deletion and soft deletes a dictionary item', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'writer',
      tenantId: 'tenant-1',
      username: 'writer',
      displayName: 'Writer',
      roles: [],
      permissions: ['platform:dict:write'],
      dataScopes: {},
    })

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <DictionariesPage />
      </QueryClientProvider>
    )

    const deleteButton = screen.getByRole('button', { name: '删除 严重' })
    await expect.element(deleteButton).toBeVisible()
    await deleteButton.click()
    await screen.getByRole('button', { name: '确认删除' }).click()

    await vi.waitFor(() => {
      expect(deleteDictItem).toHaveBeenCalledWith('severity', 'item-1')
    })
  })

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
      '启用字典',
      '新增字段',
      '编辑',
      '删除 严重',
    ]) {
      await expect
        .element(screen.getByRole('button', { name }))
        .not.toBeInTheDocument()
    }
  })
})

import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DictionaryPage } from './DictionaryPage'
import { renderWithRouter } from '../../test/test-utils'

vi.mock('../../features/platform-dictionary/api', () => ({
  listDictionaries: vi.fn(async () => [
    {
      id: 'dt1',
      tenantId: 't1',
      dictCode: 'record_status',
      dictName: '工作记录状态',
      description: '状态枚举',
      enabled: true,
      sortOrder: 0,
    },
  ]),
  listDictItems: vi.fn(async () => [
    {
      id: 'di1',
      tenantId: 't1',
      dictTypeId: 'dt1',
      itemLabel: '草稿',
      itemValue: 'draft',
      color: null,
      icon: null,
      description: null,
      enabled: true,
      sortOrder: 0,
      extraJson: '{}',
    },
  ]),
  createDictType: vi.fn(),
  createDictItem: vi.fn(),
}))

describe('DictionaryPage', () => {
  it('renders dict types and items', async () => {
    renderWithRouter(<DictionaryPage />)

    expect(await screen.findByText('工作记录状态')).toBeInTheDocument()
    expect(await screen.findByText('草稿')).toBeInTheDocument()
  })
})

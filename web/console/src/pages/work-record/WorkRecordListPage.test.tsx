import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { WorkRecordListPage } from './WorkRecordListPage'
import { renderWithRouter } from '../../test/test-utils'

vi.mock('../../features/work-record/api', () => ({
  listWorkRecords: vi.fn(async () => ({
    total: 1,
    page: 1,
    size: 20,
    items: [
      {
        id: 'r1',
        tenantId: 't1',
        templateId: 'tpl1',
        title: '数据库巡检',
        status: 'done',
        ownerId: null,
        creatorId: 'u1',
        recordTime: '2026-07-06T10:00:00Z',
        builtinDataJson: '{}',
        customDataJson: '{}',
        createdAt: '2026-07-06T10:00:00Z',
        updatedAt: '2026-07-06T10:00:00Z',
      },
    ],
  })),
  createWorkRecord: vi.fn(),
  deleteWorkRecord: vi.fn(),
  exportWorkRecordsCsv: vi.fn(),
}))

describe('WorkRecordListPage', () => {
  it('renders work records', async () => {
    renderWithRouter(<WorkRecordListPage />)

    expect(await screen.findByText('数据库巡检')).toBeInTheDocument()
    expect(screen.getByText('done')).toBeInTheDocument()
    expect(screen.getByText('导出 CSV')).toBeInTheDocument()
    expect(screen.getByText('新建记录')).toBeInTheDocument()
  })
})

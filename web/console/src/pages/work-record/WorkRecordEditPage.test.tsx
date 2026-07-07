import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { WorkRecordEditPage } from './WorkRecordEditPage'
import { renderWithRouter } from '../../test/test-utils'

vi.mock('../../features/work-record/api', () => ({
  listTemplates: vi.fn(async () => [
    {
      id: 'tpl1',
      tenantId: 't1',
      name: '日常记录',
      code: 'daily',
      description: '',
      enabled: true,
      schemaJson: '{}',
      createdBy: 'system',
      createdAt: '2026-07-06T10:00:00Z',
      updatedAt: '2026-07-06T10:00:00Z',
    },
  ]),
  listTemplateFields: vi.fn(async () => [
    {
      id: 'f1',
      tenantId: 't1',
      templateId: 'tpl1',
      fieldName: '巡检人',
      fieldCode: 'inspector',
      fieldType: 'text',
      required: true,
      defaultValue: null,
      optionSource: 'static',
      dictCode: null,
      optionsJson: '[]',
      listVisible: true,
      filterable: false,
      statistical: false,
      sortOrder: 0,
      enabled: true,
    },
  ]),
  createWorkRecord: vi.fn(),
  updateWorkRecord: vi.fn(),
  getWorkRecord: vi.fn(),
}))

describe('WorkRecordEditPage', () => {
  it('renders the dynamic field editor', async () => {
    renderWithRouter(<WorkRecordEditPage initialTemplateId="tpl1" />)

    expect(await screen.findByText('基础字段')).toBeInTheDocument()
    expect(await screen.findByText('巡检人')).toBeInTheDocument()
    expect(screen.getByText('模板字段')).toBeInTheDocument()
    expect(screen.getByText('保存草稿')).toBeInTheDocument()
  })
})

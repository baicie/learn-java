import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { WorkRecordTemplateDesignerPage } from './WorkRecordTemplateDesignerPage'
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
  createTemplate: vi.fn(),
  createTemplateField: vi.fn(),
}))

describe('WorkRecordTemplateDesignerPage', () => {
  it('renders palette and templates', async () => {
    renderWithRouter(<WorkRecordTemplateDesignerPage />)

    expect(await screen.findByText('日常记录')).toBeInTheDocument()
    expect(await screen.findByText('模板字段配置')).toBeInTheDocument()
    expect(await screen.findByText('字段组件')).toBeInTheDocument()
    expect(await screen.findByText('单行文本')).toBeInTheDocument()
  })
})

import '@testing-library/jest-dom'
import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { InspectionTasksPage } from '../pages/inspections/InspectionTasksPage'
import { renderWithRouter } from '../test/test-utils'

vi.mock('../api/client', () => ({
  listInspectionTasks: async () => [
    {
      id: 'it-1',
      tenantId: 't1',
      name: '主机基础巡检',
      targetType: 'HOST',
      targetQueryJson: '{}',
      templateKey: 'host-basic',
      enabled: true,
      createdBy: 'u1',
      createdAt: '2026-07-02T00:00:00Z',
      updatedAt: '2026-07-02T00:00:00Z',
    },
    {
      id: 'it-2',
      tenantId: 't1',
      name: 'K8s 节点巡检',
      targetType: 'K8S_NODE',
      targetQueryJson: '{"cluster":"prod"}',
      templateKey: 'k8s-node',
      enabled: true,
      createdBy: 'u1',
      createdAt: '2026-07-02T01:00:00Z',
      updatedAt: '2026-07-02T01:00:00Z',
    },
  ],
}))

describe('InspectionTasksPage', () => {
  it('renders page title and description', async () => {
    renderWithRouter(<InspectionTasksPage />)
    expect(await screen.findByText('巡检任务')).toBeInTheDocument()
    expect(screen.getByText('主动巡检主机、服务、Docker、K8s 和外部监控指标。')).toBeInTheDocument()
  })

  it('renders inspection task list', async () => {
    renderWithRouter(<InspectionTasksPage />)
    expect(await screen.findByText('主机基础巡检')).toBeInTheDocument()
    expect(screen.getByText('HOST / host-basic')).toBeInTheDocument()
    expect(screen.getByText('K8s 节点巡检')).toBeInTheDocument()
    expect(screen.getByText('K8S_NODE / k8s-node')).toBeInTheDocument()
  })

  it('does not render empty state when tasks exist', async () => {
    renderWithRouter(<InspectionTasksPage />)
    expect(await screen.findByText('主机基础巡检')).toBeInTheDocument()
    expect(screen.queryByText('暂无巡检任务')).not.toBeInTheDocument()
  })
})

import { describe, expect, it } from 'vitest'
import type { AlertEvent } from '@/lib/operations/operations'
import { filterAlerts, paginateAlerts } from './alert-filter'

const alerts: AlertEvent[] = [
  {
    id: 'alert-1',
    tenantId: 'tenant-1',
    source: 'zabbix',
    severity: 'critical',
    title: 'Checkout API is unavailable',
    status: 'open',
    startsAt: '2026-08-03T05:30:00Z',
    createdAt: '2026-08-03T05:30:00Z',
  },
  {
    id: 'alert-2',
    tenantId: 'tenant-1',
    source: 'zabbix',
    severity: 'warning',
    title: 'Database connection pool is high',
    status: 'resolved',
    startsAt: '2026-08-02T05:30:00Z',
    createdAt: '2026-08-02T05:30:00Z',
  },
  {
    id: 'alert-3',
    tenantId: 'tenant-1',
    source: 'prometheus',
    severity: 'high',
    title: 'Checkout API latency increased',
    status: 'open',
    startsAt: '2026-08-01T05:30:00Z',
    createdAt: '2026-08-01T05:30:00Z',
  },
]

describe('alert filtering', () => {
  it('matches keyword, status, and severity together, ignoring case', () => {
    expect(
      filterAlerts(alerts, {
        keyword: 'checkout',
        statuses: ['open'],
        severities: ['critical', 'high'],
      })
    ).toEqual([alerts[0], alerts[2]])
  })

  it('returns the selected page without changing the source order', () => {
    expect(paginateAlerts(alerts, { page: 2, pageSize: 1 })).toEqual({
      items: [alerts[1]],
      total: 3,
      pageCount: 3,
    })
  })
})

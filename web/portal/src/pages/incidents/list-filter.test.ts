import { describe, expect, it } from 'vitest'
import type { Incident } from '@/lib/operations/operations'
import { filterIncidents, paginateIncidents } from './list-filter'

const incidents: Incident[] = [
  {
    id: 'inc-1',
    tenantId: 'tenant-1',
    title: 'Checkout API unavailable',
    summary: 'Requests are failing at the edge.',
    severity: 'critical',
    status: 'open',
    source: 'zabbix',
    primaryAssetId: 'asset-1',
    aggregationKey: 'checkout-api',
    alertCount: 4,
    startedAt: '2026-08-03T05:30:00Z',
    detectedAt: '2026-08-03T05:30:00Z',
    lastSeenAt: '2026-08-03T05:36:00Z',
    resolvedAt: null,
    createdAt: '2026-08-03T05:30:00Z',
    updatedAt: '2026-08-03T05:36:00Z',
  },
  {
    id: 'inc-2',
    tenantId: 'tenant-1',
    title: 'Database connection saturation',
    summary: null,
    severity: 'high',
    status: 'resolved',
    source: 'zabbix',
    primaryAssetId: null,
    aggregationKey: null,
    alertCount: 2,
    startedAt: '2026-08-02T05:30:00Z',
    detectedAt: '2026-08-02T05:30:00Z',
    lastSeenAt: null,
    resolvedAt: '2026-08-02T06:00:00Z',
    createdAt: '2026-08-02T05:30:00Z',
    updatedAt: '2026-08-02T06:00:00Z',
  },
  {
    id: 'inc-3',
    tenantId: 'tenant-1',
    title: 'Checkout latency increased',
    summary: 'Upstream dependency is slow.',
    severity: 'warning',
    status: 'investigating',
    source: 'prometheus',
    primaryAssetId: 'asset-2',
    aggregationKey: 'checkout-latency',
    alertCount: 1,
    startedAt: '2026-08-01T05:30:00Z',
    detectedAt: '2026-08-01T05:30:00Z',
    lastSeenAt: null,
    resolvedAt: null,
    createdAt: '2026-08-01T05:30:00Z',
    updatedAt: '2026-08-01T05:40:00Z',
  },
]

describe('incident list filtering', () => {
  it('matches keyword, status, severity, and source together without changing order', () => {
    expect(
      filterIncidents(incidents, {
        keyword: 'checkout',
        status: 'open',
        severity: 'critical',
        source: 'zabbix',
      })
    ).toEqual([incidents[0]])
  })

  it('returns the selected page and total after filtering', () => {
    expect(paginateIncidents(incidents, { page: 2, pageSize: 1 })).toEqual({
      items: [incidents[1]],
      total: 3,
      pageCount: 3,
    })
  })
})

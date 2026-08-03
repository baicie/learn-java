import { describe, expect, it } from 'vitest'
import { operationsKeys } from './query-keys'

describe('operations query keys', () => {
  it('keeps every resource under a tenant-scoped cache key', () => {
    expect(operationsKeys.alerts('tenant-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'alerts',
    ])
    expect(operationsKeys.incidents('tenant-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
    ])
    expect(operationsKeys.incident('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
    ])
    expect(operationsKeys.incidentAlerts('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'alerts',
    ])
    expect(operationsKeys.incidentTimeline('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'timeline',
    ])
    expect(operationsKeys.incidentEvidence('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'evidence',
    ])
    expect(operationsKeys.incidentRca('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'rca',
      'latest',
    ])
    expect(operationsKeys.incidentAiDiagnosis('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'ai',
      'latest',
    ])
    expect(operationsKeys.incidentReport('tenant-1', 'inc-1')).toEqual([
      'operations',
      'tenant',
      'tenant-1',
      'incidents',
      'inc-1',
      'reports',
      'latest',
    ])
  })

  it('does not collide across tenants for the same incident id', () => {
    expect(operationsKeys.incident('tenant-1', 'inc-1')).not.toEqual(
      operationsKeys.incident('tenant-2', 'inc-1')
    )
  })
})

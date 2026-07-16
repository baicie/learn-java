export const datasourceKeys = {
  all: ['datasources'] as const,
  lists: () => [...datasourceKeys.all, 'list'] as const,
  syncRuns: (id: string) => [...datasourceKeys.all, id, 'sync-runs'] as const,
}

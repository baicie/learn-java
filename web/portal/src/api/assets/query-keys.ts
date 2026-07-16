export const assetKeys = {
  all: ['assets'] as const,
  lists: () => [...assetKeys.all, 'list'] as const,
  list: (search: object) => [...assetKeys.lists(), search] as const,
  details: () => [...assetKeys.all, 'detail'] as const,
  detail: (id: string) => [...assetKeys.details(), id] as const,
  imports: () => [...assetKeys.all, 'imports'] as const,
  import: (id: string) => [...assetKeys.imports(), id] as const,
}

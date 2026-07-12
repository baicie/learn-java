export const platformUserKeys = {
  all: ['platform-users'] as const,
  lists: () => [...platformUserKeys.all, 'list'] as const,
  list: (query: unknown) => [...platformUserKeys.lists(), query] as const,
  detail: (id: string) => [...platformUserKeys.all, 'detail', id] as const,
}

export const platformRoleKeys = {
  all: ['platform-roles'] as const,
  lists: () => [...platformRoleKeys.all, 'list'] as const,
  detail: (code: string) => [...platformRoleKeys.all, 'detail', code] as const,
  permissions: ['platform-permissions'] as const,
}
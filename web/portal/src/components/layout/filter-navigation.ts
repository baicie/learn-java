import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import type { NavigationItem } from './navigation'

export function filterNavigation(
  items: NavigationItem[],
  principal: AuthorizationPrincipal | null
): NavigationItem[] {
  const permissions = new Set(principal?.permissions ?? [])

  return items.flatMap((item) => {
    const allowed =
      !item.anyPermissions?.length ||
      item.anyPermissions.some((permission) => permissions.has(permission))

    const children = item.children
      ? filterNavigation(item.children, principal)
      : undefined

    if (!allowed && !children?.length) return []

    return [{ ...item, children }]
  })
}

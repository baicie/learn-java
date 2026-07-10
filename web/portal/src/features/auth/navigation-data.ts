export type PermissionNavigationItem = {
  title: string
  href: string
  icon?: React.ComponentType
  anyPermissions?: string[]
  allPermissions?: string[]
}

export const workRecordNavigation: PermissionNavigationItem[] = [
  {
    title: '工作记录',
    href: '/work-records',
    anyPermissions: ['work-record:read:self', 'work-record:read:all'],
  },
  {
    title: '模板管理',
    href: '/work-records/designer',
    anyPermissions: ['work-record:template:read'],
  },
  {
    title: '字典管理',
    href: '/platform/dictionaries',
    anyPermissions: ['platform:dict:read'],
  },
  {
    title: '工作日历',
    href: '/platform/work-calendar',
    anyPermissions: ['platform:calendar:read'],
  },
]

export function filterNavigation(
  items: PermissionNavigationItem[],
  permissions: string[]
) {
  return items.filter((item) => {
    const any = item.anyPermissions ?? []

    const all = item.allPermissions ?? []

    const anyAllowed =
      any.length === 0 ||
      any.some((permission) => permissions.includes(permission))

    const allAllowed = all.every((permission) =>
      permissions.includes(permission)
    )

    return anyAllowed && allAllowed
  })
}

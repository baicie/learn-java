import {
  CalendarDays,
  ClipboardList,
  LayoutDashboard,
  Library,
  ListChecks,
  ScrollText,
  Settings2,
  ShieldCheck,
  Users,
} from 'lucide-react'

export type NavigationItem = {
  titleKey: string
  to?: string
  icon?: React.ComponentType<{ className?: string }>
  anyPermissions?: string[]
  children?: NavigationItem[]
}

export const navigation: NavigationItem[] = [
  {
    titleKey: 'nav.dashboard',
    to: '/',
    icon: LayoutDashboard,
  },
  {
    titleKey: 'nav.workRecords.group',
    icon: ClipboardList,
    children: [
      {
        titleKey: 'nav.workRecords.records',
        to: '/work-records',
        icon: ListChecks,
        anyPermissions: ['work-record:read:self', 'work-record:read:all'],
      },
      {
        titleKey: 'nav.workRecords.designer',
        to: '/work-records/designer',
        icon: Library,
        anyPermissions: ['work-record:template:read'],
      },
    ],
  },
  {
    titleKey: 'nav.platform.group',
    icon: Settings2,
    children: [
      {
        titleKey: 'nav.platform.users',
        to: '/platform/users',
        icon: Users,
        anyPermissions: ['platform:user:read'],
      },
      {
        titleKey: 'nav.platform.roles',
        to: '/platform/roles',
        icon: ShieldCheck,
        anyPermissions: ['platform:role:read'],
      },
      {
        titleKey: 'nav.platform.dictionaries',
        to: '/platform/dictionaries',
        icon: Library,
        anyPermissions: ['platform:dict:read'],
      },
      {
        titleKey: 'nav.platform.calendars',
        to: '/platform/calendars',
        icon: CalendarDays,
        anyPermissions: ['platform:calendar:read'],
      },
      {
        titleKey: 'nav.platform.audit',
        to: '/platform/audit-logs',
        icon: ScrollText,
        anyPermissions: ['platform:audit:read'],
      },
    ],
  },
] as const satisfies NavigationItem[]

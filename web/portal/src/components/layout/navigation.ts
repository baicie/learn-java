import {
  CalendarDays,
  Boxes,
  ClipboardList,
  LayoutDashboard,
  ChartNoAxesCombined,
  Library,
  ListChecks,
  Settings2,
  DatabaseZap,
  PlugZap,
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
    titleKey: 'nav.resources.group',
    icon: DatabaseZap,
    children: [
      {
        titleKey: 'nav.resources.datasources',
        to: '/datasources',
        icon: PlugZap,
        anyPermissions: ['datasource:read'],
      },
      {
        titleKey: 'nav.resources.assets',
        to: '/assets',
        icon: Boxes,
        anyPermissions: ['asset:read'],
      },
    ],
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
        titleKey: 'nav.workRecords.operations',
        to: '/work-records/operations',
        icon: ChartNoAxesCombined,
        anyPermissions: [
          'work-record:analytics',
          'work-record:handover',
          'work-record:ai:generate',
          'work-record:approval:act',
        ],
      },
    ],
  },
  {
    titleKey: 'nav.platform.group',
    icon: Settings2,
    children: [
      {
        titleKey: 'nav.workRecords.designer',
        to: '/work-records/templates',
        icon: Library,
        anyPermissions: ['work-record:template:read'],
      },
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
    ],
  },
] as const satisfies NavigationItem[]

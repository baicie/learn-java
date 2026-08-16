import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { canAccessWorkRecordOperations } from '@/auth/work-record-access'
import {
  BellRing,
  CalendarDays,
  BrainCircuit,
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
  Siren,
  Users,
} from 'lucide-react'

export type NavigationItem = {
  titleKey: string
  to?: string
  icon?: React.ComponentType<{ className?: string }>
  anyPermissions?: string[]
  isAllowed?: (principal: AuthorizationPrincipal | null) => boolean
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
    titleKey: 'nav.incidentCenter.group',
    icon: Siren,
    children: [
      {
        titleKey: 'nav.incidentCenter.alerts',
        to: '/alerts',
        icon: BellRing,
        anyPermissions: ['alert:read'],
      },
      {
        titleKey: 'nav.incidentCenter.incidents',
        to: '/incidents',
        icon: Siren,
        anyPermissions: ['incident:read'],
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
        isAllowed: canAccessWorkRecordOperations,
      },
    ],
  },
  {
    titleKey: 'nav.platform.group',
    icon: Settings2,
    children: [
      {
        titleKey: 'nav.platform.aiModels',
        to: '/platform/ai-models',
        icon: BrainCircuit,
        anyPermissions: ['admin:manage'],
      },
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

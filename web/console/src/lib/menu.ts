import {
  BellIcon,
  BlocksIcon,
  DatabaseIcon,
  FileTextIcon,
  LayoutDashboardIcon,
  PlugIcon,
  ScrollTextIcon,
  SearchCheckIcon,
  ShieldIcon,
  SirenIcon,
  UsersIcon,
  type LucideIcon,
} from 'lucide-react'

import type { PlatformMenuItem } from '@/api/client'

export type MenuNode = PlatformMenuItem & {
  children: MenuNode[]
}

export const MENU_ICON_MAP: Record<string, LucideIcon> = {
  'layout-dashboard': LayoutDashboardIcon,
  bell: BellIcon,
  siren: SirenIcon,
  database: DatabaseIcon,
  'file-text': FileTextIcon,
  'search-check': SearchCheckIcon,
  plug: PlugIcon,
  users: UsersIcon,
  shield: ShieldIcon,
  blocks: BlocksIcon,
  'scroll-text': ScrollTextIcon,
}

export function resolveMenuIcon(name: string | null | undefined): LucideIcon | null {
  if (!name) return null
  return MENU_ICON_MAP[name] ?? null
}

export function buildMenuTree({ nodes }: { nodes: PlatformMenuItem[] }): MenuNode[] {
  const sorted = [...nodes].sort((a, b) => a.sortOrder - b.sortOrder)
  const byId = new Map<string, MenuNode>()
  const roots: MenuNode[] = []

  for (const node of sorted) {
    byId.set(node.id, { ...node, children: [] })
  }
  for (const node of sorted) {
    const current = byId.get(node.id)!
    const parentId = node.parentId
    if (parentId && byId.has(parentId)) {
      byId.get(parentId)!.children.push(current)
    } else {
      roots.push(current)
    }
  }
  return roots
}

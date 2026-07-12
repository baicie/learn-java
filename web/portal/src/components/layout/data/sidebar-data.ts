import {
  AudioWaveform,
  Command,
  GalleryVerticalEnd,
} from 'lucide-react'
import { t, type MessageKey } from '@/i18n'
import { filterNavigation } from '@/components/layout/filter-navigation'
import { navigation } from '@/components/layout/navigation'
import { useAuthStore } from '@/stores/auth-store'
import type { NavGroup, SidebarData } from '@/components/layout/types'

function translate(key: string): string {
  return t(key as MessageKey) || key
}

function buildNavGroups(): NavGroup[] {
  const principal = useAuthStore.getState().auth.principal

  const visible = filterNavigation(navigation, principal)

  const groups: NavGroup[] = []

  for (const item of visible) {
    if (item.to && !item.children?.length) {
      groups.push({
        title: translate(item.titleKey),
        items: [
          {
            title: () => translate(item.titleKey),
            icon: item.icon,
            url: item.to,
          },
        ],
      })
      continue
    }

    if (!item.children?.length) continue

    groups.push({
      title: translate(item.titleKey),
      items: item.children.map((child) => ({
        title: () => translate(child.titleKey),
        icon: child.icon,
        url: child.to ?? '',
      })),
    })
  }

  return groups
}

export const sidebarData: SidebarData = {
  user: {
    name: 'aegisops',
    email: 'aegisops@local',
    avatar: '/avatars/shadcn.jpg',
  },
  teams: [
    {
      name: 'AegisOps',
      logo: Command,
      plan: 'AIOps Platform',
    },
    {
      name: 'Acme Inc',
      logo: GalleryVerticalEnd,
      plan: 'Enterprise',
    },
    {
      name: 'Acme Corp.',
      logo: AudioWaveform,
      plan: 'Startup',
    },
  ],
  navGroups: buildNavGroups(),
}

export function refreshSidebarData(): SidebarData {
  return { ...sidebarData, navGroups: buildNavGroups() }
}
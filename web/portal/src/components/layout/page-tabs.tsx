import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from '@tanstack/react-router'
import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { useLayout } from '@/context/layout-provider'
import { Button } from '@/components/ui/button'
import { navigation, type NavigationItem } from './navigation'
import {
  closeTab,
  PAGE_TABS_STORAGE_KEY,
  readStoredTabs,
  visitTab,
  type PageTab,
} from './page-tabs-model'

function flattenNavigation(items: readonly NavigationItem[]): NavigationItem[] {
  return items.flatMap((item) => [
    item,
    ...flattenNavigation(item.children ?? []),
  ])
}

function fallbackTitle(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean)
  const segment = segments[segments.length - 1]
  if (!segment) return 'Dashboard'

  return decodeURIComponent(segment)
    .replace(/-/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())
}

function resolveTabTitle(
  href: string,
  translate: (key: string) => string
): string {
  const pathname = href.split(/[?#]/, 1)[0] || '/'
  const links = flattenNavigation(navigation).filter(
    (item): item is NavigationItem & { to: string } => Boolean(item.to)
  )
  const exact = links.find((item) => item.to === pathname)
  if (exact) return translate(exact.titleKey)

  const parent = links
    .filter((item) => pathname.startsWith(`${item.to}/`))
    .sort((left, right) => right.to.length - left.to.length)[0]
  const detail = fallbackTitle(pathname)
  return parent ? `${translate(parent.titleKey)} · ${detail}` : detail
}

function storeTabs(tabs: PageTab[]) {
  try {
    sessionStorage.setItem(PAGE_TABS_STORAGE_KEY, JSON.stringify(tabs))
  } catch {
    // Browsing remains usable when storage is unavailable or full.
  }
}

export function PageTabs() {
  const { pageTabs } = useLayout()
  const { t } = useTranslation()
  const href = useLocation({ select: (location) => location.href })
  const navigate = useNavigate()
  const title = useMemo(() => resolveTabTitle(href, t), [href, t])
  const syncKey = `${pageTabs}:${href}:${title}`
  const [tabState, setTabState] = useState<{
    syncKey: string
    tabs: PageTab[]
  }>(() => ({
    syncKey,
    tabs: pageTabs
      ? visitTab(readStoredTabs(sessionStorage), { href, title })
      : readStoredTabs(sessionStorage),
  }))

  if (tabState.syncKey !== syncKey) {
    setTabState({
      syncKey,
      tabs: pageTabs ? visitTab(tabState.tabs, { href, title }) : tabState.tabs,
    })
  }

  const tabs = tabState.tabs

  useEffect(() => {
    if (!pageTabs) return
    storeTabs(tabs)
  }, [pageTabs, tabs])

  if (!pageTabs) return null

  const handleClose = (tabHref: string) => {
    setTabState((currentState) => {
      const result = closeTab(currentState.tabs, tabHref, href)
      if (result.nextHref) void navigate({ to: result.nextHref as never })
      return { ...currentState, tabs: result.tabs }
    })
  }

  return (
    <nav
      role='tablist'
      aria-label='Open pages'
      className='flex h-10 shrink-0 items-end gap-1 overflow-x-auto border-b bg-muted/40 px-2 pt-1'
    >
      {tabs.map((tab) => {
        const active = tab.href === href
        return (
          <div
            key={tab.href}
            className={cn(
              'flex h-9 max-w-56 min-w-32 shrink-0 items-center rounded-t-md border border-b-0',
              active
                ? 'bg-background text-foreground'
                : 'bg-muted text-muted-foreground hover:bg-background/70'
            )}
          >
            <Button
              type='button'
              role='tab'
              aria-selected={active}
              variant='ghost'
              className='h-full min-w-0 flex-1 justify-start rounded-none px-3'
              onClick={() => void navigate({ to: tab.href as never })}
            >
              <span className='truncate'>{tab.title}</span>
            </Button>
            <Button
              type='button'
              size='icon'
              variant='ghost'
              className='me-1 size-6 shrink-0 rounded-sm'
              aria-label={`Close ${tab.title}`}
              disabled={tabs.length === 1}
              onClick={(event) => {
                event.stopPropagation()
                handleClose(tab.href)
              }}
            >
              <X data-icon='inline-start' aria-hidden='true' />
            </Button>
          </div>
        )
      })}
    </nav>
  )
}

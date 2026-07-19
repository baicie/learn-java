export const PAGE_TABS_STORAGE_KEY = 'aegisops.page-tabs'

export type PageTab = {
  href: string
  title: string
}

function pageTabKey(href: string): string {
  return href.split(/[?#]/, 1)[0]
}

type StorageReader = Pick<Storage, 'getItem'>

export function visitTab(tabs: PageTab[], visited: PageTab): PageTab[] {
  const visitedKey = pageTabKey(visited.href)
  const existingIndex = tabs.findIndex(
    (tab) => pageTabKey(tab.href) === visitedKey
  )
  if (existingIndex === -1) return [...tabs, visited]

  return tabs
    .filter(
      (tab, index) =>
        index === existingIndex || pageTabKey(tab.href) !== visitedKey
    )
    .map((tab, index) => (index === existingIndex ? visited : tab))
}

export function closeTab(
  tabs: PageTab[],
  href: string,
  activeHref: string
): { tabs: PageTab[]; nextHref?: string } {
  if (tabs.length <= 1) return { tabs }

  const closingKey = pageTabKey(href)
  const closingIndex = tabs.findIndex(
    (tab) => pageTabKey(tab.href) === closingKey
  )
  if (closingIndex === -1) return { tabs }

  const remaining = tabs.filter((tab) => pageTabKey(tab.href) !== closingKey)
  if (closingKey !== pageTabKey(activeHref)) return { tabs: remaining }

  const nextTab = tabs[closingIndex + 1] ?? tabs[closingIndex - 1]
  return { tabs: remaining, nextHref: nextTab?.href }
}

export function readStoredTabs(storage: StorageReader): PageTab[] {
  try {
    const stored = storage.getItem(PAGE_TABS_STORAGE_KEY)
    if (!stored) return []

    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed)) return []

    const validTabs = parsed.filter(
      (tab): tab is PageTab =>
        typeof tab === 'object' &&
        tab !== null &&
        typeof (tab as PageTab).href === 'string' &&
        typeof (tab as PageTab).title === 'string'
    )
    return validTabs.reduce<PageTab[]>(visitTab, [])
  } catch {
    return []
  }
}

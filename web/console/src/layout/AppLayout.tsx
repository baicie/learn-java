import { useQuery } from '@tanstack/react-query'
import { LanguagesIcon, MenuIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { listPlatformMenus } from '../api/client'
import { Button } from '../components/ui/button'
import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
  navigationMenuTriggerStyle,
} from '../components/ui/navigation-menu'
import { setLanguage, SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'
import { buildMenuTree, resolveMenuIcon, type MenuNode } from '../lib/menu'

const LANGUAGE_LABEL: Record<SupportedLanguage, string> = {
  'zh-CN': '中文',
  'en-US': 'English',
}

function LeafLink({ node }: { node: MenuNode }) {
  const Icon = resolveMenuIcon(node.icon)
  return (
    <NavigationMenuItem>
      <NavigationMenuLink
        href={node.path}
        className={`${navigationMenuTriggerStyle()} w-full justify-start`}
      >
        {Icon ? <Icon className="size-4" /> : <span className="size-4" aria-hidden />}
        <span className="truncate">{node.title}</span>
      </NavigationMenuLink>
    </NavigationMenuItem>
  )
}

function Branch({ node }: { node: MenuNode }) {
  const Icon = resolveMenuIcon(node.icon)
  return (
    <NavigationMenuItem value={node.id}>
      <NavigationMenuTrigger className={`${navigationMenuTriggerStyle()} w-full justify-start`}>
        {Icon ? <Icon className="size-4" /> : <span className="size-4" aria-hidden />}
        <span className="truncate">{node.title}</span>
      </NavigationMenuTrigger>
      <NavigationMenuContent className="min-w-48 p-1">
        <ul className="flex w-full flex-col gap-1">
          {node.children.map((child) => (
            <li key={child.id}>
              <NavigationMenuLink
                href={child.path}
                className="flex items-center gap-2 rounded-md px-3 py-2 text-sm"
              >
                <ChildIcon name={child.icon} />
                <span className="truncate">{child.title}</span>
              </NavigationMenuLink>
            </li>
          ))}
        </ul>
      </NavigationMenuContent>
    </NavigationMenuItem>
  )
}

function ChildIcon({ name }: { name: string | null | undefined }) {
  const Icon = resolveMenuIcon(name)
  if (!Icon) return <span className="size-4" aria-hidden />
  return <Icon className="size-4" />
}

function MenuRow({ node }: { node: MenuNode }) {
  if (node.children.length === 0) {
    return <LeafLink node={node} />
  }
  return <Branch node={node} />
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  const menus = useQuery({ queryKey: ['platform', 'menus'], queryFn: listPlatformMenus })
  const { t, i18n } = useTranslation()

  const currentLang = (SUPPORTED_LANGUAGES as readonly string[]).includes(i18n.language)
    ? (i18n.language as SupportedLanguage)
    : SUPPORTED_LANGUAGES[0]
  const nextLang: SupportedLanguage = currentLang === 'zh-CN' ? 'en-US' : 'zh-CN'

  const tree = buildMenuTree({ nodes: menus.data ?? [] })

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 flex w-60 flex-col border-r bg-card">
        <div className="flex items-center justify-between p-4">
          <div className="text-lg font-semibold">{t('nav.title')}</div>
          <Button variant="ghost" size="icon-sm" aria-label="菜单">
            <MenuIcon />
          </Button>
        </div>
        <div className="flex-1 overflow-y-auto p-2">
          <NavigationMenu align="start" className="flex-col items-stretch justify-start gap-1">
            <NavigationMenuList className="flex-col items-stretch justify-start gap-1">
              {tree.map((node) => (
                <MenuRow key={node.id} node={node} />
              ))}
            </NavigationMenuList>
          </NavigationMenu>
        </div>
        <div className="border-t p-3">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start"
            onClick={() => setLanguage(nextLang)}
            aria-label={t('nav.switchTo', { lang: LANGUAGE_LABEL[nextLang] })}
          >
            <LanguagesIcon data-icon="inline-start" />
            <span className="flex flex-col items-start leading-tight">
              <span className="text-xs text-muted-foreground">{t('nav.language')}</span>
              <span className="text-sm font-medium">{LANGUAGE_LABEL[currentLang]}</span>
            </span>
          </Button>
        </div>
      </aside>
      <main className="ml-60 min-h-screen p-6">{children}</main>
    </div>
  )
}

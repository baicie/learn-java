import { useQuery } from '@tanstack/react-query'
import { LanguagesIcon, MenuIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'

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

function isActive(currentPath: string, targetPath: string | null | undefined): boolean {
  if (!targetPath) return false
  if (targetPath === currentPath) return true
  // /app/x 子路由视为父路由 active
  return currentPath.startsWith(targetPath + '/')
}

/**
 * 用 React Router 的 Link 替代 shadcn NavigationMenuLink 内部的 <a href>，
 * 避免每次菜单点击触发整页刷新（重载整页 = "整个页面闪一下"）。
 *
 * shadcn NavigationMenuPrimitive.Link 的 API 是 accept any anchor props，
 * 实际渲染为 <a>。我们用 render prop 替换为客户端路由 Link。
 */
function RouterNavLink({
  to,
  className,
  children,
  active,
}: {
  to: string
  className?: string
  children: React.ReactNode
  active?: boolean
}) {
  return (
    <NavigationMenuLink render={<Link to={to} />} className={className} active={active}>
      {children}
    </NavigationMenuLink>
  )
}

function LeafLink({ node }: { node: MenuNode }) {
  const Icon = resolveMenuIcon(node.icon)
  const location = useLocation()
  const active = isActive(location.pathname, node.path)
  return (
    <NavigationMenuItem>
      <RouterNavLink
        to={node.path ?? '/'}
        className={`${navigationMenuTriggerStyle()} w-full justify-start`}
        active={active}
      >
        {Icon ? <Icon className="size-4" /> : <span className="size-4" aria-hidden />}
        <span className="truncate">{node.title}</span>
      </RouterNavLink>
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
            <ChildLink key={child.id} node={child} />
          ))}
        </ul>
      </NavigationMenuContent>
    </NavigationMenuItem>
  )
}

function ChildLink({ node }: { node: MenuNode }) {
  const Icon = resolveMenuIcon(node.icon)
  const location = useLocation()
  const active = isActive(location.pathname, node.path)
  return (
    <li>
      <RouterNavLink
        to={node.path ?? '/'}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm"
        active={active}
      >
        {Icon ? <Icon className="size-4" /> : <span className="size-4" aria-hidden />}
        <span className="truncate">{node.title}</span>
      </RouterNavLink>
    </li>
  )
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

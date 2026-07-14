import { type LinkProps } from '@tanstack/react-router'

type Team = {
  name: string
  logo: React.ElementType
  plan: string
}

/**
 * 标题既可以是静态字符串（硬编码英文等历史内容），
 * 也可以是延迟到渲染期再求值的函数（用于 i18n 等需要在组件生命周期内解析的来源）。
 * 消费方通过 `resolveTitle` 统一收敛。
 */
type NavTitle = string | (() => string)

function resolveTitle(title: NavTitle): string {
  return typeof title === 'function' ? title() : title
}

type BaseNavItem = {
  title: NavTitle
  badge?: string
  icon?: React.ElementType
}

type NavLink = BaseNavItem & {
  url: LinkProps['to'] | (string & {})
  items?: never
}

type NavCollapsible = BaseNavItem & {
  items: (BaseNavItem & { url: LinkProps['to'] | (string & {}) })[]
  url?: never
}

type NavItem = NavCollapsible | NavLink

type NavGroup = {
  title: string
  items: NavItem[]
}

type SidebarData = {
  teams: Team[]
  navGroups: NavGroup[]
}

export {
  resolveTitle,
  type SidebarData,
  type NavGroup,
  type NavItem,
  type NavCollapsible,
  type NavLink,
}

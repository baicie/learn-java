'use client'

export {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuIndicator,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuPositioner,
  NavigationMenuTrigger,
  navigationMenuTriggerStyle,
} from './navigation-menu'

import { NavigationMenu as MenuPrimitive } from '@base-ui/react/navigation-menu'

export const Menu = MenuPrimitive
export type MenuProps = MenuPrimitive.Root.Props
export type MenuItemProps = MenuPrimitive.Item.Props
export type MenuLinkProps = MenuPrimitive.Link.Props
export type MenuTriggerProps = MenuPrimitive.Trigger.Props
export type MenuContentProps = MenuPrimitive.Content.Props
export type MenuListProps = MenuPrimitive.List.Props

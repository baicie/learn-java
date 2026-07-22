import { Outlet } from '@tanstack/react-router'
import { Monitor, Bell, Palette, Wrench, UserCog } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Separator } from '@/components/ui/separator'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { SidebarNav } from './components/sidebar-nav'

const buildVersion = import.meta.env.VITE_BUILD_VERSION || 'development'

export function Settings() {
  const { t } = useTranslation()
  const sidebarNavItems = [
    {
      title: t('settings.nav.profile'),
      href: '/settings',
      icon: <UserCog size={18} />,
    },
    {
      title: t('settings.nav.account'),
      href: '/settings/account',
      icon: <Wrench size={18} />,
    },
    {
      title: t('settings.nav.appearance'),
      href: '/settings/appearance',
      icon: <Palette size={18} />,
    },
    {
      title: t('settings.nav.notifications'),
      href: '/settings/notifications',
      icon: <Bell size={18} />,
    },
    {
      title: t('settings.nav.display'),
      href: '/settings/display',
      icon: <Monitor size={18} />,
    },
  ]
  return (
    <>
      {/* ===== Top Heading ===== */}
      <Header>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ConfigDrawer />
        <ProfileDropdown />
      </Header>

      <Main fixed>
        <div className='flex flex-col gap-0.5'>
          <h1 className='text-2xl font-bold tracking-tight md:text-3xl'>
            {t('settings.title')}
          </h1>
          <p className='text-muted-foreground'>{t('settings.description')}</p>
        </div>
        <Separator className='my-4 lg:my-6' />
        <div className='flex flex-1 flex-col gap-2 overflow-hidden lg:flex-row lg:gap-12'>
          <aside className='top-0 lg:sticky lg:w-1/5'>
            <SidebarNav items={sidebarNavItems} />
            <Separator className='my-4' />
            <div className='flex flex-col gap-1 px-2'>
              <span className='text-xs font-medium text-muted-foreground'>
                {t('settings.buildVersion')}
              </span>
              <code className='text-xs break-all' title={buildVersion}>
                {buildVersion}
              </code>
            </div>
          </aside>
          <div className='flex w-full overflow-y-hidden p-1'>
            <Outlet />
          </div>
        </div>
      </Main>
    </>
  )
}

import { t, type MessageKey } from '@/i18n'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

type WorkRecordsLayoutProps = {
  titleKey: MessageKey
  descriptionKey?: MessageKey
  actions?: React.ReactNode
  children: React.ReactNode
}

export function WorkRecordsLayout({
  titleKey,
  descriptionKey,
  actions,
  children,
}: WorkRecordsLayoutProps) {
  return (
    <>
      <Header fixed>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ConfigDrawer />
        <ProfileDropdown />
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <div className='flex flex-wrap items-end justify-between gap-2'>
          <div>
            <h2 className='text-2xl font-bold tracking-tight'>{t(titleKey)}</h2>
            {descriptionKey ? (
              <p className='text-muted-foreground'>{t(descriptionKey)}</p>
            ) : null}
          </div>
          {actions}
        </div>
        {children}
      </Main>
    </>
  )
}

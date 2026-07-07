import { t } from '@/i18n'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

const permissions = [
  'platform:dict:read',
  'platform:dict:write',
  'work-record:read:self',
  'work-record:read:all',
  'work-record:write',
  'work-record:template:read',
  'work-record:template:write',
  'work-record:export',
]

export function Roles() {
  return (
    <>
      <Header fixed>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ConfigDrawer />
        <ProfileDropdown />
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <div>
          <h2 className='text-2xl font-bold tracking-tight'>
            {t('platform.roles.title')}
          </h2>
          <p className='text-muted-foreground'>
            {t('platform.roles.description')}
          </p>
        </div>
        <div className='grid max-w-3xl gap-4 rounded-md border p-4'>
          <div>
            <h3 className='font-medium'>记录管理员</h3>
            <p className='text-sm text-muted-foreground'>
              工作记录第一版权限集合，后续接入真实角色 API。
            </p>
          </div>
          <Separator />
          <div className='flex flex-wrap gap-2'>
            {permissions.map((permission) => (
              <Badge key={permission} variant='outline'>
                {permission}
              </Badge>
            ))}
          </div>
        </div>
      </Main>
    </>
  )
}

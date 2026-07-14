import { useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useAuthorization } from '@/auth/use-authorization'
import { Bell, ChevronsUpDown, LogOut, UserCog, Wrench } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { loadProfileAvatar, PROFILE_AVATAR_CHANGED } from '@/lib/profile-avatar'
import useDialogState from '@/hooks/use-dialog-state'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { SignOutDialog } from '@/components/sign-out-dialog'

export function NavUser() {
  const { isMobile } = useSidebar()
  const [open, setOpen] = useDialogState()
  const [avatar, setAvatar] = useState(loadProfileAvatar)
  const principal = useAuthorization()
  const { t } = useTranslation()
  const displayName =
    principal?.displayName || principal?.username || 'AegisOps'

  useEffect(() => {
    const refresh = () => setAvatar(loadProfileAvatar())
    window.addEventListener(PROFILE_AVATAR_CHANGED, refresh)
    return () => window.removeEventListener(PROFILE_AVATAR_CHANGED, refresh)
  }, [])

  return (
    <>
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <SidebarMenuButton
                size='lg'
                className='data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground'
              >
                <Avatar className='size-8 rounded-lg'>
                  <AvatarImage src={avatar} alt={displayName} />
                  <AvatarFallback className='rounded-lg'>
                    {displayName.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className='grid flex-1 text-start text-sm leading-tight'>
                  <span className='truncate font-semibold'>{displayName}</span>
                  <span className='truncate text-xs'>
                    {principal?.username}
                  </span>
                </div>
                <ChevronsUpDown className='ms-auto size-4' />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              className='w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg'
              side={isMobile ? 'bottom' : 'right'}
              align='end'
              sideOffset={4}
            >
              <DropdownMenuLabel className='p-0 font-normal'>
                <div className='flex items-center gap-2 px-1 py-1.5 text-start text-sm'>
                  <Avatar className='size-8 rounded-lg'>
                    <AvatarImage src={avatar} alt={displayName} />
                    <AvatarFallback className='rounded-lg'>
                      {displayName.slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <div className='grid flex-1 text-start text-sm leading-tight'>
                    <span className='truncate font-semibold'>
                      {displayName}
                    </span>
                    <span className='truncate text-xs'>
                      {principal?.username}
                    </span>
                  </div>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem asChild>
                  <Link to='/settings'>
                    <UserCog />
                    {t('settings.nav.profile')}
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to='/settings/account'>
                    <Wrench />
                    {t('settings.nav.account')}
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to='/settings/notifications'>
                    <Bell />
                    {t('settings.nav.notifications')}
                  </Link>
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                variant='destructive'
                onClick={() => setOpen(true)}
              >
                <LogOut />
                {t('auth.signOut.action')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>

      <SignOutDialog open={!!open} onOpenChange={setOpen} />
    </>
  )
}

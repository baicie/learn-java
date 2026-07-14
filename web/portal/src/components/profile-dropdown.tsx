import { useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useAuthorization } from '@/auth/use-authorization'
import { useTranslation } from 'react-i18next'
import { loadProfileAvatar, PROFILE_AVATAR_CHANGED } from '@/lib/profile-avatar'
import useDialogState from '@/hooks/use-dialog-state'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { SignOutDialog } from '@/components/sign-out-dialog'

export function ProfileDropdown() {
  const [open, setOpen] = useDialogState()
  const [avatar, setAvatar] = useState(loadProfileAvatar)
  const principal = useAuthorization()
  const { t } = useTranslation()

  useEffect(() => {
    const refresh = () => setAvatar(loadProfileAvatar())
    window.addEventListener(PROFILE_AVATAR_CHANGED, refresh)
    return () => window.removeEventListener(PROFILE_AVATAR_CHANGED, refresh)
  }, [])

  const displayName =
    principal?.displayName || principal?.username || 'AegisOps'

  return (
    <>
      <DropdownMenu modal={false}>
        <DropdownMenuTrigger asChild>
          <Button
            variant='ghost'
            size='icon'
            className='rounded-full'
            aria-label={t('settings.profile.title')}
          >
            <Avatar className='size-8'>
              <AvatarImage src={avatar} alt={displayName} />
              <AvatarFallback>
                {displayName.slice(0, 2).toUpperCase()}
              </AvatarFallback>
            </Avatar>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent className='w-56' align='end' forceMount>
          <DropdownMenuLabel className='font-normal'>
            <div className='flex flex-col gap-1.5'>
              <p className='text-sm leading-none font-medium'>{displayName}</p>
              <p className='text-xs leading-none text-muted-foreground'>
                {principal?.username}
              </p>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem asChild>
              <Link to='/settings'>{t('settings.nav.profile')}</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to='/settings/account'>{t('settings.nav.account')}</Link>
            </DropdownMenuItem>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem variant='destructive' onClick={() => setOpen(true)}>
            {t('auth.signOut.action')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <SignOutDialog open={!!open} onOpenChange={setOpen} />
    </>
  )
}

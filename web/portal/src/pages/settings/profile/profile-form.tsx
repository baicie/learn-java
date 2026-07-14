import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  getAccountProfile,
  updateAccountProfile,
  type AccountProfile,
} from '@/api/account'
import { useAuthStore } from '@/stores/auth-store'
import { loadProfileAvatar, saveProfileAvatar } from '@/lib/profile-avatar'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { notify } from '@/components/feedback/app-toaster'
import { ErrorState, PageLoadingState } from '@/components/feedback/async-state'

export function ProfileForm() {
  const profile = useQuery({
    queryKey: ['account-profile'],
    queryFn: getAccountProfile,
  })

  if (profile.isLoading) return <PageLoadingState />
  if (profile.error) {
    return (
      <ErrorState
        error={profile.error}
        onRetry={() => void profile.refetch()}
      />
    )
  }
  if (!profile.data) return null

  return (
    <ProfileEditor
      key={`${profile.data.displayName}:${profile.data.email ?? ''}`}
      profile={profile.data}
    />
  )
}

function ProfileEditor({ profile }: { profile: AccountProfile }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [avatar, setAvatar] = useState(loadProfileAvatar)
  const [displayName, setDisplayName] = useState(profile.displayName)
  const [email, setEmail] = useState(profile.email ?? '')
  const save = useMutation({
    mutationFn: () => updateAccountProfile({ displayName, email }),
    onSuccess: async (result) => {
      const auth = useAuthStore.getState().auth
      if (auth.principal) {
        auth.setPrincipal({
          ...auth.principal,
          displayName: result.displayName,
        })
      }
      notify.success(t('settings.profile.saved'))
      await queryClient.invalidateQueries({ queryKey: ['account-profile'] })
    },
    onError: (error) => notify.error(error, t('settings.profile.saveFailed')),
  })

  const uploadAvatar = (file?: File) => {
    if (!file) return
    if (file.size > 384_000) {
      notify.error(new Error(t('settings.profile.avatarTooLarge')))
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      try {
        const value = String(reader.result ?? '')
        saveProfileAvatar(value)
        setAvatar(value)
        notify.success(t('settings.profile.avatarSaved'))
      } catch (error) {
        notify.error(error, t('settings.profile.avatarFailed'))
      }
    }
    reader.readAsDataURL(file)
  }

  return (
    <form
      className='flex flex-col gap-6'
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate()
      }}
    >
      <div className='flex items-center gap-4'>
        <Avatar className='size-20'>
          <AvatarImage src={avatar} alt={displayName} />
          <AvatarFallback>
            {displayName.slice(0, 2).toUpperCase()}
          </AvatarFallback>
        </Avatar>
        <div className='flex flex-col gap-2'>
          <Label htmlFor='avatar'>{t('settings.profile.avatar')}</Label>
          <Input
            id='avatar'
            type='file'
            accept='image/png,image/jpeg,image/webp'
            onChange={(event) => uploadAvatar(event.target.files?.[0])}
          />
          <p className='text-xs text-muted-foreground'>
            {t('settings.profile.avatarHint')}
          </p>
        </div>
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='username'>{t('settings.profile.username')}</Label>
        <Input id='username' value={profile.username} disabled />
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='displayName'>{t('settings.profile.displayName')}</Label>
        <Input
          id='displayName'
          value={displayName}
          maxLength={128}
          required
          onChange={(event) => setDisplayName(event.target.value)}
        />
      </div>
      <div className='grid gap-2'>
        <Label htmlFor='email'>{t('settings.profile.email')}</Label>
        <Input
          id='email'
          type='email'
          value={email}
          maxLength={128}
          onChange={(event) => setEmail(event.target.value)}
        />
      </div>
      <Button
        className='w-fit'
        disabled={save.isPending || !displayName.trim()}
      >
        <Camera data-icon='inline-start' />
        {save.isPending ? t('common.saving') : t('common.save')}
      </Button>
    </form>
  )
}

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { setLanguage, type SupportedLanguage } from '@/i18n'
import { Languages, LockKeyhole } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { changeAccountPassword } from '@/api/account'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { notify } from '@/components/feedback/app-toaster'
import { PasswordInput } from '@/components/password-input'

export function AccountForm() {
  const { t, i18n } = useTranslation()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const changePassword = useMutation({
    mutationFn: () => changeAccountPassword({ currentPassword, newPassword }),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      notify.success(t('settings.password.saved'))
    },
    onError: (error) => notify.error(error, t('settings.password.failed')),
  })

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2 text-base'>
            <Languages />
            {t('settings.language.title')}
          </CardTitle>
          <CardDescription>
            {t('settings.language.description')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Select
            value={i18n.language}
            onValueChange={(value) => setLanguage(value as SupportedLanguage)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value='zh-CN'>简体中文</SelectItem>
                <SelectItem value='en-US'>English</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2 text-base'>
            <LockKeyhole />
            {t('settings.password.title')}
          </CardTitle>
          <CardDescription>
            {t('settings.password.description')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className='flex flex-col gap-4'
            onSubmit={(event) => {
              event.preventDefault()
              if (newPassword !== confirmPassword) {
                notify.error(new Error(t('settings.password.mismatch')))
                return
              }
              changePassword.mutate()
            }}
          >
            <PasswordField
              id='currentPassword'
              label={t('settings.password.current')}
              value={currentPassword}
              onChange={setCurrentPassword}
            />
            <PasswordField
              id='newPassword'
              label={t('settings.password.new')}
              value={newPassword}
              onChange={setNewPassword}
            />
            <PasswordField
              id='confirmPassword'
              label={t('settings.password.confirm')}
              value={confirmPassword}
              onChange={setConfirmPassword}
            />
            <Button
              className='w-fit'
              disabled={
                changePassword.isPending ||
                currentPassword.length === 0 ||
                newPassword.length < 8
              }
            >
              {changePassword.isPending
                ? t('common.saving')
                : t('settings.password.action')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

function PasswordField({
  id,
  label,
  value,
  onChange,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className='grid gap-2'>
      <Label htmlFor={id}>{label}</Label>
      <PasswordInput
        id={id}
        autoComplete='new-password'
        value={value}
        minLength={8}
        maxLength={128}
        required
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  )
}

import { ShieldCheckIcon, TriangleAlertIcon } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { login } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'

export function LoginPage() {
  const { t } = useTranslation()
  const auth = useAuth()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const result = await login(username, password)
      auth.setSession(result.token, result.user)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('auth.login.errorNetwork'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <Card className="w-full max-w-sm shadow-lg">
        <CardHeader>
          <div className="mb-2 flex items-center gap-2 text-primary">
            <ShieldCheckIcon className="size-5" data-icon="inline-start" />
            <span className="text-sm font-semibold tracking-wide uppercase">{t('app.name')}</span>
          </div>
          <CardTitle className="text-2xl">{t('auth.login.title')}</CardTitle>
          <CardDescription>{t('auth.login.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-col gap-5">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="login-username">{t('auth.login.username')}</FieldLabel>
                <Input
                  id="login-username"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="login-password">{t('auth.login.password')}</FieldLabel>
                <Input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
                <FieldDescription>{t('auth.login.demoHint')}</FieldDescription>
              </Field>
            </FieldGroup>

            {error && (
              <Alert variant="destructive">
                <TriangleAlertIcon data-icon="inline-start" />
                <AlertTitle>{t('auth.login.errorInvalid')}</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <Button type="submit" size="lg" disabled={loading} className="w-full">
              {loading ? (
                <>
                  <Spinner data-icon="inline-start" />
                  {t('auth.login.submitting')}
                </>
              ) : (
                t('auth.login.submit')
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}

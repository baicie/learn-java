import { useState, type FormEvent } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Loader2 } from 'lucide-react'
import { useAuthStore } from '@/stores/auth-store'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { fetchCurrentAuthorization } from './authorization-api'
import { login } from './login-api'

export function SignInPage() {
  const navigate = useNavigate()
  const search = useSearch({ from: '/(auth)/sign-in' })
  const setAccessToken = useAuthStore((state) => state.auth.setAccessToken)
  const setPrincipal = useAuthStore((state) => state.auth.setPrincipal)
  const setLoaded = useAuthStore((state) => state.auth.setAuthorizationLoaded)

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await login(username, password)
      setAccessToken(result.token)
      try {
        const principal = await fetchCurrentAuthorization()
        setPrincipal(principal)
        setLoaded(true)
      } catch {
        setPrincipal(null)
        setLoaded(true)
      }

      const redirect =
        typeof search.redirect === 'string' && search.redirect.length > 0
          ? search.redirect
          : '/'
      await navigate({ to: redirect, replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className={cn(
        'mx-auto w-full max-w-sm space-y-6 rounded-lg border bg-card p-6 shadow-sm'
      )}
    >
      <div className='space-y-1 text-center'>
        <h1 className='text-2xl font-semibold tracking-tight'>登录</h1>
        <p className='text-sm text-muted-foreground'>
          输入账号与密码进入 AegisOps。
        </p>
      </div>

      <form className='space-y-4' onSubmit={handleSubmit}>
        <div className='space-y-2'>
          <Label htmlFor='username'>账号</Label>
          <Input
            id='username'
            autoComplete='username'
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className='space-y-2'>
          <Label htmlFor='password'>密码</Label>
          <Input
            id='password'
            type='password'
            autoComplete='current-password'
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        {error ? (
          <p role='alert' className='text-sm text-destructive'>
            {error}
          </p>
        ) : null}

        <Button type='submit' className='w-full' disabled={submitting}>
          {submitting ? <Loader2 className='size-4 animate-spin' /> : '登录'}
        </Button>
      </form>
    </div>
  )
}

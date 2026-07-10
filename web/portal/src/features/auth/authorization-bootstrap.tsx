import { useEffect, type ReactNode } from 'react'
import { useAuthStore } from '@/stores/auth-store'
import { fetchCurrentAuthorization } from './authorization-api'

type Props = {
  children: ReactNode
}

export function AuthorizationBootstrap({ children }: Props) {
  const accessToken = useAuthStore((state) => state.auth.accessToken)

  const loaded = useAuthStore((state) => state.auth.authorizationLoaded)

  const setPrincipal = useAuthStore((state) => state.auth.setPrincipal)

  const setLoaded = useAuthStore((state) => state.auth.setAuthorizationLoaded)

  const reset = useAuthStore((state) => state.auth.reset)

  useEffect(() => {
    let cancelled = false

    if (!accessToken) {
      setPrincipal(null)
      setLoaded(true)
      return
    }

    setLoaded(false)

    void fetchCurrentAuthorization()
      .then((principal) => {
        if (cancelled) return

        setPrincipal(principal)
        setLoaded(true)
      })
      .catch(() => {
        if (cancelled) return
        reset()
      })

    return () => {
      cancelled = true
    }
  }, [accessToken, reset, setLoaded, setPrincipal])

  if (!loaded) {
    return (
      <div className='p-6 text-sm text-muted-foreground'>正在加载权限...</div>
    )
  }

  return children
}

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { me } from './api/client'
import { useAuth } from './auth/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'

export function App() {
  const auth = useAuth()
  const query = useQuery({
    queryKey: ['me', auth.token],
    queryFn: me,
    enabled: Boolean(auth.token),
    retry: false
  })

  useEffect(() => {
    if (query.data) {
      auth.setCurrentUser(query.data)
    }
  }, [auth, query.data])

  useEffect(() => {
    if (query.error) {
      auth.logout()
    }
  }, [auth, query.error])

  if (!auth.token) return <LoginPage />
  if (query.isLoading) return <div className="p-6">Loading session...</div>
  if (query.error) return <LoginPage />
  return <DashboardPage />
}

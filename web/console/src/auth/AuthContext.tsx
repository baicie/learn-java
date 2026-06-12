import { createContext, ReactNode, useContext, useMemo, useState } from 'react'
import { clearToken, getToken, Me, setToken } from '../api/client'

type AuthContextValue = {
  token: string | null
  user: Me | null
  setSession: (token: string, user: Me) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, updateToken] = useState<string | null>(() => getToken())
  const [user, setUser] = useState<Me | null>(null)

  const value = useMemo<AuthContextValue>(() => ({
    token,
    user,
    setSession(nextToken, nextUser) {
      setToken(nextToken)
      updateToken(nextToken)
      setUser(nextUser)
    },
    logout() {
      clearToken()
      updateToken(null)
      setUser(null)
    }
  }), [token, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { create } from 'zustand'
import { getCookie, removeCookie, setCookie } from '@/lib/cookies'

const ACCESS_TOKEN = 'thisisjustarandomstring'

type AuthState = {
  auth: {
    accessToken: string
    principal: AuthorizationPrincipal | null
    authorizationLoaded: boolean

    setAccessToken: (accessToken: string) => void

    setPrincipal: (principal: AuthorizationPrincipal | null) => void

    setAuthorizationLoaded: (loaded: boolean) => void

    resetAccessToken: () => void
    reset: () => void
  }
}

export const useAuthStore = create<AuthState>()((set) => {
  const cookieState = getCookie(ACCESS_TOKEN)

  const initToken = cookieState ? JSON.parse(cookieState) : ''

  return {
    auth: {
      accessToken: initToken,
      principal: null,
      authorizationLoaded: false,

      setAccessToken: (accessToken) =>
        set((state) => {
          setCookie(ACCESS_TOKEN, JSON.stringify(accessToken))

          return {
            ...state,
            auth: {
              ...state.auth,
              accessToken,
              authorizationLoaded: false,
            },
          }
        }),

      setPrincipal: (principal) =>
        set((state) => ({
          ...state,
          auth: {
            ...state.auth,
            principal,
          },
        })),

      setAuthorizationLoaded: (loaded) =>
        set((state) => ({
          ...state,
          auth: {
            ...state.auth,
            authorizationLoaded: loaded,
          },
        })),

      resetAccessToken: () =>
        set((state) => {
          removeCookie(ACCESS_TOKEN)

          return {
            ...state,
            auth: {
              ...state.auth,
              accessToken: '',
              principal: null,
              authorizationLoaded: true,
            },
          }
        }),

      reset: () =>
        set((state) => {
          removeCookie(ACCESS_TOKEN)

          return {
            ...state,
            auth: {
              ...state.auth,
              accessToken: '',
              principal: null,
              authorizationLoaded: true,
            },
          }
        }),
    },
  }
})

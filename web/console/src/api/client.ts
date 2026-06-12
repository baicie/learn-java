export type ApiResponse<T> = {
  success: boolean
  data: T
  errorCode?: string
  message?: string
  timestamp: string
}

export type LoginResponse = {
  token: string
  user: Me
}

export type Me = {
  id: string
  tenantId: string
  username: string
  displayName: string
  roles: string[]
}

const TOKEN_KEY = 'aegisops_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const resp = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {})
    }
  })

  const payload = (await resp.json()) as ApiResponse<T>
  if (!resp.ok || !payload.success) {
    throw new Error(payload.message || payload.errorCode || 'Request failed')
  }
  return payload.data
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

export function me() {
  return apiRequest<Me>('/api/auth/me')
}

export function overview() {
  return apiRequest<Record<string, number | string>>('/api/system/overview')
}

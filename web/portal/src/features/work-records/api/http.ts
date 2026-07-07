import axios from 'axios'
import { useAuthStore } from '@/stores/auth-store'

export const workRecordHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

workRecordHttp.interceptors.request.use((config) => {
  const token = useAuthStore.getState().auth.accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

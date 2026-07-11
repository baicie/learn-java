import type { ReactNode } from 'react'
import { AppToaster } from '@/components/feedback/app-toaster'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ConfirmProvider>
      {children}
      <AppToaster />
    </ConfirmProvider>
  )
}

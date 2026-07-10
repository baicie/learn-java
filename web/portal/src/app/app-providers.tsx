import type { ReactNode } from 'react'
import { I18nProvider } from '@/i18n/provider'
import { AppToaster } from '@/components/feedback/app-toaster'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <ConfirmProvider>
        {children}
        <AppToaster />
      </ConfirmProvider>
    </I18nProvider>
  )
}

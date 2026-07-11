import { useEffect, useRef } from 'react'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useConfirm } from '@/components/feedback/confirm-provider'

type NavigationResolver = {
  proceed: () => void
  reset: () => void
}

export async function resolveBlockedNavigation(
  resolver: NavigationResolver,
  confirm: () => Promise<boolean>
) {
  const leave = await confirm()

  if (leave) {
    resolver.proceed()
  } else {
    resolver.reset()
  }
}

export function useUnsavedChangesGuard(enabled: boolean) {
  const confirm = useConfirm()
  const { t } = useTranslation()
  const resolvingRef = useRef(false)

  const blocker = useBlocker({
    shouldBlockFn: () => enabled,
    enableBeforeUnload: enabled,
    withResolver: true,
  })

  useEffect(() => {
    if (blocker.status !== 'blocked' || resolvingRef.current) {
      return
    }

    resolvingRef.current = true

    void resolveBlockedNavigation(
      {
        proceed: blocker.proceed,
        reset: blocker.reset,
      },
      () =>
        confirm({
          title: t('navigation.unsaved.title'),
          description: t('navigation.unsaved.description'),
          confirmText: t('navigation.unsaved.leave'),
          cancelText: t('navigation.unsaved.stay'),
          variant: 'warning',
        })
    ).finally(() => {
      resolvingRef.current = false
    })
  }, [blocker.proceed, blocker.reset, blocker.status, confirm, t])
}

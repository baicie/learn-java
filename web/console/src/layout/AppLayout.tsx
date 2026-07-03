import { useQuery } from '@tanstack/react-query'
import { LanguagesIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router-dom'

import { listPlatformMenus } from '../api/client'
import { Button } from '../components/ui/button'
import { setLanguage, SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'

const LANGUAGE_LABEL: Record<SupportedLanguage, string> = {
  'zh-CN': '中文',
  'en-US': 'English',
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  const menus = useQuery({ queryKey: ['platform', 'menus'], queryFn: listPlatformMenus })
  const { t, i18n } = useTranslation()

  const currentLang = (SUPPORTED_LANGUAGES as readonly string[]).includes(i18n.language)
    ? (i18n.language as SupportedLanguage)
    : SUPPORTED_LANGUAGES[0]
  const nextLang: SupportedLanguage = currentLang === 'zh-CN' ? 'en-US' : 'zh-CN'

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 flex w-60 flex-col border-r bg-card">
        <div className="p-4">
          <div className="text-lg font-semibold">{t('nav.title')}</div>
        </div>
        <nav className="flex-1 space-y-1 px-2">
          {(menus.data ?? []).map((item) => (
            <NavLink
              key={item.id}
              to={item.path}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm ${isActive ? 'bg-muted font-medium' : 'hover:bg-muted'}`
              }
            >
              {item.title}
            </NavLink>
          ))}
        </nav>
        <div className="border-t p-3">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start"
            onClick={() => setLanguage(nextLang)}
            aria-label={t('nav.switchTo', { lang: LANGUAGE_LABEL[nextLang] })}
          >
            <LanguagesIcon data-icon="inline-start" />
            <span className="flex flex-col items-start leading-tight">
              <span className="text-xs text-muted-foreground">{t('nav.language')}</span>
              <span className="text-sm font-medium">{LANGUAGE_LABEL[currentLang]}</span>
            </span>
          </Button>
        </div>
      </aside>
      <main className="ml-60 min-h-screen p-6">{children}</main>
    </div>
  )
}

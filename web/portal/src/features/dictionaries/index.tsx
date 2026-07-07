import { useState } from 'react'
import { t } from '@/i18n'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { DictionaryItemTable } from './components/dictionary-item-table'
import { DictionaryTypeList } from './components/dictionary-type-list'
import { useDictItems, useDictTypes } from './hooks/use-dictionaries'

export function Dictionaries() {
  const dictTypes = useDictTypes()
  const [selectedCode, setSelectedCode] = useState<string>()
  const effectiveCode = selectedCode ?? dictTypes.data?.[0]?.dictCode
  const dictItems = useDictItems(effectiveCode, true)

  return (
    <>
      <Header fixed>
        <Search className='me-auto' />
        <ThemeSwitch />
        <ConfigDrawer />
        <ProfileDropdown />
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <div>
          <h2 className='text-2xl font-bold tracking-tight'>
            {t('platform.dictionaries.title')}
          </h2>
          <p className='text-muted-foreground'>
            {t('platform.dictionaries.description')}
          </p>
        </div>

        {dictTypes.isLoading ? (
          <Skeleton className='h-64 w-full' />
        ) : (
          <div className='grid gap-4 @4xl/content:grid-cols-[320px_minmax(0,1fr)]'>
            <DictionaryTypeList
              items={dictTypes.data ?? []}
              selectedCode={effectiveCode}
              onSelect={setSelectedCode}
            />
            {dictItems.isLoading ? (
              <Skeleton className='h-64 w-full' />
            ) : (
              <DictionaryItemTable items={dictItems.data ?? []} />
            )}
          </div>
        )}
      </Main>
    </>
  )
}

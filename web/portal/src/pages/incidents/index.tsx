import { useEffect, useMemo, useState } from 'react'
import { SearchIcon, Siren } from 'lucide-react'
import { useIncidents } from '@/hooks/operations/use-operations'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  EmptyState,
  ErrorState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { IncidentListTable } from '@/components/incidents/list/incident-list-table'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { filterIncidents, paginateIncidents } from './list-filter'

export type IncidentSearch = {
  page: number
  pageSize: number
  keyword: string
  status: string
  severity: string
  source: string
}

export function IncidentsPage({
  search,
  onSearch,
}: {
  search: IncidentSearch
  onSearch: (next: Partial<IncidentSearch>) => void
}) {
  const incidents = useIncidents()
  const filtered = useMemo(
    () => filterIncidents(incidents.data ?? [], search),
    [incidents.data, search]
  )
  const paged = useMemo(
    () => paginateIncidents(filtered, search),
    [filtered, search]
  )
  const sources = useMemo(
    () => [
      ...new Set((incidents.data ?? []).map((incident) => incident.source)),
    ],
    [incidents.data]
  )

  useEffect(() => {
    if (
      !incidents.isLoading &&
      !incidents.isError &&
      search.page > paged.pageCount
    ) {
      onSearch({ page: paged.pageCount })
    }
  }, [
    incidents.isError,
    incidents.isLoading,
    onSearch,
    paged.pageCount,
    search.page,
  ])

  return (
    <>
      <Header fixed>
        <div className='ml-auto flex items-center gap-2'>
          <Search />
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>
      <Main className='grid gap-6'>
        <div>
          <h1 className='text-2xl font-bold'>Incident 中心</h1>
          <p className='text-sm text-muted-foreground'>
            聚合关联告警，跟踪故障处置进度和影响范围。
          </p>
        </div>

        <div className='grid gap-2 sm:grid-cols-2 xl:grid-cols-4'>
          <Summary label='最近 Incident' value={incidents.data?.length ?? 0} />
          <Summary
            label='待处理'
            value={
              incidents.data?.filter((incident) =>
                ['open', 'investigating', 'mitigating'].includes(
                  incident.status
                )
              ).length ?? 0
            }
          />
          <Summary
            label='已解决'
            value={
              incidents.data?.filter(
                (incident) => incident.status === 'resolved'
              ).length ?? 0
            }
          />
          <Summary label='当前筛选结果' value={paged.total} />
        </div>

        <div className='grid gap-2 lg:grid-cols-[minmax(0,1fr)_repeat(3,minmax(10rem,12rem))]'>
          <IncidentKeywordFilter
            key={search.keyword}
            initialValue={search.keyword}
            onSubmit={(keyword) => onSearch({ keyword, page: 1 })}
          />
          <Select
            value={search.status || 'all'}
            onValueChange={(value) =>
              onSearch({ status: value === 'all' ? '' : value, page: 1 })
            }
          >
            <SelectTrigger className='w-full'>
              <SelectValue placeholder='状态' />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='all'>全部状态</SelectItem>
              <SelectItem value='open'>待处理</SelectItem>
              <SelectItem value='investigating'>调查中</SelectItem>
              <SelectItem value='mitigating'>处置中</SelectItem>
              <SelectItem value='resolved'>已解决</SelectItem>
              <SelectItem value='closed'>已关闭</SelectItem>
              <SelectItem value='ignored'>已忽略</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={search.severity || 'all'}
            onValueChange={(value) =>
              onSearch({ severity: value === 'all' ? '' : value, page: 1 })
            }
          >
            <SelectTrigger className='w-full'>
              <SelectValue placeholder='严重度' />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='all'>全部严重度</SelectItem>
              <SelectItem value='disaster'>灾难</SelectItem>
              <SelectItem value='critical'>严重</SelectItem>
              <SelectItem value='high'>高</SelectItem>
              <SelectItem value='medium'>中</SelectItem>
              <SelectItem value='warning'>警告</SelectItem>
              <SelectItem value='low'>低</SelectItem>
              <SelectItem value='info'>信息</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={search.source || 'all'}
            onValueChange={(value) =>
              onSearch({ source: value === 'all' ? '' : value, page: 1 })
            }
          >
            <SelectTrigger className='w-full'>
              <SelectValue placeholder='来源' />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='all'>全部来源</SelectItem>
              {sources.map((source) => (
                <SelectItem key={source} value={source}>
                  {source}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {incidents.isLoading ? (
          <TableLoadingState rows={8} columns={7} />
        ) : incidents.isError ? (
          <ErrorState
            error={incidents.error}
            onRetry={() => void incidents.refetch()}
          />
        ) : !filtered.length ? (
          <EmptyState
            title={
              search.keyword ||
              search.status ||
              search.severity ||
              search.source
                ? '没有匹配的 Incident'
                : '还没有 Incident'
            }
            description={
              search.keyword ||
              search.status ||
              search.severity ||
              search.source
                ? '调整筛选条件后重试。'
                : '接入监控数据源并同步告警后，平台会自动聚合 Incident。'
            }
            icon={<Siren className='size-6' />}
          />
        ) : (
          <IncidentListTable
            items={paged.items}
            page={Math.min(search.page, paged.pageCount)}
            pageCount={paged.pageCount}
            onPageChange={(page) => onSearch({ page })}
          />
        )}
      </Main>
    </>
  )
}

function IncidentKeywordFilter({
  initialValue,
  onSubmit,
}: {
  initialValue: string
  onSubmit: (keyword: string) => void
}) {
  const [keyword, setKeyword] = useState(initialValue)

  return (
    <div className='relative'>
      <SearchIcon className='absolute top-2.5 left-3 size-4 text-muted-foreground' />
      <Input
        aria-label='搜索 Incident'
        className='pl-9'
        value={keyword}
        placeholder='搜索标题、摘要或 ID'
        onChange={(event) => setKeyword(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') onSubmit(keyword)
        }}
      />
    </div>
  )
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div className='rounded-lg border bg-card p-4'>
      <p className='text-xs text-muted-foreground'>{label}</p>
      <p className='mt-1 text-2xl font-semibold'>{value}</p>
    </div>
  )
}

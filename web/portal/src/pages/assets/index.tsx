import { useState } from 'react'
import { Boxes, FileUp, Plus, SearchIcon } from 'lucide-react'
import type { AssetSearch } from '@/api/assets/assets-api'
import { useAssets, useAssetSummary } from '@/hooks/assets/use-assets'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { AssetFormDialog } from '@/components/assets/asset-form-dialog'
import { AssetImportDialog } from '@/components/assets/asset-import-dialog'
import { AssetsTable } from '@/components/assets/assets-table'
import {
  EmptyState,
  ErrorState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { PermissionGate } from '@/components/permission-gate'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

export function AssetsPage({
  search,
  onSearch,
}: {
  search: AssetSearch
  onSearch: (next: Partial<AssetSearch>) => void
}) {
  const [createOpen, setCreateOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [keyword, setKeyword] = useState(search.keyword ?? '')
  const assets = useAssets(search)
  const summary = useAssetSummary()
  const items = assets.data?.items ?? []

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
        <div className='flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between'>
          <div>
            <h1 className='text-2xl font-bold tracking-tight'>资源中心</h1>
            <p className='text-sm text-muted-foreground'>
              统一查看来自手工、CSV 和监控系统的主机与服务。
            </p>
          </div>
          <div className='flex gap-2'>
            <PermissionGate any={['asset:import']}>
              <Button variant='outline' onClick={() => setImportOpen(true)}>
                <FileUp />
                导入 CSV
              </Button>
            </PermissionGate>
            <PermissionGate any={['asset:write']}>
              <Button onClick={() => setCreateOpen(true)}>
                <Plus />
                新增资源
              </Button>
            </PermissionGate>
          </div>
        </div>
        <div className='grid gap-3 sm:grid-cols-2 xl:grid-cols-4'>
          <Summary label='资源总数' value={summary.data?.totalAssets ?? 0} />
          <Summary label='活跃资源' value={summary.data?.activeAssets ?? 0} />
          <Summary
            label='多来源资源'
            value={summary.data?.multiSourceAssets ?? 0}
          />
          <Summary
            label='待处理冲突'
            value={summary.data?.pendingConflicts ?? 0}
          />
        </div>
        <div className='flex flex-col gap-2 sm:flex-row'>
          <div className='relative flex-1'>
            <SearchIcon className='absolute top-2.5 left-3 size-4 text-muted-foreground' />
            <Input
              className='pl-9'
              value={keyword}
              placeholder='搜索名称、显示名称或 IP'
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') onSearch({ keyword, page: 1 })
              }}
            />
          </div>
          <Select
            value={search.assetType || 'all'}
            onValueChange={(value) =>
              onSearch({
                assetType: value === 'all' ? undefined : value,
                page: 1,
              })
            }
          >
            <SelectTrigger className='w-full sm:w-40'>
              <SelectValue placeholder='资源类型' />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='all'>全部类型</SelectItem>
              <SelectItem value='host'>主机</SelectItem>
              <SelectItem value='service'>服务</SelectItem>
              <SelectItem value='application'>应用</SelectItem>
              <SelectItem value='database'>数据库</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={search.sourceType || 'all'}
            onValueChange={(value) =>
              onSearch({
                sourceType: value === 'all' ? undefined : value,
                page: 1,
              })
            }
          >
            <SelectTrigger className='w-full sm:w-40'>
              <SelectValue placeholder='来源' />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value='all'>全部来源</SelectItem>
              <SelectItem value='manual'>手工</SelectItem>
              <SelectItem value='csv'>CSV</SelectItem>
              <SelectItem value='zabbix'>Zabbix</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {assets.isLoading ? (
          <TableLoadingState rows={8} columns={9} />
        ) : assets.isError ? (
          <ErrorState
            error={assets.error}
            onRetry={() => void assets.refetch()}
          />
        ) : !items.length ? (
          <EmptyState
            title={
              search.keyword || search.assetType || search.sourceType
                ? '没有匹配的资源'
                : '还没有资源'
            }
            description={
              search.keyword || search.assetType || search.sourceType
                ? '调整筛选条件后重试。'
                : '可以手工新增、导入 CSV，或从 Zabbix 同步资源。'
            }
            icon={<Boxes className='size-6' />}
            action={
              <div className='flex gap-2'>
                <PermissionGate any={['asset:write']}>
                  <Button onClick={() => setCreateOpen(true)}>新增资源</Button>
                </PermissionGate>
                <PermissionGate any={['asset:import']}>
                  <Button variant='outline' onClick={() => setImportOpen(true)}>
                    导入 CSV
                  </Button>
                </PermissionGate>
              </div>
            }
          />
        ) : (
          <AssetsTable
            items={items}
            page={search.page}
            pageSize={search.pageSize}
            total={assets.data?.total ?? 0}
            onPageChange={(page) => onSearch({ page })}
          />
        )}
      </Main>
      <AssetFormDialog open={createOpen} onOpenChange={setCreateOpen} />
      <AssetImportDialog open={importOpen} onOpenChange={setImportOpen} />
    </>
  )
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className='p-4'>
        <p className='text-xs text-muted-foreground'>{label}</p>
        <p className='mt-1 text-2xl font-semibold'>{value}</p>
      </CardContent>
    </Card>
  )
}

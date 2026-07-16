import { useState } from 'react'
import { DatabaseZap, Plus, RefreshCw, TestTube2 } from 'lucide-react'
import {
  useDatasources,
  useSyncDatasource,
  useSyncRuns,
  useTestDatasource,
} from '@/hooks/datasources/use-datasources'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { DatasourceFormDialog } from '@/components/datasources/datasource-form-dialog'
import { notify } from '@/components/feedback/app-toaster'
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

export function DatasourcesPage() {
  const [open, setOpen] = useState(false)
  const sources = useDatasources()
  const test = useTestDatasource()
  const sync = useSyncDatasource()

  const testConnection = (id: string) =>
    test.mutate(id, {
      onSuccess: (result) =>
        result.success
          ? notify.success(
              `连接成功${result.version ? ` · ${result.version}` : ''}`
            )
          : notify.error(result.message),
      onError: (error) => notify.error(error, '连接测试失败'),
    })
  const startSync = (id: string) =>
    sync.mutate(id, {
      onSuccess: (result) => notify.success(`同步已加入队列：${result.runId}`),
      onError: (error) => notify.error(error, '启动同步失败'),
    })

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
            <h1 className='text-2xl font-bold tracking-tight'>数据源</h1>
            <p className='text-sm text-muted-foreground'>
              管理外部监控系统的连接、健康状态和同步任务。
            </p>
          </div>
          <PermissionGate any={['datasource:write']}>
            <Button onClick={() => setOpen(true)}>
              <Plus />
              添加数据源
            </Button>
          </PermissionGate>
        </div>
        {sources.isLoading ? (
          <TableLoadingState rows={5} columns={7} />
        ) : sources.isError ? (
          <ErrorState
            error={sources.error}
            onRetry={() => void sources.refetch()}
          />
        ) : !sources.data?.length ? (
          <EmptyState
            title='还没有数据源'
            description='添加 Zabbix 数据源后即可同步主机和告警。'
            icon={<DatabaseZap className='size-6' />}
            action={
              <PermissionGate any={['datasource:write']}>
                <Button onClick={() => setOpen(true)}>
                  添加 Zabbix 数据源
                </Button>
              </PermissionGate>
            }
          />
        ) : (
          <Card>
            <CardContent className='overflow-x-auto p-0'>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>名称</TableHead>
                    <TableHead>类型</TableHead>
                    <TableHead>Endpoint</TableHead>
                    <TableHead>连接状态</TableHead>
                    <TableHead>最近同步</TableHead>
                    <TableHead>最近结果</TableHead>
                    <TableHead className='text-right'>操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sources.data.map((source) => (
                    <TableRow key={source.id}>
                      <TableCell className='font-medium'>
                        {source.name}
                      </TableCell>
                      <TableCell>Zabbix</TableCell>
                      <TableCell className='max-w-72 truncate font-mono text-xs'>
                        {source.endpoint ?? '—'}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            source.status === 'error'
                              ? 'destructive'
                              : 'outline'
                          }
                        >
                          {source.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {source.lastSyncAt
                          ? new Date(source.lastSyncAt).toLocaleString()
                          : '尚未同步'}
                      </TableCell>
                      <TableCell>
                        <RecentSyncResult datasourceId={source.id} />
                      </TableCell>
                      <TableCell>
                        <PermissionGate any={['datasource:write']}>
                          <div className='flex justify-end gap-1'>
                            <Button
                              size='sm'
                              variant='ghost'
                              disabled={test.isPending}
                              onClick={() => testConnection(source.id)}
                            >
                              <TestTube2 />
                              测试
                            </Button>
                            <Button
                              size='sm'
                              variant='ghost'
                              disabled={sync.isPending}
                              onClick={() => startSync(source.id)}
                            >
                              <RefreshCw />
                              同步
                            </Button>
                          </div>
                        </PermissionGate>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </Main>
      <DatasourceFormDialog open={open} onOpenChange={setOpen} />
    </>
  )
}

function RecentSyncResult({ datasourceId }: { datasourceId: string }) {
  const runs = useSyncRuns(datasourceId)
  const latest = runs.data?.[0]
  if (runs.isLoading)
    return <span className='text-muted-foreground'>读取中…</span>
  if (!latest) return <span className='text-muted-foreground'>—</span>
  return (
    <Badge
      variant={latest.status === 'failed' ? 'destructive' : 'secondary'}
      title={latest.message ?? undefined}
    >
      {latest.status === 'pending'
        ? '排队中'
        : latest.status === 'running'
          ? '同步中'
          : latest.status === 'success'
            ? '成功'
            : '失败'}
    </Badge>
  )
}

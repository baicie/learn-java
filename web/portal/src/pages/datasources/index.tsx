import { useState } from 'react'
import { DatabaseZap, Plus } from 'lucide-react'
import type { Datasource } from '@/lib/datasources/datasource'
import {
  useDatasources,
  useSyncDatasource,
  useTestDatasource,
} from '@/hooks/datasources/use-datasources'
import { Button } from '@/components/ui/button'
import { DatasourceFormDialog } from '@/components/datasources/datasource-form-dialog'
import { DatasourcesTable } from '@/components/datasources/datasources-table'
import { ZabbixWebhookDialog } from '@/components/datasources/zabbix-webhook-dialog'
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
  const [editing, setEditing] = useState<Datasource | null>(null)
  const [webhookDatasource, setWebhookDatasource] = useState<Datasource | null>(
    null
  )
  const sources = useDatasources()
  const test = useTestDatasource()
  const sync = useSyncDatasource()

  const testConnection = (id: string) =>
    test.mutate(id, {
      onSuccess: (result) =>
        result.ok
          ? notify.success(
              `连接成功${result.version ? ` · ${result.version}` : ''}`
            )
          : notify.error(new Error(result.message)),
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
            <Button
              onClick={() => {
                setEditing(null)
                setOpen(true)
              }}
            >
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
            description='添加 Zabbix、Kubernetes 或被动接入数据源后即可开始采集。'
            icon={<DatabaseZap className='size-6' />}
            action={
              <PermissionGate any={['datasource:write']}>
                <Button
                  onClick={() => {
                    setEditing(null)
                    setOpen(true)
                  }}
                >
                  添加数据源
                </Button>
              </PermissionGate>
            }
          />
        ) : (
          <DatasourcesTable
            items={sources.data}
            onTest={testConnection}
            onSync={startSync}
            onEdit={(datasource) => {
              setEditing(datasource)
              setOpen(true)
            }}
            onWebhook={setWebhookDatasource}
            pending={test.isPending || sync.isPending}
          />
        )}
      </Main>
      <DatasourceFormDialog
        open={open}
        datasource={editing}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen)
          if (!nextOpen) setEditing(null)
        }}
      />
      {webhookDatasource ? (
        <ZabbixWebhookDialog
          datasource={webhookDatasource}
          open
          onOpenChange={(next) => {
            if (!next) setWebhookDatasource(null)
          }}
        />
      ) : null}
    </>
  )
}

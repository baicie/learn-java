import { PlugIcon, PlusIcon, RefreshCwIcon } from 'lucide-react'
import { type FormEvent } from 'react'

import type { CreateZabbixDataSourcePayload, DataSourceRecord } from '@/api/client'

import { StatusBadge } from '@/components/console/status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'

export type DatasourceForm = {
  name: string
  endpoint: string
  username: string
  password: string
  apiToken: string
}

export function DatasourceFormCard({
  form,
  onFormChange,
  onSubmit,
  isSubmitting,
}: {
  form: DatasourceForm
  onFormChange: (next: DatasourceForm) => void
  onSubmit: (payload: CreateZabbixDataSourcePayload) => void
  isSubmitting: boolean
}) {
  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      type: 'zabbix',
      name: form.name,
      zabbix: {
        endpoint: form.endpoint,
        username: form.username || undefined,
        password: form.password || undefined,
        apiToken: form.apiToken || undefined,
        connectTimeoutSeconds: 5,
        readTimeoutSeconds: 20,
      },
    })
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <PlugIcon className="size-4 text-muted-foreground" />
          <CardTitle>Add Zabbix Datasource</CardTitle>
        </div>
        <CardDescription>填写 Zabbix API 地址和认证信息,创建后可执行 Test / Sync。</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="ds-name">Name</FieldLabel>
              <Input
                id="ds-name"
                value={form.name}
                onChange={(event) => onFormChange({ ...form, name: event.target.value })}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="ds-endpoint">API Endpoint</FieldLabel>
              <Input
                id="ds-endpoint"
                value={form.endpoint}
                onChange={(event) => onFormChange({ ...form, endpoint: event.target.value })}
                required
              />
            </Field>
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="ds-username">Username</FieldLabel>
                <Input
                  id="ds-username"
                  value={form.username}
                  onChange={(event) => onFormChange({ ...form, username: event.target.value })}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="ds-password">Password</FieldLabel>
                <Input
                  id="ds-password"
                  type="password"
                  value={form.password}
                  onChange={(event) => onFormChange({ ...form, password: event.target.value })}
                />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="ds-token">API Token</FieldLabel>
              <Input
                id="ds-token"
                type="password"
                value={form.apiToken}
                onChange={(event) => onFormChange({ ...form, apiToken: event.target.value })}
              />
            </Field>
          </FieldGroup>

          <Separator />

          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <RefreshCwIcon className="animate-spin" data-icon="inline-start" />
                Creating
              </>
            ) : (
              <>
                <PlusIcon data-icon="inline-start" />
                Create Datasource
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

export function DatasourceListCard({
  items,
  isLoading,
  onTest,
  onSync,
  isTestPending,
  isSyncPending,
}: {
  items: DataSourceRecord[] | undefined
  isLoading: boolean
  onTest: (id: string) => void
  onSync: (id: string) => void
  isTestPending: boolean
  isSyncPending: boolean
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Datasources</CardTitle>
        <CardDescription>点击 Test 验证连接,点击 Sync 触发 Zabbix 主机与告警同步。</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        )}

        {items && items.length > 0 && (
          <div className="flex flex-col gap-3">
            {items.map((ds) => (
              <div
                key={ds.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{ds.name}</span>
                    <StatusBadge status={ds.status} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {ds.type} · last sync {ds.lastSyncAt || '-'}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={isTestPending}
                    onClick={() => onTest(ds.id)}
                  >
                    Test
                  </Button>
                  <Button size="sm" disabled={isSyncPending} onClick={() => onSync(ds.id)}>
                    Sync
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        {items && items.length === 0 && (
          <Empty className="border">
            <EmptyMedia variant="icon">
              <PlugIcon />
            </EmptyMedia>
            <EmptyTitle>还没有数据源</EmptyTitle>
            <EmptyDescription>填写左侧表单创建一个 Zabbix 数据源。</EmptyDescription>
          </Empty>
        )}
      </CardContent>
    </Card>
  )
}

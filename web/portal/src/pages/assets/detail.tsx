import { useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import {
  ArrowLeft,
  Archive,
  Fingerprint,
  GitBranch,
  Pencil,
  Tags,
} from 'lucide-react'
import { assetTypeLabels, sourceLabels } from '@/lib/assets/asset'
import { formatDateTime } from '@/lib/date-format'
import { useArchiveAsset, useAssetDetail } from '@/hooks/assets/use-assets'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { AssetFormDialog } from '@/components/assets/asset-form-dialog'
import {
  EmptyState,
  ErrorState,
  PageLoadingState,
} from '@/components/feedback/async-state'
import { useConfirm } from '@/components/feedback/confirm-provider'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { PermissionGate } from '@/components/permission-gate'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

export function AssetDetailPage({ assetId }: { assetId: string }) {
  const [editOpen, setEditOpen] = useState(false)
  const detail = useAssetDetail(assetId)
  const archive = useArchiveAsset()
  const confirm = useConfirm()
  const navigate = useNavigate()
  if (detail.isLoading) return <PageLoadingState />
  if (detail.isError || !detail.data)
    return (
      <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
    )
  const { asset, sources, identities, relations } = detail.data
  const archiveAsset = async () => {
    const accepted = await confirm({
      title: `归档 ${asset.displayName || asset.name}`,
      description:
        '归档后默认列表和详情将不再展示该资源，来源映射和审计记录仍会保留。',
      confirmText: '确认归档',
      variant: 'destructive',
    })
    if (!accepted) return
    archive.mutate(
      { id: asset.id, version: asset.version },
      {
        onSuccess: () =>
          void navigate({
            to: '/assets',
            search: {
              page: 1,
              pageSize: 20,
              keyword: '',
              assetType: '',
              sourceType: '',
              status: '',
            },
          }),
      }
    )
  }

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
            <Button variant='ghost' size='sm' asChild className='mb-2 -ml-3'>
              <Link
                to='/assets'
                search={{
                  page: 1,
                  pageSize: 20,
                  keyword: '',
                  assetType: '',
                  sourceType: '',
                  status: '',
                }}
              >
                <ArrowLeft />
                返回资源列表
              </Link>
            </Button>
            <div className='flex flex-wrap items-center gap-2'>
              <h1 className='text-2xl font-bold tracking-tight'>
                {asset.displayName || asset.name}
              </h1>
              <Badge variant='outline'>
                {assetTypeLabels[asset.assetType] ?? asset.assetType}
              </Badge>
              <Badge>{asset.status}</Badge>
            </div>
            <p className='text-sm text-muted-foreground'>
              {asset.name} · {asset.id}
            </p>
          </div>
          <PermissionGate any={['asset:write']}>
            <div className='flex gap-2'>
              <Button variant='outline' onClick={() => setEditOpen(true)}>
                <Pencil />
                编辑资源
              </Button>
              <Button
                variant='destructive'
                disabled={archive.isPending}
                onClick={() => void archiveAsset()}
              >
                <Archive />
                归档资源
              </Button>
            </div>
          </PermissionGate>
        </div>
        <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]'>
          <div className='grid content-start gap-4'>
            <Card>
              <CardHeader>
                <CardTitle>基本信息</CardTitle>
                <CardDescription>
                  规范资源字段由手工或最近来源更新。
                </CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4 sm:grid-cols-2'>
                <Value
                  label='资源类型'
                  value={assetTypeLabels[asset.assetType] ?? asset.assetType}
                />
                <Value label='环境' value={asset.environment} />
                <Value label='IP / 地址' value={asset.ip} />
                <Value label='站点' value={asset.site} />
                <Value label='负责人团队' value={asset.ownerTeam} />
                <Value label='关键等级' value={asset.criticality} />
                <Value
                  label='最近发现'
                  value={
                    asset.lastSeenAt ? formatDateTime(asset.lastSeenAt) : null
                  }
                />
                <Value
                  label='最近更新'
                  value={formatDateTime(asset.updatedAt)}
                />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle className='flex items-center gap-2'>
                  <Fingerprint className='size-4' />
                  身份标识
                </CardTitle>
              </CardHeader>
              <CardContent>
                {identities.length ? (
                  <div className='grid gap-2'>
                    {identities.map((identity) => (
                      <div
                        key={identity.id}
                        className='flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 text-sm'
                      >
                        <div>
                          <span className='font-medium'>
                            {identity.identityType}
                          </span>
                          <p className='font-mono text-xs text-muted-foreground'>
                            {identity.identityValue}
                          </p>
                        </div>
                        <div className='flex gap-1'>
                          <Badge variant='outline'>
                            {identity.strength === 'strong'
                              ? '强身份'
                              : '弱身份'}
                          </Badge>
                          {identity.verified ? <Badge>已验证</Badge> : null}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState compact title='暂无身份标识' />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle className='flex items-center gap-2'>
                  <Tags className='size-4' />
                  标签
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='flex flex-wrap gap-2'>
                  {Object.entries(asset.tags).map(([key, value]) => (
                    <Badge key={key} variant='secondary'>
                      {key}={String(value)}
                    </Badge>
                  ))}
                  {!Object.keys(asset.tags).length ? (
                    <span className='text-sm text-muted-foreground'>
                      暂无标签
                    </span>
                  ) : null}
                </div>
              </CardContent>
            </Card>
          </div>
          <div className='grid content-start gap-4'>
            <Card>
              <CardHeader>
                <CardTitle>数据来源</CardTitle>
                <CardDescription>只读展示外部系统的真实标识。</CardDescription>
              </CardHeader>
              <CardContent>
                {sources.length ? (
                  <Accordion type='multiple'>
                    {sources.map((source) => (
                      <AccordionItem key={source.id} value={source.id}>
                        <AccordionTrigger>
                          <span className='flex flex-wrap items-center gap-2'>
                            <Badge variant='outline'>
                              {sourceLabels[source.sourceType] ??
                                source.sourceType}
                            </Badge>
                            <span className='text-xs text-muted-foreground'>
                              {source.syncStatus}
                            </span>
                          </span>
                        </AccordionTrigger>
                        <AccordionContent className='grid gap-2'>
                          <p className='font-mono text-xs break-all'>
                            {source.externalId}
                          </p>
                          <p className='text-xs text-muted-foreground'>
                            最近发现 {formatDateTime(source.lastSeenAt)}
                          </p>
                        </AccordionContent>
                      </AccordionItem>
                    ))}
                  </Accordion>
                ) : (
                  <EmptyState compact title='暂无来源' />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle className='flex items-center gap-2'>
                  <GitBranch className='size-4' />
                  资源关系
                </CardTitle>
                <CardDescription>
                  第一阶段使用列表，不展示伪拓扑。
                </CardDescription>
              </CardHeader>
              <CardContent>
                {relations.length ? (
                  <div className='grid gap-2'>
                    {relations.map((relation) => (
                      <div
                        key={relation.id}
                        className='rounded-md border p-3 text-sm'
                      >
                        <p className='font-medium'>{relation.relationType}</p>
                        <p className='mt-1 font-mono text-xs text-muted-foreground'>
                          {relation.fromAssetId === asset.id ? '→' : '←'}{' '}
                          {relation.fromAssetId === asset.id
                            ? relation.toAssetId
                            : relation.fromAssetId}
                        </p>
                        <p className='mt-1 text-xs text-muted-foreground'>
                          置信度 {relation.confidence}
                        </p>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState compact title='暂无资源关系' />
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </Main>
      <AssetFormDialog
        key={`${asset.id}:${asset.version}`}
        open={editOpen}
        onOpenChange={setEditOpen}
        asset={asset}
      />
    </>
  )
}

function Value({ label, value }: { label: string; value?: string | null }) {
  return (
    <div>
      <p className='text-xs text-muted-foreground'>{label}</p>
      <p className='mt-1 text-sm font-medium'>{value || '—'}</p>
    </div>
  )
}

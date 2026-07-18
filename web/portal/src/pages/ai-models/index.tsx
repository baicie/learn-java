import { useState } from 'react'
import { BrainCircuit, Plus } from 'lucide-react'
import type { AiModel } from '@/lib/ai-models/ai-model'
import {
  useAiModels,
  useDeleteAiModel,
  useSetDefaultAiModel,
  useTestAiModel,
  useUpdateAiModel,
} from '@/hooks/ai-models/use-ai-models'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { AiModelFormDialog } from '@/components/ai-models/ai-model-form-dialog'
import { AiModelList } from '@/components/ai-models/ai-model-list'
import { notify } from '@/components/feedback/app-toaster'
import {
  EmptyState,
  ErrorState,
  TableLoadingState,
} from '@/components/feedback/async-state'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'

export function AiModelsPage() {
  const models = useAiModels()
  const update = useUpdateAiModel()
  const test = useTestAiModel()
  const setDefault = useSetDefaultAiModel()
  const remove = useDeleteAiModel()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<AiModel | null>(null)
  const [deleting, setDeleting] = useState<AiModel | null>(null)
  const pending =
    update.isPending ||
    test.isPending ||
    setDefault.isPending ||
    remove.isPending

  const openCreate = () => {
    setEditing(null)
    setDialogOpen(true)
  }
  const openEdit = (model: AiModel) => {
    setEditing(model)
    setDialogOpen(true)
  }
  const testConnection = (model: AiModel) =>
    test.mutate(model.id, {
      onSuccess: (result) =>
        result.success
          ? notify.success(result.message)
          : notify.error(result.message),
      onError: (error) => notify.error(error, '连接测试失败'),
    })
  const makeDefault = (model: AiModel) =>
    setDefault.mutate(model.id, {
      onSuccess: () => notify.success(`${model.name} 已设为默认模型`),
      onError: (error) => notify.error(error, '设置默认模型失败'),
    })
  const toggle = (model: AiModel) =>
    update.mutate(
      {
        id: model.id,
        input: {
          provider: 'deepseek',
          name: model.name,
          modelName: model.modelName,
          baseUrl: model.baseUrl,
          apiKey: '',
          enabled: !model.enabled,
        },
      },
      {
        onSuccess: () =>
          notify.success(model.enabled ? '模型已停用' : '模型已启用'),
        onError: (error) => notify.error(error, '更新模型状态失败'),
      }
    )
  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.id, {
      onSuccess: () => {
        notify.success('模型配置已删除')
        setDeleting(null)
      },
      onError: (error) => notify.error(error, '删除模型配置失败'),
    })
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
            <h1 className='text-2xl font-bold tracking-tight'>AI 模型</h1>
            <p className='text-sm text-muted-foreground'>
              管理用于 AI 诊断的 DeepSeek 模型连接配置。
            </p>
          </div>
          <Button onClick={openCreate}>
            <Plus />
            添加模型
          </Button>
        </div>
        {models.isLoading ? (
          <TableLoadingState rows={4} columns={5} />
        ) : models.isError ? (
          <ErrorState
            error={models.error}
            onRetry={() => void models.refetch()}
          />
        ) : !models.data?.length ? (
          <EmptyState
            title='还没有模型配置'
            description='添加 DeepSeek 模型连接后，可测试连通性并选择默认模型。'
            icon={<BrainCircuit className='size-6' />}
            action={<Button onClick={openCreate}>添加模型</Button>}
          />
        ) : (
          <AiModelList
            models={models.data}
            pending={pending}
            onEdit={openEdit}
            onTest={testConnection}
            onSetDefault={makeDefault}
            onToggle={toggle}
            onDelete={setDeleting}
          />
        )}
      </Main>
      <AiModelFormDialog
        open={dialogOpen}
        model={editing}
        onOpenChange={setDialogOpen}
      />
      <AlertDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除模型配置</AlertDialogTitle>
            <AlertDialogDescription>
              将删除“{deleting?.name}”及其加密密钥，此操作无法撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDelete}
              disabled={remove.isPending}
            >
              {remove.isPending ? '删除中…' : '确认删除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

import {
  CheckCircle2,
  CircleOff,
  MoreHorizontal,
  Pencil,
  PlugZap,
  Power,
  PowerOff,
  Star,
  Trash2,
  XCircle,
} from 'lucide-react'
import type { AiModel } from '@/lib/ai-models/ai-model'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

export function AiModelList({
  models,
  pending,
  onEdit,
  onTest,
  onSetDefault,
  onToggle,
  onDelete,
}: {
  models: AiModel[]
  pending: boolean
  onEdit: (model: AiModel) => void
  onTest: (model: AiModel) => void
  onSetDefault: (model: AiModel) => void
  onToggle: (model: AiModel) => void
  onDelete: (model: AiModel) => void
}) {
  return (
    <div className='overflow-hidden rounded-lg border'>
      <div className='hidden grid-cols-[minmax(14rem,1.4fr)_minmax(12rem,1fr)_9rem_9rem_6rem] items-center gap-4 border-b bg-muted/40 px-4 py-2 text-xs font-medium text-muted-foreground md:grid'>
        <span>模型</span>
        <span>API 地址</span>
        <span>状态</span>
        <span>连接测试</span>
        <span className='text-right'>操作</span>
      </div>
      <div className='divide-y'>
        {models.map((model) => (
          <div
            key={model.id}
            className='grid gap-4 px-4 py-4 md:grid-cols-[minmax(14rem,1.4fr)_minmax(12rem,1fr)_9rem_9rem_6rem] md:items-center'
          >
            <div className='min-w-0'>
              <div className='flex flex-wrap items-center gap-2'>
                <span className='font-medium'>{model.name}</span>
                {model.defaultModel && (
                  <Badge variant='default'>
                    <Star />
                    默认
                  </Badge>
                )}
              </div>
              <p className='mt-1 truncate font-mono text-xs text-muted-foreground'>
                {model.modelName}
              </p>
            </div>
            <div className='min-w-0'>
              <p className='truncate text-sm'>{model.baseUrl}</p>
              <p className='mt-1 text-xs text-muted-foreground'>DeepSeek</p>
            </div>
            <div>
              <Badge variant={model.enabled ? 'secondary' : 'outline'}>
                {model.enabled ? <CheckCircle2 /> : <CircleOff />}
                {model.enabled ? '已启用' : '已停用'}
              </Badge>
            </div>
            <div className='min-w-0'>
              {model.lastTestStatus ? (
                <div className='flex items-center gap-2 text-sm'>
                  {model.lastTestStatus === 'success' ? (
                    <CheckCircle2 className='size-4 text-primary' />
                  ) : (
                    <XCircle className='size-4 text-destructive' />
                  )}
                  <span>
                    {model.lastTestStatus === 'success' ? '成功' : '失败'}
                  </span>
                </div>
              ) : (
                <span className='text-sm text-muted-foreground'>未测试</span>
              )}
            </div>
            <div className='flex items-center justify-end gap-1'>
              <Button
                variant='ghost'
                size='icon'
                disabled={pending}
                title='测试连接'
                onClick={() => onTest(model)}
              >
                <PlugZap />
                <span className='sr-only'>测试连接</span>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant='ghost' size='icon' disabled={pending}>
                    <MoreHorizontal />
                    <span className='sr-only'>更多操作</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align='end'>
                  <DropdownMenuGroup>
                    <DropdownMenuItem onSelect={() => onEdit(model)}>
                      <Pencil />
                      编辑
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      disabled={!model.enabled || model.defaultModel}
                      onSelect={() => onSetDefault(model)}
                    >
                      <Star />
                      设为默认
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onToggle(model)}>
                      {model.enabled ? <PowerOff /> : <Power />}
                      {model.enabled ? '停用' : '启用'}
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                  <DropdownMenuSeparator />
                  <DropdownMenuGroup>
                    <DropdownMenuItem
                      variant='destructive'
                      onSelect={() => onDelete(model)}
                    >
                      <Trash2 />
                      删除
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

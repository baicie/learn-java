import { Copy, GripVertical, MoveDown, MoveUp, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { DesignerField } from './types'

type FormCanvasProps = {
  fields: DesignerField[]
  selectedFieldId: string
  onSelect: (id: string) => void
  onMove: (id: string, direction: 'up' | 'down') => void
  onDuplicate: (id: string) => void
  onRemoveOrDisable: (id: string) => void
}

export function FormCanvas({
  fields,
  selectedFieldId,
  onSelect,
  onMove,
  onDuplicate,
  onRemoveOrDisable,
}: FormCanvasProps) {
  return (
    <Card className='h-full'>
      <CardHeader>
        <CardTitle>表单画布</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-3'>
        {fields.length === 0 ? (
          <div className='rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground'>
            从左侧字段库添加字段
          </div>
        ) : null}

        {fields.map((field, index) => (
          <div
            key={field.id}
            className={cn(
              'relative rounded-lg border p-3 text-left transition',
              selectedFieldId === field.id && 'border-primary bg-muted',
              !field.enabled && 'opacity-55'
            )}
          >
            <Button
              type='button'
              variant='ghost'
              className='absolute inset-0 z-0 h-auto w-auto rounded-lg'
              aria-label={`选择字段 ${field.fieldName}`}
              onClick={() => onSelect(field.id)}
            />
            <div className='pointer-events-none relative z-10 flex items-start gap-3'>
              <GripVertical className='mt-1 size-4 text-muted-foreground' />
              <div className='min-w-0 flex-1'>
                <div className='flex items-center gap-2'>
                  <span className='font-medium'>{field.fieldName}</span>
                  <Badge variant='outline'>{field.fieldType}</Badge>
                  {field.locked ? (
                    <Badge variant='outline'>编码锁定</Badge>
                  ) : null}
                  {!field.enabled ? (
                    <Badge variant='secondary'>已禁用</Badge>
                  ) : null}
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  {field.fieldCode}
                </div>
              </div>

              <div className='pointer-events-auto flex gap-1'>
                <Button
                  type='button'
                  size='icon'
                  variant='ghost'
                  disabled={index === 0}
                  aria-label={`上移字段 ${field.fieldName}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    onMove(field.id, 'up')
                  }}
                >
                  <MoveUp className='size-4' />
                </Button>
                <Button
                  type='button'
                  size='icon'
                  variant='ghost'
                  disabled={index === fields.length - 1}
                  aria-label={`下移字段 ${field.fieldName}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    onMove(field.id, 'down')
                  }}
                >
                  <MoveDown className='size-4' />
                </Button>
                <Button
                  type='button'
                  size='icon'
                  variant='ghost'
                  aria-label={`复制字段 ${field.fieldName}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    onDuplicate(field.id)
                  }}
                >
                  <Copy className='size-4' />
                </Button>
                <Button
                  type='button'
                  size='icon'
                  variant='ghost'
                  aria-label={`删除字段 ${field.fieldName}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    onRemoveOrDisable(field.id)
                  }}
                >
                  <Trash2 className='size-4' />
                </Button>
              </div>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

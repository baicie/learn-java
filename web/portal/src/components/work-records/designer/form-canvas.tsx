import {
  Copy,
  EyeOff,
  GripVertical,
  MoveDown,
  MoveUp,
  Trash2,
} from 'lucide-react'
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
            role='button'
            tabIndex={0}
            className={`rounded-lg border p-3 text-left transition ${
              selectedFieldId === field.id ? 'border-primary bg-muted' : ''
            } ${field.enabled ? '' : 'opacity-55'} cursor-pointer`}
            onClick={() => onSelect(field.id)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onSelect(field.id)
              }
            }}
          >
            <div className='flex items-start gap-3'>
              <GripVertical className='mt-1 size-4 text-muted-foreground' />
              <div className='min-w-0 flex-1'>
                <div className='flex items-center gap-2'>
                  <span className='font-medium'>{field.fieldName}</span>
                  <span className='rounded bg-muted px-1.5 py-0.5 text-xs'>
                    {field.fieldType}
                  </span>
                  {field.locked ? (
                    <span className='rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800'>
                      编码锁定
                    </span>
                  ) : null}
                  {!field.enabled ? (
                    <span className='rounded bg-slate-100 px-1.5 py-0.5 text-xs'>
                      已禁用
                    </span>
                  ) : null}
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  {field.fieldCode}
                </div>
              </div>

              <div className='flex gap-1'>
                <Button
                  type='button'
                  size='icon'
                  variant='ghost'
                  disabled={index === 0}
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
                  onClick={(event) => {
                    event.stopPropagation()
                    onRemoveOrDisable(field.id)
                  }}
                >
                  {field.locked || field.referenced ? (
                    <EyeOff className='size-4' />
                  ) : (
                    <Trash2 className='size-4' />
                  )}
                </Button>
              </div>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

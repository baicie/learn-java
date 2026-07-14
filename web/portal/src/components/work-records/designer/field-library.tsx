import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { defaultFieldName } from './schema'
import { type WorkRecordFieldType, WORK_RECORD_FIELD_TYPES } from './types'

type FieldLibraryProps = {
  onAdd: (fieldType: WorkRecordFieldType) => void
}

export function FieldLibrary({ onAdd }: FieldLibraryProps) {
  return (
    <Card className='h-full'>
      <CardHeader>
        <CardTitle>字段库</CardTitle>
      </CardHeader>
      <CardContent className='grid gap-2'>
        {WORK_RECORD_FIELD_TYPES.map((type) => (
          <Button
            key={type}
            type='button'
            variant='outline'
            className='justify-start'
            onClick={() => onAdd(type)}
          >
            <Plus className='mr-2 size-4' />
            {defaultFieldName(type)}
            <span className='ml-auto text-xs text-muted-foreground'>
              {type}
            </span>
          </Button>
        ))}
      </CardContent>
    </Card>
  )
}

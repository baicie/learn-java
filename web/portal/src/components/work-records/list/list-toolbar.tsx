import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type {
  ListQueryState,
  RecordListMeta,
  WorkRecordUserOption,
} from './types'

type Props = {
  meta?: RecordListMeta
  query: ListQueryState
  userOptions: WorkRecordUserOption[]
  onChange: (patch: Partial<ListQueryState>) => void
}

export function ListToolbar({ meta, query, userOptions, onChange }: Props) {
  const { t } = useTranslation()
  return (
    <div className='grid gap-3 rounded-lg border p-4'>
      <div className='grid gap-3 md:grid-cols-4'>
        <Input
          placeholder={t('workRecords.list.searchPlaceholder')}
          value={query.keyword}
          onChange={(event) => onChange({ keyword: event.target.value })}
        />

        <Select
          value={query.templateId || 'all'}
          onValueChange={(value) =>
            onChange({ templateId: value === 'all' ? '' : value })
          }
        >
          <SelectTrigger className='w-full'>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value='all'>
                {t('workRecords.list.allTemplates')}
              </SelectItem>
              {(meta?.templates ?? []).map((template) => (
                <SelectItem key={template.id} value={template.id}>
                  {template.name}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>

        <Select
          value={query.ownerId || 'all'}
          onValueChange={(value) =>
            onChange({ ownerId: value === 'all' ? '' : value })
          }
        >
          <SelectTrigger
            className='w-full'
            aria-label={t('workRecords.field.owner')}
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value='all'>全部负责人</SelectItem>
              {userOptions.map((user) => (
                <SelectItem key={user.id} value={user.id}>
                  {user.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>

        <Input
          placeholder={t('workRecords.field.creator')}
          value={query.creatorId}
          onChange={(event) => onChange({ creatorId: event.target.value })}
        />
      </div>

      <div className='grid gap-3 md:grid-cols-4'>
        <Select
          value={query.statuses[0] ?? 'all'}
          onValueChange={(value) =>
            onChange({ statuses: value === 'all' ? [] : [value] })
          }
        >
          <SelectTrigger className='w-full'>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value='all'>
                {t('workRecords.list.allStatus')}
              </SelectItem>
              <SelectItem value='draft'>
                {t('workRecords.list.status.draft')}
              </SelectItem>
              <SelectItem value='processing'>
                {t('workRecords.list.status.processing')}
              </SelectItem>
              <SelectItem value='done'>
                {t('workRecords.list.status.done')}
              </SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>

        <div className='grid gap-1'>
          <Label htmlFor='record-time-from'>
            {t('workRecords.list.recordTimeFromLabel')}
          </Label>
          <Input
            id='record-time-from'
            type='date'
            value={query.recordTimeFrom}
            onChange={(event) =>
              onChange({ recordTimeFrom: event.target.value })
            }
          />
        </div>

        <div className='grid gap-1'>
          <Label htmlFor='record-time-to'>
            {t('workRecords.list.recordTimeToLabel')}
          </Label>
          <Input
            id='record-time-to'
            type='date'
            value={query.recordTimeTo}
            onChange={(event) => onChange({ recordTimeTo: event.target.value })}
          />
        </div>

        <Button
          type='button'
          variant='outline'
          onClick={() =>
            onChange({
              keyword: '',
              templateId: '',
              statuses: [],
              ownerId: '',
              creatorId: '',
              recordTimeFrom: '',
              recordTimeTo: '',
            })
          }
        >
          {t('workRecords.list.activeFiltersLabel')}
        </Button>
      </div>
    </div>
  )
}

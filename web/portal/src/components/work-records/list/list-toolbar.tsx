import { useState } from 'react'
import { RotateCcw, Search } from 'lucide-react'
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

type FilterDraft = {
  keyword: string
  templateId: string
  ownerId: string
  creatorId: string
  status: string
  recordTimeFrom: string
  recordTimeTo: string
}

function filtersOf(query: ListQueryState): FilterDraft {
  return {
    keyword: query.keyword,
    templateId: query.templateId,
    ownerId: query.ownerId,
    creatorId: query.creatorId,
    status: query.statuses[0] ?? 'all',
    recordTimeFrom: query.recordTimeFrom,
    recordTimeTo: query.recordTimeTo,
  }
}

const EMPTY_DRAFT: FilterDraft = {
  keyword: '',
  templateId: 'all',
  ownerId: 'all',
  creatorId: '',
  status: 'all',
  recordTimeFrom: '',
  recordTimeTo: '',
}

export function ListToolbar({ meta, query, userOptions, onChange }: Props) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState<FilterDraft>(() => filtersOf(query))
  const appliedKey = JSON.stringify(filtersOf(query))
  const [lastAppliedKey, setLastAppliedKey] = useState(appliedKey)

  // Sync the draft when the applied filters change from outside
  // (e.g. route search params), per the render-time adjustment pattern.
  if (lastAppliedKey !== appliedKey) {
    setLastAppliedKey(appliedKey)
    setDraft(JSON.parse(appliedKey) as FilterDraft)
  }

  const applyFilters = () => {
    onChange({
      keyword: draft.keyword,
      templateId: draft.templateId === 'all' ? '' : draft.templateId,
      ownerId: draft.ownerId === 'all' ? '' : draft.ownerId,
      creatorId: draft.creatorId,
      statuses: draft.status === 'all' ? [] : [draft.status],
      recordTimeFrom: draft.recordTimeFrom,
      recordTimeTo: draft.recordTimeTo,
    })
  }

  const resetFilters = () => {
    setDraft(EMPTY_DRAFT)
    onChange({
      keyword: '',
      templateId: '',
      ownerId: '',
      creatorId: '',
      statuses: [],
      recordTimeFrom: '',
      recordTimeTo: '',
    })
  }

  return (
    <form
      className='grid gap-3 rounded-lg border p-4 md:grid-cols-4'
      onSubmit={(event) => {
        event.preventDefault()
        applyFilters()
      }}
    >
      <div className='grid content-start gap-1.5'>
        <Label htmlFor='filter-keyword'>
          {t('workRecords.list.keywordLabel')}
        </Label>
        <div className='relative'>
          <Input
            id='filter-keyword'
            className='pr-8'
            placeholder={t('workRecords.list.searchPlaceholder')}
            value={draft.keyword}
            onChange={(event) =>
              setDraft({ ...draft, keyword: event.target.value })
            }
          />
          <Search className='pointer-events-none absolute top-1/2 right-2.5 size-4 -translate-y-1/2 text-muted-foreground' />
        </div>
      </div>

      <div className='grid content-start gap-1.5'>
        <Label>{t('workRecords.field.template')}</Label>
        <Select
          value={draft.templateId}
          onValueChange={(value) => setDraft({ ...draft, templateId: value })}
        >
          <SelectTrigger
            className='w-full'
            aria-label={t('workRecords.field.template')}
          >
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
      </div>

      <div className='grid content-start gap-1.5'>
        <Label>{t('workRecords.field.owner')}</Label>
        <Select
          value={draft.ownerId}
          onValueChange={(value) => setDraft({ ...draft, ownerId: value })}
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
      </div>

      <div className='grid content-start gap-1.5'>
        <Label htmlFor='filter-creator'>{t('workRecords.field.creator')}</Label>
        <Input
          id='filter-creator'
          placeholder={t('workRecords.field.creator')}
          value={draft.creatorId}
          onChange={(event) =>
            setDraft({ ...draft, creatorId: event.target.value })
          }
        />
      </div>

      <div className='grid content-start gap-1.5'>
        <Label>{t('workRecords.field.status')}</Label>
        <Select
          value={draft.status}
          onValueChange={(value) => setDraft({ ...draft, status: value })}
        >
          <SelectTrigger
            className='w-full'
            aria-label={t('workRecords.field.status')}
          >
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
      </div>

      <div className='grid content-start gap-1.5'>
        <Label htmlFor='record-time-from'>
          {t('workRecords.list.recordTimeFromLabel')}
        </Label>
        <Input
          id='record-time-from'
          type='date'
          value={draft.recordTimeFrom}
          onChange={(event) =>
            setDraft({ ...draft, recordTimeFrom: event.target.value })
          }
        />
      </div>

      <div className='grid content-start gap-1.5'>
        <Label htmlFor='record-time-to'>
          {t('workRecords.list.recordTimeToLabel')}
        </Label>
        <Input
          id='record-time-to'
          type='date'
          value={draft.recordTimeTo}
          onChange={(event) =>
            setDraft({ ...draft, recordTimeTo: event.target.value })
          }
        />
      </div>

      <div className='flex items-end justify-end gap-2'>
        <Button type='button' variant='outline' onClick={resetFilters}>
          <RotateCcw data-icon='inline-start' />
          {t('workRecords.list.activeFiltersLabel')}
        </Button>
        <Button type='submit'>
          <Search data-icon='inline-start' />
          {t('workRecords.list.search')}
        </Button>
      </div>
    </form>
  )
}

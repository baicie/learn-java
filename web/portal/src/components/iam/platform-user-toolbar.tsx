import { useTranslation } from 'react-i18next'
import type { PlatformUserQuery } from '@/lib/iam/platform-user'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const STATUS_VALUES = ['active', 'disabled', 'locked', 'pending'] as const

export type PlatformUserToolbarProps = {
  value: PlatformUserQuery
  onChange: (next: Partial<PlatformUserQuery>) => void
}

export function PlatformUserToolbar({
  value,
  onChange,
}: PlatformUserToolbarProps) {
  const { t } = useTranslation('platform')

  return (
    <div className='flex flex-wrap items-center gap-2 rounded-md border bg-card/40 p-3'>
      <Input
        className='w-64'
        placeholder='按用户名 / 显示名搜索'
        defaultValue={value.keyword ?? ''}
        onBlur={(event) =>
          onChange({ keyword: event.target.value || undefined })
        }
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            onChange({
              keyword: (event.target as HTMLInputElement).value || undefined,
            })
          }
        }}
        data-testid='user-keyword-input'
      />
      <Select
        value={value.status ?? 'all'}
        onValueChange={(next) =>
          onChange({
            status:
              next === 'all'
                ? undefined
                : (next as PlatformUserQuery['status']),
          })
        }
      >
        <SelectTrigger className='w-40' data-testid='user-status-select'>
          <SelectValue placeholder='状态' />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value='all'>全部状态</SelectItem>
          {STATUS_VALUES.map((status) => (
            <SelectItem key={status} value={status}>
              {t(`platform.user.status.${status}`, status)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

import { common } from './common'
import { platform } from './platform'
import { workRecords } from './work-records'

export const enUS = {
  ...common,
  ...platform,
  ...workRecords,
} as const

export type Translation = typeof enUS

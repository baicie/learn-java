import { common } from './common'
import { platform } from './platform'
import { workRecords } from './work-records'

export const zhCN = {
  ...common,
  ...platform,
  ...workRecords,
} as const

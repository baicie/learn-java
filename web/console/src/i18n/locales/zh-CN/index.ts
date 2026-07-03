import { app } from './app'
import { auth } from './auth'
import { common } from './common'
import { nav } from './nav'

export const zhCN = {
  app,
  auth,
  common,
  nav,
} as const

export type Translation = typeof zhCN

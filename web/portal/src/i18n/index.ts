import { zhCN, type MessageKey } from './locales/zh-CN'

const messages: Record<MessageKey, string> = zhCN

export function t(key: MessageKey): string {
  return messages[key] ?? key
}

export type { MessageKey }

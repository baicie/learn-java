import { createFileRoute } from '@tanstack/react-router'
import { DictionariesPage } from '@/features/dictionaries'

export const Route = createFileRoute('/_authenticated/platform/dictionaries')({
  component: DictionariesPage,
})

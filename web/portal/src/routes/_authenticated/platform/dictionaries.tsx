import { createFileRoute } from '@tanstack/react-router'
import { Dictionaries } from '@/features/dictionaries'

export const Route = createFileRoute('/_authenticated/platform/dictionaries')({
  component: Dictionaries,
})

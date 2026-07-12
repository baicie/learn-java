import z from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/features/auth/auth-layout'
import { SignInPage } from '@/features/auth/sign-in'

const searchSchema = z.object({
  redirect: z.string().optional(),
})

export const Route = createFileRoute('/(auth)/sign-in')({
  validateSearch: searchSchema,
  component: () => (
    <AuthLayout>
      <SignInPage />
    </AuthLayout>
  ),
})
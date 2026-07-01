import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const statusBadgeVariants = cva('', {
  variants: {
    tone: {
      neutral: 'border-transparent bg-muted text-muted-foreground',
      info: 'border-transparent bg-secondary text-secondary-foreground',
      success: 'border-transparent bg-primary/10 text-primary',
      warning:
        'border-transparent bg-amber-100 text-amber-900 dark:bg-amber-400/20 dark:text-amber-200',
      danger: 'border-transparent bg-destructive/10 text-destructive',
      muted: 'border-transparent bg-muted text-muted-foreground',
    },
  },
  defaultVariants: {
    tone: 'neutral',
  },
})

const STATUS_TONE_MAP: Record<
  string,
  NonNullable<VariantProps<typeof statusBadgeVariants>['tone']>
> = {
  active: 'success',
  error: 'danger',
  inactive: 'muted',
  open: 'danger',
  investigating: 'warning',
  mitigating: 'info',
  resolved: 'success',
  closed: 'muted',
  ignored: 'muted',
}

const SEVERITY_TONE_MAP: Record<
  string,
  NonNullable<VariantProps<typeof statusBadgeVariants>['tone']>
> = {
  critical: 'danger',
  high: 'danger',
  warning: 'warning',
  medium: 'warning',
  info: 'info',
  low: 'muted',
}

export function StatusBadge({ status }: { status: string }) {
  const tone = STATUS_TONE_MAP[status] ?? 'neutral'
  return (
    <span
      data-slot="status-badge"
      data-tone={tone}
      className={cn(
        'inline-flex h-5 items-center rounded-4xl border px-2 text-xs font-medium capitalize',
        statusBadgeVariants({ tone }),
      )}
    >
      {status}
    </span>
  )
}

export function SeverityBadge({ severity }: { severity: string }) {
  const tone = SEVERITY_TONE_MAP[severity.toLowerCase()] ?? 'neutral'
  return (
    <span
      data-slot="severity-badge"
      data-tone={tone}
      className={cn(
        'inline-flex h-5 items-center rounded-4xl border px-2 text-xs font-medium uppercase tracking-wide',
        statusBadgeVariants({ tone }),
      )}
    >
      {severity}
    </span>
  )
}

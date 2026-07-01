import { LogOutIcon, UserIcon, Wand2Icon } from 'lucide-react'

import type { Me } from '@/api/client'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

export function ConsoleHeader({
  user,
  onLogout,
  isAggregating,
  onAggregate,
}: {
  user: Me | null
  onLogout: () => void
  isAggregating: boolean
  onAggregate: () => void
}) {
  const displayName = user?.displayName || 'Admin'
  const initials = displayName.slice(0, 1).toUpperCase()

  return (
    <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
        <div className="flex items-center gap-3">
          <div className="flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Wand2Icon className="size-4" />
          </div>
          <div>
            <h1 className="text-base leading-none font-semibold">AegisOps Console</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              Phase 2 Incident aggregation center
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Button size="sm" onClick={onAggregate} disabled={isAggregating}>
            <Wand2Icon data-icon="inline-start" />
            {isAggregating ? 'Aggregating' : 'Aggregate Incidents'}
          </Button>
          <Separator orientation="vertical" className="h-6" />
          <div className="flex items-center gap-2">
            <Avatar size="sm">
              <AvatarFallback>{initials}</AvatarFallback>
            </Avatar>
            <div className="hidden text-right sm:block">
              <p className="text-sm leading-none font-medium">{displayName}</p>
              <p className="mt-1 flex items-center justify-end gap-1 text-xs text-muted-foreground">
                <UserIcon className="size-3" />
                {user?.roles?.[0] || 'viewer'}
              </p>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={onLogout}>
            <LogOutIcon data-icon="inline-start" />
            Logout
          </Button>
        </div>
      </div>
    </header>
  )
}

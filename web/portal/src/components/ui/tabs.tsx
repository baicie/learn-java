import {
  createContext,
  useContext,
  useState,
  type ComponentProps,
  type ReactNode,
} from 'react'
import { cn } from '@/lib/utils'

type TabsContextValue = {
  value: string
  setValue: (value: string) => void
}

const TabsContext = createContext<TabsContextValue | null>(null)

function useTabs() {
  const context = useContext(TabsContext)
  if (!context) throw new Error('Tabs components must be used inside Tabs')
  return context
}

export function Tabs({
  defaultValue,
  className,
  children,
}: {
  defaultValue: string
  className?: string
  children: ReactNode
}) {
  const [value, setValue] = useState(defaultValue)
  return (
    <TabsContext.Provider value={{ value, setValue }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

export function TabsList({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      role='tablist'
      className={cn(
        'inline-flex min-h-9 items-center rounded-lg bg-muted p-1 text-muted-foreground',
        className
      )}
      {...props}
    />
  )
}

export function TabsTrigger({
  value,
  className,
  ...props
}: ComponentProps<'button'> & { value: string }) {
  const tabs = useTabs()
  const active = tabs.value === value
  return (
    <button
      type='button'
      role='tab'
      aria-selected={active}
      className={cn(
        'inline-flex min-h-7 items-center justify-center gap-1.5 rounded-md px-3 py-1 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
        active && 'bg-background text-foreground shadow-sm',
        className
      )}
      onClick={() => tabs.setValue(value)}
      {...props}
    />
  )
}

export function TabsContent({
  value,
  className,
  ...props
}: ComponentProps<'div'> & { value: string }) {
  const tabs = useTabs()
  if (tabs.value !== value) return null
  return (
    <div
      role='tabpanel'
      tabIndex={0}
      className={cn(
        'outline-none focus-visible:ring-2 focus-visible:ring-ring',
        className
      )}
      {...props}
    />
  )
}

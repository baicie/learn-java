from __future__ import annotations

import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit('usage: fix-ui-primitives.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
target = package / 'scripts/fix-typecheck-targets.py'
if not target.is_file():
    raise SystemExit(f'missing generated typecheck fixer: {target}')

content = target.read_text(encoding='utf-8')
src_declaration = "SRC = Path('web/portal/src')\n"
if src_declaration not in content:
    import_marker = 'from pathlib import Path\n'
    if import_marker not in content:
        raise SystemExit('Path import marker not found in typecheck fixer')
    content = content.replace(
        import_marker,
        import_marker + '\n' + src_declaration,
        1,
    )

ui_block = r'''# Complete the shadcn-style primitives referenced by migrated screens.
ui_dir = SRC / 'components/ui'
ui_dir.mkdir(parents=True, exist_ok=True)
(ui_dir / 'spinner.tsx').write_text(
    """import type { ComponentProps } from 'react'
import { LoaderCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Spinner({ className, ...props }: ComponentProps<typeof LoaderCircle>) {
  return (
    <LoaderCircle
      role='status'
      aria-label='加载中'
      className={cn('size-4 animate-spin', className)}
      {...props}
    />
  )
}
""",
    encoding='utf-8',
)
(ui_dir / 'toggle-group.tsx').write_text(
    """import {
  createContext,
  useContext,
  type ComponentProps,
  type ReactNode,
} from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type ToggleGroupContextValue = {
  value?: string
  onValueChange?: (value: string) => void
  variant: ComponentProps<typeof Button>['variant']
  size: ComponentProps<typeof Button>['size']
}

const ToggleGroupContext = createContext<ToggleGroupContextValue>({
  variant: 'outline',
  size: 'default',
})

type ToggleGroupProps = Omit<ComponentProps<'div'>, 'onChange'> & {
  type: 'single'
  value?: string
  onValueChange?: (value: string) => void
  variant?: ComponentProps<typeof Button>['variant']
  size?: ComponentProps<typeof Button>['size']
  children: ReactNode
}

export function ToggleGroup({
  value,
  onValueChange,
  variant = 'outline',
  size = 'default',
  className,
  children,
  ...props
}: ToggleGroupProps) {
  return (
    <ToggleGroupContext.Provider value={{ value, onValueChange, variant, size }}>
      <div
        role='group'
        className={cn('flex items-center gap-1', className)}
        {...props}
      >
        {children}
      </div>
    </ToggleGroupContext.Provider>
  )
}

type ToggleGroupItemProps = Omit<ComponentProps<typeof Button>, 'value'> & {
  value: string
}

export function ToggleGroupItem({
  value,
  className,
  onClick,
  ...props
}: ToggleGroupItemProps) {
  const context = useContext(ToggleGroupContext)
  const selected = context.value === value

  return (
    <Button
      type='button'
      variant={selected ? 'secondary' : context.variant}
      size={context.size}
      aria-pressed={selected}
      data-state={selected ? 'on' : 'off'}
      className={cn(className)}
      onClick={(event) => {
        onClick?.(event)
        if (!event.defaultPrevented) {
          context.onValueChange?.(selected ? '' : value)
        }
      }}
      {...props}
    />
  )
}
""",
    encoding='utf-8',
)
'''
if ui_block not in content:
    content = content.rstrip() + '\n\n' + ui_block + '\n'

target.write_text(content, encoding='utf-8')

phase7 = package / 'scripts/phase21-07-cleanup.sh'
phase7_content = phase7.read_text(encoding='utf-8')
format_marker = 'pnpm --dir web/portal run format\n'
route_generation = (
    'rm -f web/portal/src/routeTree.gen.ts\n'
    'pnpm --dir web/portal exec vite build\n'
)
if route_generation not in phase7_content:
    if format_marker not in phase7_content:
        raise SystemExit('phase21-07 format marker not found')
    phase7_content = phase7_content.replace(
        format_marker,
        route_generation + format_marker,
        1,
    )
phase7.write_text(phase7_content, encoding='utf-8')

print(f'patched UI primitives and route generation in {package}')

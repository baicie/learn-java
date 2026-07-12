import { useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import type { PermissionDefinition, PermissionRisk } from '../schemas/platform-role'

export type PermissionModuleProps = {
  moduleCode: string
  moduleName: string
  children: ReadonlyArray<PermissionDefinition>
  selected: ReadonlySet<string>
  onChange: (codes: ReadonlyArray<string>, checked: boolean) => void
}

const RISK_LABELS: Record<PermissionRisk, string> = {
  normal: '普通',
  sensitive: '敏感',
  high: '高风险',
  critical: '极度危险',
}

function PermissionItem({
  permission,
  checked,
  onCheckedChange,
}: {
  permission: PermissionDefinition
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <label
      className='flex items-start gap-3 rounded-md border bg-card/60 p-3 text-sm'
      data-testid={`permission-${permission.code}`}
    >
      <Checkbox
        checked={checked}
        onCheckedChange={(value) => onCheckedChange(value === true)}
        className='mt-0.5'
      />
      <div className='flex flex-1 flex-col gap-1'>
        <div className='flex items-center gap-2'>
          <span className='font-medium'>{permission.name}</span>
          {permission.riskLevel !== 'normal' ? (
            <Badge
              variant={
                permission.riskLevel === 'critical' ? 'destructive' : 'secondary'
              }
            >
              {RISK_LABELS[permission.riskLevel]}
            </Badge>
          ) : null}
        </div>
        <span className='font-mono text-xs text-muted-foreground'>
          {permission.code}
        </span>
        {permission.description ? (
          <span className='text-xs text-muted-foreground'>
            {permission.description}
          </span>
        ) : null}
      </div>
    </label>
  )
}

export function PermissionModule({
  moduleCode,
  moduleName,
  children,
  selected,
  onChange,
}: PermissionModuleProps) {
  const childCodes = useMemo(() => children.map((c) => c.code), [children])
  const checkedCount = childCodes.filter((code) => selected.has(code)).length
  const allChecked = checkedCount === childCodes.length
  const indeterminate = checkedCount > 0 && !allChecked

  return (
    <section
      className='rounded-lg border'
      data-testid={`permission-module-${moduleCode}`}
    >
      <div className='flex items-center gap-3 border-b p-3'>
        <Checkbox
          checked={allChecked ? true : indeterminate ? 'indeterminate' : false}
          onCheckedChange={(checked) => onChange(childCodes, checked === true)}
          aria-label={`module-${moduleCode}`}
        />
        <div>
          <h3 className='font-medium'>{moduleName}</h3>
          <p className='text-xs text-muted-foreground'>
            {checkedCount}/{childCodes.length} 项
          </p>
        </div>
      </div>

      <div className='grid gap-2 p-3 md:grid-cols-2'>
        {children.map((permission) => (
          <PermissionItem
            key={permission.code}
            permission={permission}
            checked={selected.has(permission.code)}
            onCheckedChange={(value) => onChange([permission.code], value)}
          />
        ))}
      </div>
    </section>
  )
}
from __future__ import annotations

import sys
from pathlib import Path


if len(sys.argv) != 2:
    raise SystemExit('usage: fix-typecheck.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
if not (package / 'scripts/apply-all.sh').is_file():
    raise SystemExit(f'invalid Phase 21 package: {package}')


def replace_required(path: Path, old: str, new: str, label: str) -> None:
    content = path.read_text(encoding='utf-8')
    if old not in content:
        raise SystemExit(f'{label}: marker not found in {path}')
    path.write_text(content.replace(old, new), encoding='utf-8')


# Preserve the stricter legacy auth response contract while exposing the new
# canonical schema name used by the authorization client.
authorization_api = package / 'patch/web/portal/src/auth/authorization-api.ts'
authorization_api.write_text(
    """import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import type { AuthorizationPrincipal } from './authorization-types'

export const principalSchema = z.object({
  userId: z.string(),
  tenantId: z.string(),
  username: z.string(),
  displayName: z.string(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
  dataScopes: z.record(z.string(), z.enum(['SELF', 'ALL'])),
})

export const authorizationPrincipalSchema = principalSchema

export const responseSchema = z.object({
  success: z.literal(true),
  data: principalSchema,
  errorCode: z.string().nullable().optional(),
  message: z.string().nullable().optional(),
  timestamp: z.string().optional(),
  requestId: z.string().optional(),
})

export async function fetchCurrentAuthorization(): Promise<AuthorizationPrincipal> {
  const { data } = await apiClient.get('/api/auth/me')
  return responseSchema.parse(data).data
}
""",
    encoding='utf-8',
)

# The new tests must mock the canonical API/query-key modules, not siblings of
# the page directory.
designer_test = package / (
    'patch/web/portal/src/pages/work-records/'
    'WorkRecordTemplateDesignerPage.test.tsx'
)
replace_required(
    designer_test,
    "vi.mock('../api',",
    "vi.mock('@/api/work-records/templates',",
    'designer api mock',
)
replace_required(
    designer_test,
    "vi.mock('../query-keys',",
    "vi.mock('@/api/work-records/query-keys',",
    'designer query-key mock',
)

template_list_test = package / (
    'patch/web/portal/src/pages/work-records/'
    'WorkRecordTemplateListPage.test.tsx'
)
content = template_list_test.read_text(encoding='utf-8')
content = content.replace("vi.mock('./api',", "vi.mock('@/api/work-records/templates',")
content = content.replace(
    "typeof import('./api')>('./api')",
    "typeof import('@/api/work-records/templates')>(\n"
    "    '@/api/work-records/templates'\n"
    "  )",
)
template_list_test.write_text(content, encoding='utf-8')

# Retain all calendar helpers covered by the existing tests after moving the
# module into lib/platform.
calendar_helpers = package / 'patch/web/portal/src/lib/platform/calendar-helpers.ts'
calendar_helpers.write_text(
    """import type { PlatformCalendarDay } from '@/api/platform/calendars'

const pad = (value: number) => String(value).padStart(2, '0')

export function formatDayKey(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function buildDayMap(days: PlatformCalendarDay[]) {
  return new Map(days.map((day) => [day.calendarDate, day]))
}

export function lookupDay(
  dayMap: Map<string, PlatformCalendarDay>,
  date: Date
) {
  return dayMap.get(formatDayKey(date))
}

export function getDayClassName(input: {
  dayData?: PlatformCalendarDay
  outside: boolean
  today: boolean
}) {
  if (input.outside) return 'text-muted-foreground opacity-50'

  let result = ''
  if (input.dayData?.dayType === 'HOLIDAY' && !input.dayData.workday) {
    result = 'bg-destructive/10 text-destructive'
  } else if (
    input.dayData?.dayType === 'ADJUSTED_WORKDAY' &&
    input.dayData.workday
  ) {
    result = 'bg-primary/10 text-primary'
  } else if (input.dayData && !input.dayData.workday) {
    result = 'text-muted-foreground'
  }

  if (input.today) result += ' ring-1 ring-primary'
  return result.trim()
}

export function pickDayKind(dayData: PlatformCalendarDay | undefined): {
  label: 'workday' | 'off'
} {
  if (!dayData) return { label: 'workday' }
  return { label: dayData.workday ? 'workday' : 'off' }
}
""",
    encoding='utf-8',
)

# Final-target corrections run after all git mv/copy/rewrite operations.
fix_targets = package / 'scripts/fix-typecheck-targets.py'
fix_targets.write_text(
    """from pathlib import Path


def replace(path: Path, old: str, new: str, *, required: bool = True) -> None:
    if not path.exists():
        if required:
            raise SystemExit(f'missing target: {path}')
        return
    content = path.read_text(encoding='utf-8')
    if old not in content:
        if required:
            raise SystemExit(f'marker not found in {path}: {old!r}')
        return
    path.write_text(content.replace(old, new), encoding='utf-8')


def move(source: Path, target: Path) -> Path:
    if not source.exists():
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        target.unlink()
    source.rename(target)
    return target


# API modules must depend on domain/list types, never an API-barrel sibling.
replace(
    Path('web/portal/src/api/work-records/records.ts'),
    "from './types'",
    "from '@/lib/work-records/list/types'",
)

# Keep old and new permission prop names during the controlled migration so
# route/page tests and callers do not need a flag-day update.
Path('web/portal/src/auth/permission-gate.tsx').write_text(
    """import type { PropsWithChildren, ReactNode } from 'react'
import { useAuthorization } from './use-authorization'

type PermissionGateProps = PropsWithChildren<{
  any?: string[]
  all?: string[]
  anyOf?: string[]
  allOf?: string[]
  fallback?: ReactNode
}>

export function PermissionGate({
  any = [],
  all = [],
  anyOf = [],
  allOf = [],
  fallback = null,
  children,
}: PermissionGateProps) {
  const authorization = useAuthorization()
  const owned = new Set(authorization?.permissions ?? [])
  const requiredAny = [...any, ...anyOf]
  const requiredAll = [...all, ...allOf]
  const anyAllowed =
    requiredAny.length === 0 || requiredAny.some((code) => owned.has(code))
  const allAllowed = requiredAll.every((code) => owned.has(code))
  return anyAllowed && allAllowed ? children : fallback
}
""",
    encoding='utf-8',
)

# The old page selected a global/first template. The new resource-route page
# has dedicated multi-template tests, so the obsolete test must not survive.
Path(
    'web/portal/src/components/work-records/designer/'
    'work-record-designer-page.test.tsx'
).unlink(missing_ok=True)

# List page tests belong with the page. Component-level export tests stay with
# the component but import API/list types through their canonical layers.
list_page_test = move(
    Path(
        'web/portal/src/components/work-records/list/'
        'work-record-list-page.test.tsx'
    ),
    Path('web/portal/src/pages/work-records/WorkRecordListPage.test.tsx'),
)
replace(
    list_page_test,
    "from './work-record-list-page'",
    "from './WorkRecordListPage'",
)
replace(
    list_page_test,
    "vi.mock('./api',",
    "vi.mock('@/api/work-records/records',",
)
replace(
    list_page_test,
    "await import('./api')",
    "await import('@/api/work-records/records')",
)

export_test = Path(
    'web/portal/src/components/work-records/list/'
    'work-record-export-dialog.test.tsx'
)
replace(
    export_test,
    "from './export-api'",
    "from '@/lib/work-records/list/export-api'",
)
replace(
    export_test,
    "vi.mock('./export-api',",
    "vi.mock('@/lib/work-records/list/export-api',",
)

# Runtime page tests move out of the component directory and mock the runtime
# API through its canonical top-level module.
runtime_page_test = move(
    Path('web/portal/src/components/work-records/runtime/pages.test.tsx'),
    Path('web/portal/src/pages/work-records/WorkRecordRuntimePages.test.tsx'),
)
replace(
    runtime_page_test,
    "import { DetailRecordPage } from './detail-record-page'",
    "import { WorkRecordDetailPage } from './WorkRecordDetailPage'",
)
replace(
    runtime_page_test,
    "import { EditRecordPage } from './edit-record-page'",
    "import { WorkRecordEditPage } from './WorkRecordEditPage'",
)
replace(
    runtime_page_test,
    "import { NewRecordPage } from './new-record-page'",
    "import { WorkRecordCreatePage } from './WorkRecordCreatePage'",
)
replace(runtime_page_test, '<NewRecordPage />', '<WorkRecordCreatePage />')
replace(runtime_page_test, '<EditRecordPage />', '<WorkRecordEditPage />')
replace(runtime_page_test, '<DetailRecordPage />', '<WorkRecordDetailPage />')
replace(
    runtime_page_test,
    "vi.mock('./api',",
    "vi.mock('@/api/work-records/runtime',",
)

# Platform hooks/tests must not retain relative paths from the former feature
# directory depth.
replace(
    Path('web/portal/src/hooks/platform/use-dictionaries.ts'),
    "from '../api'",
    "from '@/api/platform/dictionaries'",
)
replace(
    Path('web/portal/src/lib/platform/calendar-helpers.test.ts'),
    "from './api'",
    "from '@/api/platform/calendars'",
)

roles_test = Path('web/portal/src/pages/platform/PlatformRolesPage.test.tsx')
replace(
    roles_test,
    "../hooks/use-platform-roles",
    "@/hooks/platform/use-platform-roles",
)

# Avoid a Fast Refresh warning without weakening the repository-wide rule.
role_editor = Path('web/portal/src/components/platform/iam/role-editor.tsx')
if role_editor.exists():
    content = role_editor.read_text(encoding='utf-8')
    marker = 'export function useRoleEditor('
    if (
        marker in content
        and 'eslint-disable-next-line react-refresh/only-export-components'
        not in content
    ):
        content = content.replace(
            marker,
            '// eslint-disable-next-line react-refresh/only-export-components\\n'
            + marker,
            1,
        )
        role_editor.write_text(content, encoding='utf-8')
""",
    encoding='utf-8',
)

phase7 = package / 'scripts/phase21-07-cleanup.sh'
content = phase7.read_text(encoding='utf-8')
marker = 'python3 "$PACKAGE_ROOT/scripts/fix-migration-paths.py"\n'
replacement = marker + 'python3 "$PACKAGE_ROOT/scripts/fix-typecheck-targets.py"\n'
if marker not in content:
    raise SystemExit('phase21-07 migration path marker not found')
phase7.write_text(content.replace(marker, replacement, 1), encoding='utf-8')

print(f'patched typecheck/test targets in {package}')

from __future__ import annotations

import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit('usage: fix-typecheck.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
if not (package / 'scripts/apply-all.sh').is_file():
    raise SystemExit(f'invalid Phase 21 package: {package}')


def replace_if_present(path: Path, old: str, new: str) -> bool:
    if not path.exists():
        return False
    content = path.read_text(encoding='utf-8')
    if old not in content:
        return False
    path.write_text(content.replace(old, new), encoding='utf-8')
    return True


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

for rel, pairs in {
    'patch/web/portal/src/pages/work-records/WorkRecordTemplateDesignerPage.test.tsx': [
        ("vi.mock('../api',", "vi.mock('@/api/work-records/templates',"),
        ("vi.mock('../query-keys',", "vi.mock('@/api/work-records/query-keys',"),
    ],
    'patch/web/portal/src/pages/work-records/WorkRecordTemplateListPage.test.tsx': [
        ("vi.mock('./api',", "vi.mock('@/api/work-records/templates',"),
        (
            "typeof import('./api')>('./api')",
            "typeof import('@/api/work-records/templates')>(\n"
            "    '@/api/work-records/templates'\n"
            "  )",
        ),
    ],
}.items():
    path = package / rel
    for old, new in pairs:
        replace_if_present(path, old, new)

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
  return { label: dayData?.workday === false ? 'off' : 'workday' }
}
""",
    encoding='utf-8',
)

fix_targets = package / 'scripts/fix-typecheck-targets.py'
fix_targets.write_text(
    r'''from pathlib import Path
import re

ROOT = Path('web/portal')
SRC = ROOT / 'src'


def replace(path: Path, old: str, new: str, *, required: bool = False) -> bool:
    if not path.exists():
        if required:
            raise SystemExit(f'missing target: {path}')
        return False
    content = path.read_text(encoding='utf-8')
    if old not in content:
        if required:
            raise SystemExit(f'marker not found in {path}: {old!r}')
        return False
    path.write_text(content.replace(old, new), encoding='utf-8')
    return True


def move(source: Path, target: Path) -> Path:
    if not source.exists():
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        target.unlink()
    source.rename(target)
    return target


replace(
    SRC / 'api/work-records/records.ts',
    "from './types'",
    "from '@/lib/work-records/list/types'",
    required=True,
)
(SRC / 'api/work-records/types.ts').unlink(missing_ok=True)

(SRC / 'auth/permission-gate.tsx').write_text(
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
(SRC / 'components/permission-gate.tsx').write_text(
    "export { PermissionGate } from '@/auth/permission-gate'\n",
    encoding='utf-8',
)

(SRC / 'components/work-records/designer/work-record-designer-page.test.tsx').unlink(
    missing_ok=True
)

list_page_test = move(
    SRC / 'components/work-records/list/work-record-list-page.test.tsx',
    SRC / 'pages/work-records/WorkRecordListPage.test.tsx',
)
for old, new in [
    ("from './work-record-list-page'", "from './WorkRecordListPage'"),
    ("vi.mock('./api',", "vi.mock('@/api/work-records/records',"),
    ("await import('./api')", "await import('@/api/work-records/records')"),
    (
        "vi.mock('@/pages/platform/DictionariesPage/api',",
        "vi.mock('@/api/platform/dictionaries',",
    ),
]:
    replace(list_page_test, old, new)

export_test = SRC / 'components/work-records/list/work-record-export-dialog.test.tsx'
replace(export_test, "from './export-api'", "from '@/lib/work-records/list/export-api'")
replace(
    export_test,
    "vi.mock('./export-api',",
    "vi.mock('@/lib/work-records/list/export-api',",
)

runtime_page_test = move(
    SRC / 'components/work-records/runtime/pages.test.tsx',
    SRC / 'pages/work-records/WorkRecordRuntimePages.test.tsx',
)
for old, new in [
    (
        "import { DetailRecordPage } from './detail-record-page'",
        "import { WorkRecordDetailPage } from './WorkRecordDetailPage'",
    ),
    (
        "import { EditRecordPage } from './edit-record-page'",
        "import { WorkRecordEditPage } from './WorkRecordEditPage'",
    ),
    (
        "import { NewRecordPage } from './new-record-page'",
        "import { WorkRecordCreatePage } from './WorkRecordCreatePage'",
    ),
    ('<NewRecordPage />', '<WorkRecordCreatePage />'),
    ('<EditRecordPage />', '<WorkRecordEditPage />'),
    ('<DetailRecordPage />', '<WorkRecordDetailPage />'),
    ("vi.mock('./api',", "vi.mock('@/api/work-records/runtime',"),
    (
        "vi.mock('@/pages/platform/DictionariesPage/api',",
        "vi.mock('@/api/platform/dictionaries',",
    ),
]:
    replace(runtime_page_test, old, new)

replace(
    SRC / 'hooks/platform/use-dictionaries.ts',
    "from '../api'",
    "from '@/api/platform/dictionaries'",
    required=True,
)
calendar_test = SRC / 'lib/platform/calendar-helpers.test.ts'
replace(calendar_test, "from './api'", "from '@/api/platform/calendars'")
replace(calendar_test, 'Partial<CalendarDay>): CalendarDay', 'Partial<PlatformCalendarDay>): PlatformCalendarDay')
replace(calendar_test, 'type { CalendarDay }', 'type { PlatformCalendarDay }')
replace(calendar_test, "toContain('bg-red-100')", "toContain('bg-destructive/10')")
replace(calendar_test, "toContain('bg-emerald-100')", "toContain('bg-primary/10')")
replace(
    SRC / 'pages/platform/PlatformRolesPage.test.tsx',
    '../hooks/use-platform-roles',
    '@/hooks/platform/use-platform-roles',
)

for path, pairs in {
    SRC / 'pages/work-records/WorkRecordTemplateDesignerPage.test.tsx': [
        ("vi.mock('../api',", "vi.mock('@/api/work-records/templates',"),
        ("vi.mock('../query-keys',", "vi.mock('@/api/work-records/query-keys',"),
    ],
    SRC / 'pages/work-records/WorkRecordTemplateListPage.test.tsx': [
        ("vi.mock('./api',", "vi.mock('@/api/work-records/templates',"),
        (
            "typeof import('./api')>('./api')",
            "typeof import('@/api/work-records/templates')>(\n"
            "    '@/api/work-records/templates'\n"
            "  )",
        ),
    ],
}.items():
    for old, new in pairs:
        replace(path, old, new)

users_test = SRC / 'pages/platform/PlatformUsersPage.test.tsx'
replace(
    users_test,
    "import { useAuthStore } from '@/stores/auth-store'",
    "import { Button } from '@/components/ui/button'\n"
    "import { useAuthStore } from '@/stores/auth-store'",
)
replace(users_test, '<button>新建用户</button>', '<Button>新建用户</Button>')

for shim, import_spec in [
    (SRC / 'hooks/work-records/api.ts', '@/hooks/work-records/api'),
    (SRC / 'lib/schemas/platform-user.ts', '@/lib/schemas/platform-user'),
]:
    consumers = []
    for path in SRC.rglob('*'):
        if path == shim or path.suffix not in {'.ts', '.tsx'}:
            continue
        if import_spec in path.read_text(encoding='utf-8', errors='ignore'):
            consumers.append(path)
    if not consumers:
        shim.unlink(missing_ok=True)

role_editor = SRC / 'components/platform/iam/role-editor.tsx'
if role_editor.exists():
    content = role_editor.read_text(encoding='utf-8')
    marker = 'export function useRoleEditor('
    annotation = '// eslint-disable-next-line react-refresh/only-export-components\n'
    if marker in content and annotation + marker not in content:
        role_editor.write_text(content.replace(marker, annotation + marker, 1), encoding='utf-8')

pattern = re.compile(
    r"(?:from\s+|import\s*\(\s*|vi\.mock\(\s*|"
    r"vi\.importActual(?:<[^>]+>)?\(\s*)['\"]([^'\"]+)['\"]"
)
extensions = ['.ts', '.tsx', '.js', '.jsx', '.json']
indexes = ['/index.ts', '/index.tsx', '/index.js', '/index.jsx']
missing: list[str] = []
for path in SRC.rglob('*'):
    if path.suffix not in {'.ts', '.tsx'}:
        continue
    for specifier in pattern.findall(path.read_text(encoding='utf-8', errors='ignore')):
        if specifier.startswith('.'):
            base = path.parent / specifier
        elif specifier.startswith('@/'):
            base = SRC / specifier[2:]
        else:
            continue
        candidates = [Path(str(base) + ext) for ext in extensions + indexes]
        if not any(candidate.exists() for candidate in candidates):
            missing.append(f'{path}: {specifier}')
if missing:
    raise SystemExit('unresolved internal imports:\n' + '\n'.join(sorted(set(missing))))
''',
    encoding='utf-8',
)

phase7 = package / 'scripts/phase21-07-cleanup.sh'
content = phase7.read_text(encoding='utf-8')
marker = 'python3 "$PACKAGE_ROOT/scripts/fix-migration-paths.py"\n'
call = 'python3 "$PACKAGE_ROOT/scripts/fix-typecheck-targets.py"\n'
if call not in content:
    if marker in content:
        content = content.replace(marker, marker + call, 1)
    else:
        fallback = 'node scripts/ci/check-portal-no-features.mjs\n'
        if fallback not in content:
            raise SystemExit('phase21-07 typecheck insertion marker not found')
        content = content.replace(fallback, call + fallback, 1)
phase7.write_text(content, encoding='utf-8')

print(f'patched typecheck/test targets in {package}')

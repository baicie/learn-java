from __future__ import annotations

import sys
from pathlib import Path


if len(sys.argv) != 2:
    raise SystemExit('usage: fix-lint.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
if not (package / 'scripts/apply-all.sh').is_file():
    raise SystemExit(f'invalid Phase 21 package: {package}')

create_page = package / 'patch/web/portal/src/pages/work-records/WorkRecordCreatePage.tsx'
content = create_page.read_text(encoding='utf-8')
old = (
    "import type { WorkRecordRuntimeFormValue, WorkRecordStatus } "
    "from '@/lib/work-records/runtime/types'"
)
new = "import type { WorkRecordStatus } from '@/lib/work-records/runtime/types'"
if old not in content:
    raise SystemExit('WorkRecordCreatePage unused type marker not found')
create_page.write_text(content.replace(old, new), encoding='utf-8')

route = package / (
    'patch/web/portal/src/routes/_authenticated/work-records/templates/'
    '$templateId.designer.tsx'
)
route.write_text(
    """import { createFileRoute } from '@tanstack/react-router'
import { requireAnyPermission } from '@/auth/permission'
import { WorkRecordTemplateDesignerPage } from '@/pages/work-records/WorkRecordTemplateDesignerPage'

export const Route = createFileRoute(
  '/_authenticated/work-records/templates/$templateId/designer'
)({
  beforeLoad: () => requireAnyPermission(['work-record:template:read']),
  component: TemplateDesignerRoute,
})

function TemplateDesignerRoute() {
  const { templateId } = Route.useParams()
  return <WorkRecordTemplateDesignerPage templateId={templateId} />
}
""",
    encoding='utf-8',
)

fix_script = package / 'scripts/fix-lint-targets.py'
fix_script.write_text(
    """from pathlib import Path

role_editor = Path('web/portal/src/components/platform/iam/role-editor.tsx')
content = role_editor.read_text(encoding='utf-8')
marker = 'export function useRoleEditor('
replacement = (
    '// Fast Refresh only needs component-only exports; this file intentionally '\
    'co-locates the role editor hook and view.\\n'
    '// eslint-disable-next-line react-refresh/only-export-components\\n'
    + marker
)
if marker not in content:
    raise SystemExit('role editor hook marker not found')
if 'eslint-disable-next-line react-refresh/only-export-components' not in content:
    content = content.replace(marker, replacement, 1)
role_editor.write_text(content, encoding='utf-8')
""",
    encoding='utf-8',
)

phase7 = package / 'scripts/phase21-07-cleanup.sh'
content = phase7.read_text(encoding='utf-8')
marker = 'python3 "$PACKAGE_ROOT/scripts/fix-migration-paths.py"\n'
replacement = marker + 'python3 "$PACKAGE_ROOT/scripts/fix-lint-targets.py"\n'
if marker not in content:
    raise SystemExit('phase21-07 migration path marker not found')
phase7.write_text(content.replace(marker, replacement, 1), encoding='utf-8')

print(f'patched ESLint targets in {package}')

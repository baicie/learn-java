from __future__ import annotations

import sys
import textwrap
from pathlib import Path


if len(sys.argv) != 2:
    raise SystemExit('usage: normalize.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
if not (package / 'scripts/apply-all.sh').is_file():
    raise SystemExit(f'invalid Phase 21 package: {package}')


def replace_required(path: Path, old: str, new: str, label: str) -> None:
    content = path.read_text(encoding='utf-8')
    if old not in content:
        raise SystemExit(f'{label}: marker not found in {path}')
    path.write_text(content.replace(old, new), encoding='utf-8')


# Phase 21.2 只负责 IAM 页面，避免提前创建字典/日历目标文件。
phase2 = package / 'scripts/phase21-02-iam.sh'
replace_required(
    phase2,
    'copy_patch "$PACKAGE_ROOT" web/portal/src/pages/platform\n'
    'copy_patch "$PACKAGE_ROOT" web/portal/src/components/platform/iam\n',
    'copy_patch "$PACKAGE_ROOT" web/portal/src/pages/platform/PlatformUsersPage.tsx\n'
    'copy_patch "$PACKAGE_ROOT" web/portal/src/pages/platform/PlatformRolesPage.tsx\n'
    'copy_patch "$PACKAGE_ROOT" web/portal/src/components/platform/iam\n',
    'phase21-02 ownership',
)

# 删除已由多模板路由替代的单模板 designer 路由。
phase6 = package / 'scripts/phase21-06-routes.sh'
replace_required(
    phase6,
    'copy_patch "$PACKAGE_ROOT" web/portal/src/routes\n',
    'rm -f web/portal/src/routes/_authenticated/work-records/designer.tsx\n'
    'copy_patch "$PACKAGE_ROOT" web/portal/src/routes\n',
    'phase21-06 legacy designer route',
)

# 补齐原脚本遗漏的旧 features 引用。
rewrite = package / 'scripts/rewrite-imports.py'
replace_required(
    rewrite,
    "    '@/features/auth/permission': '@/auth/permission',\n",
    "    '@/features/auth/permission': '@/auth/permission',\n"
    "    '@/features/auth/authorization-types': '@/auth/authorization-types',\n"
    "    '@/features/settings': '@/pages/settings',\n",
    'features import rewrite',
)

# field 组件由项目内确定性代码提供，避免 shadcn CLI 因 label.tsx 已存在进入交互。
field_source = textwrap.dedent(
    """\
    import * as React from 'react'
    import { cn } from '@/lib/utils'
    import { Label } from '@/components/ui/label'

    function FieldSet({ className, ...props }: React.ComponentProps<'fieldset'>) {
      return (
        <fieldset
          data-slot='field-set'
          className={cn('flex flex-col gap-4', className)}
          {...props}
        />
      )
    }

    function FieldLegend({ className, ...props }: React.ComponentProps<'legend'>) {
      return (
        <legend
          data-slot='field-legend'
          className={cn('mb-2 font-medium', className)}
          {...props}
        />
      )
    }

    function FieldGroup({ className, ...props }: React.ComponentProps<'div'>) {
      return (
        <div
          data-slot='field-group'
          className={cn('flex flex-col gap-4', className)}
          {...props}
        />
      )
    }

    function Field({
      className,
      orientation = 'vertical',
      ...props
    }: React.ComponentProps<'div'> & {
      orientation?: 'vertical' | 'horizontal' | 'responsive'
    }) {
      return (
        <div
          data-slot='field'
          data-orientation={orientation}
          className={cn(
            'group/field flex gap-2 data-[orientation=vertical]:flex-col data-[orientation=horizontal]:items-center',
            className
          )}
          {...props}
        />
      )
    }

    function FieldLabel({
      className,
      ...props
    }: React.ComponentProps<typeof Label>) {
      return (
        <Label
          data-slot='field-label'
          className={cn('font-medium', className)}
          {...props}
        />
      )
    }

    function FieldDescription({
      className,
      ...props
    }: React.ComponentProps<'p'>) {
      return (
        <p
          data-slot='field-description'
          className={cn('text-sm text-muted-foreground', className)}
          {...props}
        />
      )
    }

    function FieldError({ className, ...props }: React.ComponentProps<'p'>) {
      return (
        <p
          data-slot='field-error'
          role='alert'
          className={cn('text-sm text-destructive', className)}
          {...props}
        />
      )
    }

    export {
      Field,
      FieldDescription,
      FieldError,
      FieldGroup,
      FieldLabel,
      FieldLegend,
      FieldSet,
    }
    """
)
field_patch = package / 'patch/web/portal/src/components/ui/field.tsx'
field_patch.parent.mkdir(parents=True, exist_ok=True)
field_patch.write_text(field_source, encoding='utf-8')

# 在最终 UI 守卫前处理模板遗留组件。
ui_fix_source = textwrap.dedent(
    '''\
    from pathlib import Path


    def read(path: str) -> tuple[Path, str]:
        target = Path(path)
        return target, target.read_text(encoding='utf-8')


    def write(target: Path, content: str) -> None:
        target.write_text(content, encoding='utf-8')


    account, content = read(
        'web/portal/src/pages/settings/account/account-form.tsx'
    )
    content = content.replace(
        "import { CaretSortIcon, CheckIcon } from '@radix-ui/react-icons'",
        "import { Check, ChevronsUpDown } from 'lucide-react'",
    )
    content = content.replace('<CaretSortIcon', '<ChevronsUpDown')
    content = content.replace('<CheckIcon', '<Check')
    write(account, content)

    appearance, content = read(
        'web/portal/src/pages/settings/appearance/appearance-form.tsx'
    )
    content = content.replace(
        "import { ChevronDownIcon } from '@radix-ui/react-icons'\n",
        '',
    )
    content = content.replace("import { cn } from '@/lib/utils'\n", '')
    content = content.replace(
        "import { Button, buttonVariants } from '@/components/ui/button'",
        "import { Button } from '@/components/ui/button'",
    )
    radio_import = (
        "import { RadioGroup, RadioGroupItem } "
        "from '@/components/ui/radio-group'\n"
    )
    select_import = """import {
      Select,
      SelectContent,
      SelectItem,
      SelectTrigger,
      SelectValue,
    } from '@/components/ui/select'
    """
    if radio_import not in content:
        raise SystemExit('appearance radio import marker not found')
    content = content.replace(radio_import, radio_import + select_import)
    start_marker = "              <div className='relative w-max'>"
    end_marker = "              <FormDescription className='font-manrope'>"
    start = content.index(start_marker)
    end = content.index(end_marker, start)
    select_block = """              <Select
                value={field.value}
                onValueChange={field.onChange}
              >
                <FormControl>
                  <SelectTrigger className='w-50 capitalize'>
                    <SelectValue placeholder='Select font' />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {fonts.map((font) => (
                    <SelectItem key={font} value={font} className='capitalize'>
                      {font}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
    """
    content = content[:start] + select_block + content[end:]
    write(appearance, content)

    export_dialog, content = read(
        'web/portal/src/components/work-records/list/work-record-export-dialog.tsx'
    )
    content = content.replace(
        "import { Button } from '@/components/ui/button'\n",
        "import { Button } from '@/components/ui/button'\n"
        "import { Checkbox } from '@/components/ui/checkbox'\n",
    )
    content = content.replace(
        "import { downloadExport, exportWorkRecords } from './export-api'",
        "import { downloadExport, exportWorkRecords } "
        "from '@/lib/work-records/list/export-api'",
    )
    content = content.replace(
        """                <input
                  type='checkbox'
                  checked={selectedKeys.includes(column.key)}
                  onChange={() => toggleColumn(column.key)}
                />""",
        """                <Checkbox
                  checked={selectedKeys.includes(column.key)}
                  onCheckedChange={() => toggleColumn(column.key)}
                />""",
    )
    content = content.replace(
        """            <input
              type='checkbox'
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />""",
        """            <Checkbox
              checked={confirmed}
              onCheckedChange={(checked) => setConfirmed(checked === true)}
            />""",
    )
    write(export_dialog, content)

    Path(
        'web/portal/src/components/platform/iam/platform-user-table.tsx'
    ).unlink(missing_ok=True)
    '''
)
ui_fix = package / 'scripts/fix-remaining-ui.py'
ui_fix.write_text(ui_fix_source, encoding='utf-8')

phase7 = package / 'scripts/phase21-07-cleanup.sh'
replace_required(
    phase7,
    'rm -rf web/portal/src/features\n',
    'rm -rf web/portal/src/features\n'
    'copy_patch "$PACKAGE_ROOT" web/portal/src/components/ui/field.tsx\n'
    'python3 "$PACKAGE_ROOT/scripts/fix-remaining-ui.py"\n',
    'phase21-07 deterministic UI fixes',
)
replace_required(
    phase7,
    'for component in field empty spinner toggle-group; do',
    'for component in empty spinner toggle-group; do',
    'phase21-07 non-interactive shadcn install',
)

print(f'normalized Phase 21 package: {package}')

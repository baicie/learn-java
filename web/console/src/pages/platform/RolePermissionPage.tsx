import { ShieldIcon } from 'lucide-react'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export function RolePermissionPage() {
  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold">角色权限</h1>
        <p className="text-sm text-muted-foreground">Phase 1 建立入口，后续补充角色授权交互。</p>
      </header>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { code: 'admin', name: '管理员', desc: '完整系统访问权限' },
          { code: 'operator', name: '运维人员', desc: '告警处理、故障管理权限' },
          { code: 'viewer', name: '只读用户', desc: '查看告警、故障和分析报告' },
        ].map((role) => (
          <Card key={role.code}>
            <CardHeader className="flex flex-row items-center gap-3 pb-2">
              <ShieldIcon className="text-muted-foreground" />
              <CardTitle className="text-base">{role.name}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">{role.desc}</p>
              <p className="mt-2 text-xs text-muted-foreground">代码: {role.code}</p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}

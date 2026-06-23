import { Link } from 'react-router-dom'

import { Card, CardContent } from '@/components/ui/card'

export function EvidencePage() {
  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
          <div>
            <h1 className="text-base font-semibold leading-none">Evidence</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              Evidence 按 Incident 组织，请先进入 Incident Detail 查看。
            </p>
          </div>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-6 py-6">
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <p className="text-center font-medium">请选择一个 Incident</p>
            <p className="mt-2 text-center text-sm text-muted-foreground">
              进入 Incident Detail 的 Evidence Tab，可查看 CPU、接口响应、健康检查和日志证据。
            </p>
            <Link
              to="/incidents"
              className="mt-4 inline-flex h-7 items-center justify-center gap-1 rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium hover:bg-muted hover:text-foreground"
            >
              打开 Incidents
            </Link>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}

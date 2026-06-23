import { Link } from 'react-router-dom'

import { Card, CardContent } from '@/components/ui/card'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-muted/30">
      <Card className="w-full max-w-md">
        <CardContent className="flex flex-col items-center py-12">
          <h1 className="text-4xl font-bold">404</h1>
          <p className="mt-2 text-muted-foreground">页面不存在</p>
          <Link
            to="/"
            className="mt-4 inline-flex h-7 items-center justify-center gap-1 rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium hover:bg-muted hover:text-foreground"
          >
            返回首页
          </Link>
        </CardContent>
      </Card>
    </div>
  )
}

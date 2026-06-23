import ReactMarkdown from 'react-markdown'

export function MarkdownPreview({ markdown }: { markdown?: string }) {
  if (!markdown) {
    return (
      <div className="rounded-lg border border-border bg-muted/30 px-4 py-8 text-center text-sm text-muted-foreground">
        暂无 Markdown 报告。
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-border bg-muted/30 p-4" data-testid="markdown-preview">
      <article className="prose prose-sm max-w-none">
        <ReactMarkdown>{markdown}</ReactMarkdown>
      </article>
    </div>
  )
}

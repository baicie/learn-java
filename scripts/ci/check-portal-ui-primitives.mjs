import { readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'

const root = new URL('../../web/portal/src/', import.meta.url)
const repo = new URL('../../', import.meta.url).pathname
const violations = []
const nativeControl = /<(button|input|select|textarea|table)(?:\s|>)/g
const radixImport = /from\s+['"]@radix-ui\//

for (const file of sourceFiles(root)) {
  const path = relative(repo, file)
  const businessSource =
    path.startsWith('web/portal/src/pages/') ||
    path.startsWith('web/portal/src/auth/') ||
    path.startsWith('web/portal/src/components/iam/') ||
    path.startsWith('web/portal/src/components/dictionaries/') ||
    path.startsWith('web/portal/src/components/work-records/')
  if (!businessSource || /\.(test|spec)\.tsx?$/.test(path)) continue

  const content = readFileSync(file, 'utf8')
  const controls = [...content.matchAll(nativeControl)].map((match) => match[1])
  if (controls.length) violations.push(`${path}: 原生 ${[...new Set(controls)].join(', ')}`)
  if (radixImport.test(content)) violations.push(`${path}: 直接引用 @radix-ui/*`)
}

if (violations.length) {
  console.error(`Portal UI 原语守卫失败:\n- ${violations.join('\n- ')}`)
  process.exit(1)
}

console.log('Portal UI 原语守卫通过')

function* sourceFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory.pathname, entry.name)
    if (entry.isDirectory()) yield* sourceFiles(new URL(`${entry.name}/`, directory))
    else if (['.ts', '.tsx'].includes(extname(entry.name))) yield path
  }
}

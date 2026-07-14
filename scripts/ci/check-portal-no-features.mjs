import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'

const root = new URL('../../web/portal/src/', import.meta.url)
const features = new URL('features/', root)
const violations = []

if (existsSync(features)) violations.push('web/portal/src/features 目录仍然存在')

for (const file of sourceFiles(root)) {
  const content = readFileSync(file, 'utf8')
  if (content.includes('@/features/') || content.includes('/src/features/')) {
    violations.push(relative(new URL('../../', import.meta.url).pathname, file))
  }
}

if (violations.length) {
  console.error(`Portal features 守卫失败:\n- ${violations.join('\n- ')}`)
  process.exit(1)
}

console.log('Portal features 守卫通过')

function* sourceFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory.pathname, entry.name)
    if (entry.isDirectory()) yield* sourceFiles(new URL(`${entry.name}/`, directory))
    else if (['.ts', '.tsx'].includes(extname(entry.name))) yield path
  }
}

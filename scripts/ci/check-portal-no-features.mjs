import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = fileURLToPath(new URL('../../', import.meta.url))
const root = fileURLToPath(new URL('../../web/portal/src/', import.meta.url))
const features = join(root, 'features')
const violations = []

if (existsSync(features)) violations.push('web/portal/src/features 目录仍然存在')

for (const file of sourceFiles(root)) {
  const content = readFileSync(file, 'utf8')
  if (content.includes('@/features/') || content.includes('/src/features/')) {
    violations.push(relative(repoRoot, file))
  }
}

if (violations.length) {
  console.error(`Portal features 守卫失败:\n- ${violations.join('\n- ')}`)
  process.exit(1)
}

console.log('Portal features 守卫通过')

function* sourceFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) yield* sourceFiles(path)
    else if (['.ts', '.tsx'].includes(extname(entry.name))) yield path
  }
}

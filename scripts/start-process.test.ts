import assert from 'node:assert/strict'
import { test } from 'node:test'
import { join } from 'node:path'

import {
  isManagedAppProcess,
  parseJavaProcessList,
  parseJavaSystemProperties,
} from './start-process.ts'

test('parseJavaProcessList extracts Java launch commands by PID', () => {
  const processes = parseJavaProcessList(`
16068 apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar
31492 jdk.jcmd/sun.tools.jcmd.JCmd -l
`)

  assert.equal(
    processes.get(16068),
    'apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar'
  )
  assert.equal(processes.size, 2)
})

test('parseJavaSystemProperties decodes escaped Windows paths', () => {
  const properties = parseJavaSystemProperties(`
sun.java.command=apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar
user.dir=D\\:\\\\workspace\\\\git-code\\\\ai-ops
`)

  assert.equal(properties.get('user.dir'), 'D:\\workspace\\git-code\\ai-ops')
})

test('isManagedAppProcess accepts only the expected jar in this workspace', () => {
  const root = 'D:\\workspace\\git-code\\ai-ops'
  const jar = join(
    root,
    'apps',
    'aiops-server',
    'target',
    'aiops-server-0.1.0-SNAPSHOT.jar'
  )
  const command =
    'apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar --server.port=8080'

  assert.equal(isManagedAppProcess(command, root, jar), true)
  assert.equal(
    isManagedAppProcess(command, 'D:\\workspace\\other-clone', jar),
    false
  )
  assert.equal(
    isManagedAppProcess('another-service.jar --server.port=8080', root, jar),
    false
  )
})

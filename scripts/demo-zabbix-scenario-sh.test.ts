import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const script = readFileSync(
  join(repoRoot, 'scripts/demo-zabbix-scenario.sh'),
  'utf8'
)

test('bash demo gives all four webhook events a per-run identity', () => {
  assert.match(
    script,
    /RUN_ID="\$\(date -u \+"%Y%m%dT%H%M%S"\)-\$\$-\$\{RANDOM\}\$\{RANDOM\}"/
  )
  assert.match(script, /^EVENT_IDS=\(/m)
  for (const suffix of ['20001', '20002', '20003', '20004']) {
    assert.match(script, new RegExp(`"z9-\\$\\{RUN_ID\\}-${suffix}"`))
  }
  assert.match(script, /webhook "\$\{EVENT_IDS\[0\]\}"/)
  assert.match(script, /webhook "\$\{EVENT_IDS\[1\]\}"/)
  assert.match(script, /webhook "\$\{EVENT_IDS\[2\]\}"/)
  assert.match(script, /webhook "\$\{EVENT_IDS\[3\]\}"/)
  assert.doesNotMatch(script, /webhook "2000[1-4]"/)
})

test('bash demo selects only an incident containing every injected event', () => {
  assert.doesNotMatch(script, /latest_incident_id/)
  assert.match(script, /\/api\/incidents\/\$\{incident_id\}\/alerts/)
  assert.match(script, /EXPECTED_SOURCE_EVENT_IDS=\(/)
  assert.match(script, /all\(\$expected\[\];/)
  assert.match(
    script,
    /incident_id_for_source_events "\$\{EXPECTED_SOURCE_EVENT_IDS\[@\]\}"/
  )
  assert.match(script, /No incident contains all injected Zabbix events/)
})

test('bash demo fixes one event timestamp for the whole run', () => {
  assert.match(script, /^EVENT_STARTED_AT=""$/m)
  assert.match(script, /EVENT_STARTED_AT="\$\(date -u/)
  assert.match(script, /local ts="\$\{EVENT_STARTED_AT\}"/)
})

test('bash demo uses bounded HTTP calls and a private temporary directory', () => {
  assert.match(script, /--connect-timeout/)
  assert.match(script, /--max-time/)
  assert.match(script, /mktemp -d/)
  assert.match(script, /trap cleanup EXIT/)
  assert.doesNotMatch(script, /\/tmp\/aiops-/)
})

test('bash demo validates every downstream response before succeeding', () => {
  assert.match(script, /evidenceCreated/)
  assert.match(script, /evidenceUpdated/)
  assert.match(script, /RCA analysis returned no root cause or rules/)
  assert.match(script, /AI diagnosis returned incomplete content/)
  assert.match(script, /Report markdown is missing required sections/)
})

test('bash demo matches an existing Zabbix datasource by endpoint', () => {
  assert.match(script, /--arg endpoint "\$\{ZABBIX_ENDPOINT\}"/)
  assert.match(
    script,
    /select\(\.type == "zabbix" and \.endpoint == \$endpoint\)/
  )
})

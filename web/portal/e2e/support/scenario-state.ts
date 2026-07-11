import fs from 'node:fs'
import path from 'node:path'

export type EnterpriseScenarioState = {
  adminToken: string
  userAToken: string
  userBToken: string

  oldRecordId: string
  newRecordId: string
  otherRecordId: string

  oldTitle: string
  newTitle: string
  otherTitle: string
}

const STATE_FILE = path.resolve(
  process.cwd(),
  '.playwright/enterprise-state.json'
)

export function writeScenarioState(state: EnterpriseScenarioState) {
  fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true })

  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2), 'utf8')
}

export function readScenarioState(): EnterpriseScenarioState {
  return JSON.parse(
    fs.readFileSync(STATE_FILE, 'utf8')
  ) as EnterpriseScenarioState
}

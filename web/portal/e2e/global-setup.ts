import type { FullConfig } from '@playwright/test'
import { seedEnterpriseScenario } from './support/enterprise-scenario'
import { writeScenarioState } from './support/scenario-state'

export default async function globalSetup(_config: FullConfig) {
  const state = await seedEnterpriseScenario()
  writeScenarioState(state)
}

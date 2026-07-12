import {
  expect,
  test as base,
} from '@playwright/test'
import {
  seedEnterpriseScenario,
  type EnterpriseScenarioState,
} from '../support/enterprise-scenario'

export const test = base.extend<
  Record<string, never>,
  {
    scenario: EnterpriseScenarioState
  }
>({
  scenario: [
    async ({ _: _fixture }, use) => {
      const scenario = await seedEnterpriseScenario()
      await use(scenario)
    },
    {
      scope: 'worker',
    },
  ],
})

export { expect }

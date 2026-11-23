import { expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

export async function expectAutomaticallyAccessible(page: Page, screen: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const violations = results.violations.map(({ id, impact, help, nodes }) => ({
    id,
    impact,
    help,
    elements: nodes.map(({ target, failureSummary }) => ({ target, failureSummary })),
  }))
  expect(violations, `${screen} accessibility findings`).toEqual([])
}

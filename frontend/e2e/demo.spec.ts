import { expect, test } from '@playwright/test'

test('guided demo creates, exactly replays, processes and records history', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  await page.goto('/overview')
  const identity = await page.evaluate(async () => (await fetch('/api/v1/auth/me')).json()) as { company: { demo: boolean } }
  expect(identity.company.demo).toBe(true)
  await page.getByRole('link', { name: 'Run guided demo' }).click()
  await expect(page.getByRole('heading', { name: 'Prove a payment replay' })).toBeVisible()
  await page.getByLabel('Customer').selectOption({ index: 1 })
  await page.getByLabel('Amount (BRL)').fill('41.27')
  await page.getByLabel('Description').fill('R19 approval journey')
  await page.getByRole('button', { name: 'Create simulated payment' }).click()
  await expect(page.getByRole('heading', { name: 'Repeat the exact request' })).toBeVisible()
  const id = (await page.locator('.guided-payment-summary strong').innerText()).trim()
  await page.getByRole('button', { name: 'Replay exact request' }).click()
  await expect(page.getByText('Replay confirmed by the API')).toBeVisible()
  await expect(page.locator('.guided-replay-result')).toContainText('true')
  await expect(page.locator('.guided-replay-result')).toContainText(id)
  await page.getByRole('button', { name: 'Process payment' }).click()
  await expect(page.getByRole('heading', { name: 'Server-confirmed outcome' })).toBeVisible()
  await expect(page.locator('.guided-final-payment')).toContainText('Approved')
  await expect(page.getByRole('list', { name: 'Payment history' })).toContainText('Status changed')
  expect(errors).toEqual([])
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Restart the guided demo' })).toBeVisible()
})

test('a second real browser context receives a distinct demo company and seeded customers', async ({ browser, baseURL }) => {
  const contexts = await Promise.all([browser.newContext({ baseURL }), browser.newContext({ baseURL })])
  try {
    const identities = []
    const customerIds = []
    for (const context of contexts) {
      const page = await context.newPage()
      await page.goto('/login')
      await page.getByRole('button', { name: /explore demo workspace/i }).click()
      await expect(page).toHaveURL(/\/payments$/)
      identities.push(await page.evaluate(async () => (await fetch('/api/v1/auth/me')).json()) as { company: { id: string } })
      customerIds.push(await page.evaluate(async () => {
        const page = await fetch('/api/v1/customers?page=0&size=20').then(response => response.json()) as { items: Array<{ id: string }> }
        return page.items[0].id
      }))
    }
    expect(identities[0].company.id).not.toBe(identities[1].company.id)
    expect(customerIds[0]).not.toBe(customerIds[1])
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('demo entry proxy returns 429 after its immediate burst is exhausted', async ({ browser, baseURL }) => {
  test.skip(!process.env.FLOWPAY_TEST_RATE_LIMIT, 'Requires isolated Compose with the default 2r/m limiter and a clean window')
  const contexts = await Promise.all([browser.newContext({ baseURL }), browser.newContext({ baseURL }), browser.newContext({ baseURL })])
  try {
    for (const context of contexts) {
      const page = await context.newPage()
      await page.goto('/login')
      await page.getByRole('button', { name: /explore demo workspace/i }).click()
      await expect(page).toHaveURL(/\/payments$/)
    }
    const status = await contexts[0].pages()[0].evaluate(async () => {
      const csrf = await fetch('/api/v1/auth/csrf').then(response => response.json()) as { token: string; headerName: string }
      const response = await fetch('/api/v1/demo/sessions', { method: 'POST', headers: { [csrf.headerName]: csrf.token } })
      return response.status
    })
    expect(status).toBe(429)
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

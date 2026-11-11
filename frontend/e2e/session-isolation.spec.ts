import { expect, test } from '@playwright/test'

test('separate sessions cannot read each other customer or payment records', async ({ browser, baseURL }) => {
  const contexts = await Promise.all([browser.newContext({ baseURL }), browser.newContext({ baseURL })])
  const data: Array<{ customerId: string; paymentId: string }> = []
  try {
    for (let index = 0; index < contexts.length; index++) {
      const page = await contexts[index].newPage()
      await page.goto('/login')
      await page.getByRole('button', { name: /explore demo workspace/i }).click()
      await expect(page).toHaveURL(/\/payments$/)
      await page.goto('/customers')
      await page.getByRole('button', { name: 'Add customer' }).click()
      const name = `Isolated R19 ${index} ${Date.now()}`
      await page.getByLabel('Customer name').fill(name)
      await page.getByLabel('Email address').fill(`isolated-${index}-${Date.now()}@example.test`)
      await page.getByRole('button', { name: 'Save customer' }).click()
      await expect(page.getByRole('status')).toContainText('ready to use')
      const customerId = await page.getByRole('table').locator('tbody tr').filter({ hasText: name }).locator('small').innerText()
      const payment = await page.evaluate(async customerId => {
        const csrf = await fetch('/api/v1/auth/csrf').then(response => response.json()) as { token: string; headerName: string }
        const response = await fetch('/api/v1/payments', { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ customerId, amount: '12.34', currency: 'BRL', simulationScenario: 'APPROVE', description: 'R19 isolation probe' }) })
        if (!response.ok) throw new Error(`Payment creation failed: ${response.status}`)
        return response.json() as Promise<{ id: string }>
      }, customerId)
      data.push({ customerId, paymentId: payment.id })
    }
    expect(data[0].customerId).not.toBe(data[1].customerId)
    expect(data[0].paymentId).not.toBe(data[1].paymentId)
    for (let index = 0; index < contexts.length; index++) {
      const foreign = data[1 - index]
      const statuses = await contexts[index].pages()[0].evaluate(async ids => {
        const responses = await Promise.all([fetch(`/api/v1/customers/${ids.customerId}`), fetch(`/api/v1/payments/${ids.paymentId}`)])
        return responses.map(response => response.status)
      }, foreign)
      expect(statuses).toEqual([404, 404])
    }
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('logout invalidates the session and protected navigation requires a new sign in', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(async () => (await fetch('/api/v1/auth/me')).status)).toBe(401)
  await page.goto('/customers')
  await expect(page).toHaveURL(/\/login$/)
})

test('a server-expired session during a payment form shows expiry and submits no payment', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  await page.getByRole('button', { name: 'Create payment' }).click()
  await page.getByLabel('Customer', { exact: true }).selectOption({ index: 1 })
  await page.getByLabel('Amount (BRL)').fill('22.22')
  await page.getByLabel('Description').fill('Must not submit after session expiry')

  const logoutStatus = await page.evaluate(async () => {
    const csrf = await fetch('/api/v1/auth/csrf').then(response => response.json()) as { token: string; headerName: string }
    return fetch('/api/v1/auth/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } }).then(response => response.status)
  })
  expect(logoutStatus).toBe(204)

  const createResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/payments') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Create payment' }).last().click()
  expect((await createResponse).status()).toBe(401)
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status')).toContainText(/session has expired/i)
})

test('a read held across logout and new demo entry cannot restore the previous company data', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  const oldIdentity = await page.evaluate(async () => (await fetch('/api/v1/auth/me')).json()) as { company: { id: string } }
  const oldPaymentIds = await page.evaluate(async () => (await fetch('/api/v1/payments?page=0&size=20').then(response => response.json()) as { items: Array<{ id: string }> }).items.map(payment => payment.id))

  let releaseRead: (() => void) | undefined
  let readStarted: (() => void) | undefined
  const started = new Promise<void>(resolve => { readStarted = resolve })
  const held = new Promise<void>(resolve => { releaseRead = resolve })
  let holdOnce = true
  await page.route('**/api/v1/payments*', async route => {
    if (holdOnce && route.request().method() === 'GET') {
      holdOnce = false
      readStarted?.()
      await held
    }
    await route.continue()
  })
  await page.goto('/payments?q=held-across-logout')
  await started
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments(?:\?.*)?$/)
  const newIdentity = await page.evaluate(async () => (await fetch('/api/v1/auth/me')).json()) as { company: { id: string } }
  expect(newIdentity.company.id).not.toBe(oldIdentity.company.id)
  releaseRead?.()
  await page.goto('/payments')
  await expect(page.getByRole('heading', { name: 'Payments', exact: true })).toBeVisible()
  const newPaymentIds = await page.evaluate(async () => (await fetch('/api/v1/payments?page=0&size=20').then(response => response.json()) as { items: Array<{ id: string }> }).items.map(payment => payment.id))
  expect(newPaymentIds.some(id => oldPaymentIds.includes(id))).toBe(false)
  expect(await page.evaluate(async () => (await fetch('/api/v1/auth/me')).json().then(identity => identity.company.id))).toBe(newIdentity.company.id)
  await page.unrouteAll({ behavior: 'wait' })
})

test('payment list recovers from a backend 503 using a real retry', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  let failuresRemaining = 4
  await page.route(/\/api\/v1\/payments\?/, async route => {
    if (failuresRemaining > 0 && route.request().method() === 'GET') {
      failuresRemaining--
      await route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503, code: 'SERVICE_UNAVAILABLE', detail: 'Temporary acceptance failure', requestId: 'r19-outage-probe' }) })
    } else await route.continue()
  })
  const outageResponse = page.waitForResponse(response => response.url().includes('/api/v1/payments?') && response.request().method() === 'GET')
  await page.goto('/payments?q=backend-outage-recovery')
  expect((await outageResponse).status()).toBe(503)
  await expect(page.getByRole('alert')).toContainText('Payments could not be loaded.')
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('heading', { name: 'No payments match these filters' })).toBeVisible()
})

import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

async function enterDemo(page: Page) {
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
}

async function createPayment(page: Page, description: string, scenario: 'approval' | 'decline') {
  await page.goto('/payments')
  await page.getByRole('button', { name: 'Create payment' }).click()
  await expect(page.getByLabel('Customer', { exact: true })).toBeEnabled()
  await page.getByLabel('Customer', { exact: true }).selectOption({ index: 1 })
  await page.getByLabel('Amount (BRL)').fill('19.85')
  await page.getByLabel('Description').fill(description)
  await page.getByLabel(scenario === 'approval' ? 'Simulate approval' : 'Simulate decline').check()
  const responsePromise = page.waitForResponse(response => response.url().endsWith('/api/v1/payments') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Create payment' }).last().click()
  const response = await responsePromise
  await expect(page.getByRole('status')).toContainText(/created as Pending/i)
  return (await response.json() as { id: string }).id
}

test('creates customer, approves payment, filters it and opens direct/reloaded details', async ({ page }) => {
  await enterDemo(page)
  await page.goto('/customers')
  await page.getByRole('button', { name: 'Add customer' }).click()
  const name = `R19 Customer ${Date.now()}`
  await page.getByLabel('Customer name').fill(name)
  await page.getByLabel('Email address').fill(`r19-${Date.now()}@example.test`)
  await page.getByRole('button', { name: 'Save customer' }).click()
  await expect(page.getByRole('status')).toContainText('ready to use')
  await expect(page.getByRole('table')).toContainText(name)
  const paymentId = await createPayment(page, 'R19 searchable approval', 'approval')
  await page.getByLabel('Filter by status').selectOption('PENDING')
  await expect(page).toHaveURL(/status=PENDING/)
  await expect(page.getByRole('table')).toContainText('Pending')
  expect(paymentId).toMatch(/^[0-9a-f-]{36}$/i)
  await page.goto(`/payments/${paymentId}`)
  await expect(page.getByRole('heading', { name: 'R19 searchable approval' })).toBeVisible()
  await page.getByRole('button', { name: 'Process payment' }).click()
  await expect(page.locator('.payment-details-page')).toContainText('Approved')
  await page.reload()
  await expect(page.getByRole('heading', { name: 'R19 searchable approval' })).toBeVisible()
  await expect(page.locator('.payment-details-page')).toContainText('Approved')
})

test('declines and cancels separate pending payments, recording final history', async ({ page }) => {
  await enterDemo(page)
  const declineId = await createPayment(page, 'R19 decline case', 'decline')
  await page.goto(`/payments/${declineId}`)
  await page.getByRole('button', { name: 'Process payment' }).click()
  await expect(page.locator('.payment-details-page')).toContainText('Rejected')
  await expect(page.getByRole('list', { name: 'Payment history' })).toContainText('Reason: simulated decline.')
  await page.goto('/payments')
  const cancelId = await createPayment(page, 'R19 cancellation case', 'approval')
  await page.goto(`/payments/${cancelId}`)
  await page.getByRole('button', { name: 'Cancel payment' }).click()
  await expect(page.locator('.payment-details-page')).toContainText('Cancelled')
  await expect(page.getByRole('list', { name: 'Payment history' })).toContainText('Cancelled')
})

test('serves Swagger UI assets and OpenAPI YAML through the same-origin proxy', async ({ page, request, baseURL }) => {
  const yaml = await request.get(`${baseURL}/api/openapi.yaml`)
  expect(yaml.status()).toBe(200)
  expect(yaml.headers()['content-type']).toContain('yaml')
  expect(await yaml.text()).toContain('/api/v1/payments')
  await page.goto('/api/docs')
  await expect(page).toHaveTitle(/FlowPay API documentation/i)
  await expect(page.locator('#swagger-ui .swagger-ui')).toBeVisible()
})

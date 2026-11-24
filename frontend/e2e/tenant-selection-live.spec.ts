import { expect, test } from '@playwright/test'
import { expectAutomaticallyAccessible } from './accessibility'

const demoPassword = process.env.FLOWPAY_E2E_DEMO_PASSWORD
const demoTenantId = 'db702708-27ec-47d1-8e06-1f9587feafcb'
const readerTenantId = '5817d87b-ae9b-4cdf-a4c7-2a6bd8d66745'

if (!demoPassword) {
  throw new Error('Set FLOWPAY_E2E_DEMO_PASSWORD for the isolated local demo environment.')
}

async function tabUntilFocused(page: import('@playwright/test').Page, locator: import('@playwright/test').Locator) {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    if (await locator.evaluate((element) => element === document.activeElement)) return
    await page.keyboard.press('Tab')
  }
  throw new Error('The control was not reachable with Tab.')
}

test('demo member switches between real companies without leaking balances', async ({ page }) => {
  test.skip(process.env.FLOWPAY_DEMO_MULTI_TENANT_E2E !== 'true', 'Requires the opt-in synthetic reader membership.')
  await page.addInitScript(() => {
    if (window.sessionStorage.getItem('flowpay.e2e.clearedTenant') !== 'true') {
      window.localStorage.removeItem('flowpay.activeTenantId')
      window.sessionStorage.setItem('flowpay.e2e.clearedTenant', 'true')
    }
  })
  await page.goto('/')
  const switchToLogin = page.getByRole('button', { name: 'Entrar', exact: true })
  await tabUntilFocused(page, switchToLogin)
  await switchToLogin.press('Enter')
  await expect(page.getByRole('heading', { name: 'Acesse sua conta' })).toBeVisible()
  await page.getByLabel('E-mail').fill('demo@flowpay.local')
  await page.getByLabel('Senha').fill(demoPassword!)
  await page.getByLabel('Senha').press('Enter')

  await expect(page.getByRole('heading', { name: 'Escolha uma empresa' })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'real multi-company selection')

  const demoDashboardResponse = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/dashboard'
      && response.request().headers()['x-flowpay-tenant'] === demoTenantId
  })
  await page.getByRole('button', { name: /FlowPay Demonstração/ }).click()
  const demoDashboard = await (await demoDashboardResponse).json() as { controlledBalanceMinor: string }
  expect(demoDashboard.controlledBalanceMinor).toBe('2500000')
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()
  await expect(page.getByText('R$ 25.000,00', { exact: true })).toBeVisible()

  const companySelector = page.getByLabel('Empresa ativa')
  const readerDashboardResponse = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/dashboard'
      && response.request().headers()['x-flowpay-tenant'] === readerTenantId
  })
  await companySelector.selectOption(readerTenantId)
  const readerDashboard = await (await readerDashboardResponse).json() as { controlledBalanceMinor: string }
  expect(readerDashboard.controlledBalanceMinor).toBe('0')
  await expect(page.getByText('Acesso de leitura', { exact: true })).toBeVisible()
  await expect(page.locator('main')).toHaveAttribute('data-read-only', 'true')
  await expect(page.getByRole('article').filter({ hasText: 'Saldo controlado' }).getByText('R$ 0,00', { exact: true })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'real read-only company workspace')

  const restoredDemoDashboard = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/dashboard'
      && response.request().headers()['x-flowpay-tenant'] === demoTenantId
  })
  await companySelector.selectOption(demoTenantId)
  expect((await (await restoredDemoDashboard).json()).controlledBalanceMinor).toBe('2500000')
  await expect(page.getByText('R$ 25.000,00', { exact: true })).toBeVisible()
})

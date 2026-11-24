import { expect, test } from '@playwright/test'
import { expectAutomaticallyAccessible } from './accessibility'

const tenantOne = '11111111-1111-4111-8111-111111111111'
const tenantTwo = '22222222-2222-4222-8222-222222222222'

test('member selects and switches companies with server-validated tenant context', async ({ page }) => {
  const dashboardRequests: Array<{ tenant: string | undefined }> = []
  const businessRequests: Array<{ path: string; tenant: string | undefined }> = []
  await page.addInitScript(() => {
    if (!window.sessionStorage.getItem('flowpay.test.initialized')) {
      window.localStorage.removeItem('flowpay.activeTenantId')
      window.sessionStorage.setItem('flowpay.test.initialized', 'true')
    }
  })
  await page.route('**/api/**', async (route) => {
    const requestUrl = new URL(route.request().url())
    if (requestUrl.pathname === '/api/auth/config') {
      await route.fulfill({ json: { registrationEnabled: false } })
      return
    }
    if (requestUrl.pathname === '/api/auth/csrf') {
      await route.fulfill({ json: { token: 'csrf-test-token' } })
      return
    }
    if (requestUrl.pathname === '/api/auth/session') {
      await route.fulfill({ json: {
        email: 'member@example.test',
        tenantId: tenantOne,
        companyName: 'Alpha Serviços',
        role: 'MANAGER',
        tenants: [
          { id: tenantOne, name: 'Alpha Serviços', role: 'MANAGER' },
          { id: tenantTwo, name: 'Beta Serviços', role: 'READER' },
        ],
      } })
      return
    }
    businessRequests.push({ path: requestUrl.pathname, tenant: route.request().headers()['x-flowpay-tenant'] })
    if (requestUrl.pathname === '/api/dashboard') {
      dashboardRequests.push({ tenant: route.request().headers()['x-flowpay-tenant'] })
      await route.fulfill({ json: {
        from: '2026-11-01',
        to: '2026-11-30',
        timezone: 'America/Sao_Paulo',
        asOf: '2026-11-24',
        controlledBalanceMinor: '10000',
        periodInflowsMinor: '0',
        periodOutflowsMinor: '0',
        receivablesDueMinor: '0',
        payablesDueMinor: '0',
        overdueReceivablesMinor: '0',
        overduePayablesMinor: '0',
        upcomingDues: [],
        recentMovements: [],
      } })
      return
    }
    await route.fulfill({ status: 500, json: { message: `Unexpected API request: ${requestUrl.pathname}` } })
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Escolha uma empresa' })).toBeVisible()
  expect(businessRequests).toHaveLength(0)
  await expectAutomaticallyAccessible(page, 'company selection')

  await page.getByRole('button', { name: /Alpha Serviços/ }).click()
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()
  const companySelector = page.getByLabel('Empresa ativa')
  await expect(companySelector).toHaveValue(tenantOne)
  await expect.poll(() => dashboardRequests.some((request) => request.tenant === tenantOne)).toBe(true)
  expect(businessRequests.every((request) => request.tenant === tenantOne)).toBe(true)

  await companySelector.selectOption(tenantTwo)
  await expect(page.getByLabel('Empresa ativa')).toHaveValue(tenantTwo)
  await expect(page.getByText('Acesso de leitura', { exact: true })).toBeVisible()
  await expect(page.locator('main')).toHaveAttribute('data-read-only', 'true')
  await expect.poll(() => dashboardRequests.some((request) => request.tenant === tenantTwo)).toBe(true)
  expect(businessRequests.every((request) => request.tenant === tenantOne || request.tenant === tenantTwo)).toBe(true)
  await expectAutomaticallyAccessible(page, 'read-only company workspace')

  await page.getByRole('button', { name: 'Movimentos de caixa', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Movimentos de caixa' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ajustar saldo', exact: true })).toBeHidden()
})

import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { expectAutomaticallyAccessible } from './accessibility'

const tenantId = '8efbba1a-9139-4b5c-bc44-164b3c045c45'

test('workspace skip link and responsive navigation work with keyboard', async ({ page }) => {
  let authenticated = false
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())

    if (url.pathname === '/api/auth/config') {
      await route.fulfill({ json: { registrationEnabled: false } })
    } else if (url.pathname === '/api/auth/csrf') {
      await route.fulfill({ status: 200, headers: { 'Set-Cookie': 'XSRF-TOKEN=ui-test-token; Path=/; SameSite=Lax' } })
    } else if (url.pathname === '/api/auth/login' && request.method() === 'POST') {
      authenticated = true
      await route.fulfill({ json: { email: 'ui-test@flowpay.local' } })
    } else if (url.pathname === '/api/auth/session') {
      if (!authenticated) {
        await route.fulfill({ status: 401, json: { title: 'Unauthorized' } })
        return
      }
      await route.fulfill({ json: {
        email: 'ui-test@flowpay.local',
        tenants: [{ id: tenantId, name: 'Empresa sintética', role: 'OWNER' }],
      } })
    } else if (url.pathname === '/api/dashboard') {
      await route.fulfill({ json: {
        from: '2026-11-01', to: '2026-11-30', timezone: 'America/Sao_Paulo', asOf: '2026-11-24',
        controlledBalanceMinor: '2500000', periodInflowsMinor: '0', periodOutflowsMinor: '0',
        receivablesDueMinor: '0', payablesDueMinor: '0', overdueReceivablesMinor: '0', overduePayablesMinor: '0',
        upcomingDues: [], recentMovements: [],
      } })
    } else {
      await route.fulfill({ json: { items: [], total: 0, page: 0, size: 20, asOf: '2026-11-24' } })
    }
  })

  await page.addInitScript(() => window.localStorage.removeItem('flowpay.activeTenantId'))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByLabel('E-mail').fill('ui-test@flowpay.local')
  await page.getByLabel('Senha').fill('Synthetic-Only-Password')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'workspace with synthetic API')

  const skipLink = page.getByRole('link', { name: 'Pular para o conteúdo principal' })
  await skipLink.focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('#workspace-content')).toBeFocused()

  const menu = page.getByRole('button', { name: 'Abrir menu de navegação' })
  const navigation = page.getByRole('navigation', { name: 'Navegação principal' })
  await expect(menu).toBeVisible()
  await expect(navigation).toBeHidden()
  await menu.focus()
  await menu.press('Enter')
  await expect(navigation).toBeVisible()
  await navigation.getByRole('button', { name: 'Visão geral' }).focus()
  await page.keyboard.press('Escape')
  await expect(menu).toBeFocused()
  await expect(navigation).toBeHidden()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)

  await page.setViewportSize({ width: 800, height: 900 })
  await expect(menu).toBeVisible()
  await expect(navigation).toBeHidden()
  await menu.click()
  await expect(navigation).toBeVisible()
  await expectAutomaticallyAccessible(page, 'tablet workspace navigation open with synthetic API')
  await navigation.getByRole('button', { name: 'Contatos' }).click()
  await expect(page.getByRole('heading', { name: 'Clientes e fornecedores' })).toBeVisible()
  await expect(menu).toBeFocused()
  await expect(page.locator('#workspace-navigation .nav-link[aria-current="page"]')).toHaveText('Contatos')
  await expectAutomaticallyAccessible(page, 'contacts workspace at tablet width with synthetic API')
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(800)

  await page.setViewportSize({ width: 1024, height: 900 })
  await expect(menu).toBeHidden()
  await expect(navigation).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1024)

  await navigation.getByRole('button', { name: 'Extratos' }).click()
  await expect(page.getByRole('heading', { name: 'Prévia de extrato' })).toBeVisible()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar CSV de exemplo' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('flowpay-exemplo-extrato.csv')
  const path = await download.path()
  expect(path).toBeTruthy()
  const rows = (await readFile(path!, 'utf8')).replace(/^\uFEFF/, '').trimEnd().split(/\r?\n/)
  expect(rows[0]).toBe('data;descricao;valor;direcao')
  expect(rows[1]).toMatch(/^\d{2}\/\d{2}\/\d{4};Exemplo sintético de entrada;100,00;ENTRADA$/)
  expect(rows[2]).toMatch(/^\d{2}\/\d{2}\/\d{4};Exemplo sintético de saída;25,50;SAIDA$/)
})

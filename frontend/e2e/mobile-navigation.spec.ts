import { expect, test } from '@playwright/test'
import { expectAutomaticallyAccessible } from './accessibility'

const demoPassword = process.env.FLOWPAY_E2E_DEMO_PASSWORD

if (!demoPassword) {
  throw new Error('Set FLOWPAY_E2E_DEMO_PASSWORD for the isolated local demo environment.')
}

test('mobile navigation disclosure supports keyboard and returns focus', async ({ page }) => {
  await page.addInitScript(() => window.localStorage.removeItem('flowpay.activeTenantId'))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await page.getByLabel('E-mail').fill('demo@flowpay.local')
  await page.getByLabel('Senha').fill(demoPassword!)
  await page.getByLabel('Senha').press('Enter')
  if (process.env.FLOWPAY_DEMO_MULTI_TENANT_E2E === 'true') {
    await expect(page.getByRole('heading', { name: 'Escolha uma empresa' })).toBeVisible()
    await page.getByRole('button', { name: /FlowPay Demonstração/ }).click()
  }
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()

  const menu = page.getByRole('button', { name: 'Abrir menu de navegação' })
  const navigation = page.getByRole('navigation', { name: 'Navegação principal' })
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await expect(navigation).toBeHidden()

  const skipLink = page.getByRole('link', { name: 'Pular para o conteúdo principal' })
  await skipLink.focus()
  await expect(skipLink).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#workspace-content')).toBeFocused()

  await menu.focus()
  await menu.press('Enter')
  await expect(page.getByRole('button', { name: 'Fechar menu de navegação' })).toHaveAttribute('aria-expanded', 'true')
  await expect(navigation).toBeVisible()
  await expectAutomaticallyAccessible(page, 'mobile workspace with navigation open')
  await page.keyboard.press('Tab')
  await expect(navigation.getByRole('button', { name: 'Visão geral' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(menu).toBeFocused()
  await expect(navigation).toBeHidden()

  await menu.click()
  await navigation.getByRole('button', { name: 'Contatos' }).click()
  await expect(page.getByRole('heading', { name: 'Clientes e fornecedores' })).toBeVisible()
  await expect(menu).toBeFocused()
  await expect(navigation).toBeHidden()

  await menu.click()
  await navigation.getByRole('button', { name: 'Contas financeiras' }).focus()
  await page.keyboard.press('Escape')
  await expect(menu).toBeFocused()
  await expect(navigation).toBeHidden()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)

  await page.setViewportSize({ width: 800, height: 900 })
  await expect(menu).toBeVisible()
  await expect(navigation).toBeHidden()
  await menu.click()
  await expect(navigation).toBeVisible()
  await expectAutomaticallyAccessible(page, 'tablet workspace with navigation open')
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(800)
  await page.keyboard.press('Escape')
  await expect(menu).toBeFocused()
  await expect(navigation).toBeHidden()

  await page.setViewportSize({ width: 1024, height: 900 })
  await expect(menu).toBeHidden()
  await expect(navigation).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1024)
})

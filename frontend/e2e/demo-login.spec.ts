import { expect, test } from '@playwright/test'
import { expectAutomaticallyAccessible } from './accessibility'

const demoPassword = process.env.FLOWPAY_E2E_DEMO_PASSWORD

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

test('demo owner signs in and sees the seeded financial workspace', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Crie sua empresa' })).toBeVisible()
  const switchToLogin = page.getByRole('button', { name: 'Entrar', exact: true })
  await tabUntilFocused(page, switchToLogin)
  await switchToLogin.press('Enter')
  await expect(page.getByRole('heading', { name: 'Acesse sua conta' })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'demo login')

  const email = page.getByLabel('E-mail')
  const password = page.getByLabel('Senha')
  await tabUntilFocused(page, email)
  await email.fill('demo@flowpay.local')
  await password.fill(demoPassword!)
  await password.press('Enter')

  await expect(page.getByText('Ambiente de demonstração', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()
  await expect(page.getByText('Saldo controlado', { exact: true })).toBeVisible()
  await expect(page.getByText('R$ 25.000,00', { exact: true })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'dashboard')

  await page.getByRole('button', { name: 'Contatos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Cliente Exemplo Ltda.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Fornecedor Exemplo Ltda.' })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'contacts')

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Conta principal demo' })).toBeVisible()

  await page.getByRole('button', { name: 'A receber', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projeto demonstração' })).toBeVisible()

  await page.getByRole('button', { name: 'A pagar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Licença de software' })).toBeVisible()

  await page.getByRole('button', { name: 'Extratos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Prévia de extrato' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Selecionar conta e arquivo' })).toBeVisible()
  await expect(page.getByLabel('Conta financeira')).toBeVisible()
  await expect(page.getByLabel('Arquivo CSV')).toBeVisible()
  await expect(page.getByText(/Esta prévia não salva linhas nem altera o caixa\./)).toBeVisible()
  await expectAutomaticallyAccessible(page, 'statement import')

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: 'Prévia de extrato' })).toBeVisible()
  const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(documentWidth).toBeLessThanOrEqual(390)
})

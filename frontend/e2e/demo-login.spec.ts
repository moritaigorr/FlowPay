import { expect, test } from '@playwright/test'

const demoPassword = process.env.FLOWPAY_E2E_DEMO_PASSWORD

test.skip(!demoPassword, 'Set FLOWPAY_E2E_DEMO_PASSWORD for the isolated local demo environment.')

test('demo owner signs in and sees the seeded financial workspace', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Crie sua empresa' })).toBeVisible()
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Acesse sua conta' })).toBeVisible()

  await page.getByLabel('E-mail').fill('demo@flowpay.local')
  await page.getByLabel('Senha').fill(demoPassword!)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()

  await expect(page.getByText('Ambiente de demonstração', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible()
  await expect(page.getByText('Saldo controlado', { exact: true })).toBeVisible()
  await expect(page.getByText('R$ 25.000,00', { exact: true })).toBeVisible()
  await expect(page.getByText('R$ 1.850,00', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Contatos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Cliente Exemplo Ltda.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Fornecedor Exemplo Ltda.' })).toBeVisible()

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Conta principal demo' })).toBeVisible()

  await page.getByRole('button', { name: 'A receber', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projeto demonstração' })).toBeVisible()

  await page.getByRole('button', { name: 'A pagar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Licença de software' })).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByText('Ambiente de demonstração', { exact: true })).toBeVisible()
  const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(documentWidth).toBeLessThanOrEqual(390)
})

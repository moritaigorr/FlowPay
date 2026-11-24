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

  // A 1440px desktop at 200% zoom has roughly a 720px CSS layout viewport.
  await page.setViewportSize({ width: 720, height: 900 })
  const zoomedDashboardWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(zoomedDashboardWidth).toBeLessThanOrEqual(720)
  await expect(page.getByText('R$ 25.000,00', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 1440, height: 1000 })

  await page.getByRole('button', { name: 'Contatos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Cliente Exemplo Ltda.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Fornecedor Exemplo Ltda.' })).toBeVisible()
  await expectAutomaticallyAccessible(page, 'contacts')

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Conta principal demo' })).toBeVisible()

  await page.getByRole('button', { name: 'A receber', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projeto demonstração' })).toBeVisible()

  const filterStart = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
  const filterEndDate = new Date(`${filterStart}T12:00:00Z`)
  filterEndDate.setUTCDate(filterEndDate.getUTCDate() + 365)
  const filterEnd = filterEndDate.toISOString().slice(0, 10)
  const filteredRequest = page.waitForRequest((request) => {
    const url = new URL(request.url())
    return url.pathname === '/api/financial-titles'
      && url.searchParams.get('status') === 'OPEN'
      && url.searchParams.has('contactId')
      && url.searchParams.get('dueFrom') === filterStart
      && url.searchParams.get('dueTo') === filterEnd
  })
  await page.getByLabel('Estado').selectOption('OPEN')
  await page.getByLabel('Pessoa').selectOption({ label: 'Cliente Exemplo Ltda.' })
  await page.getByLabel('Vencimento a partir de').fill(filterStart)
  await page.getByLabel('Vencimento até').fill(filterEnd)
  const request = await filteredRequest
  const filterParams = new URL(request.url()).searchParams
  expect(filterParams.get('status')).toBe('OPEN')
  expect(filterParams.get('dueFrom')).toBe(filterStart)
  expect(filterParams.get('dueTo')).toBe(filterEnd)
  expect(filterParams.get('contactId')).toBeTruthy()
  await page.getByRole('button', { name: 'Limpar filtros' }).click()
  await expect(page.getByLabel('Estado')).toHaveValue('')
  await expect(page.getByLabel('Pessoa')).toHaveValue('')
  await expect(page.getByLabel('Vencimento a partir de')).toHaveValue('')
  await expect(page.getByLabel('Vencimento até')).toHaveValue('')

  const emptyFilteredRequest = page.waitForRequest((filtered) => {
    const url = new URL(filtered.url())
    return url.pathname === '/api/financial-titles'
      && url.searchParams.get('dueFrom') === '2000-01-01'
      && url.searchParams.get('dueTo') === '2000-12-31'
  })
  await page.getByLabel('Vencimento a partir de').fill('2000-01-01')
  await page.getByLabel('Vencimento até').fill('2000-12-31')
  await emptyFilteredRequest
  await expect(page.getByRole('heading', { name: 'Nenhum título encontrado' })).toBeVisible()
  await expect(page.getByText('Ajuste os filtros ou limpe-os para ver outros títulos.')).toBeVisible()
  await page.getByRole('button', { name: 'Limpar filtros' }).click()
  await expect(page.getByRole('heading', { name: 'Projeto demonstração' })).toBeVisible()

  await page.getByRole('button', { name: 'A pagar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Licença de software' })).toBeVisible()

  await page.getByRole('button', { name: 'Extratos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Prévia de extrato' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Selecionar conta e arquivo' })).toBeVisible()
  await expect(page.getByLabel('Conta financeira')).toBeVisible()
  await expect(page.getByLabel('Arquivo CSV')).toBeVisible()
  await expect(page.getByText(/UTF-8, até 10 MB e 50\.000 linhas de dados\./)).toBeVisible()
  await expect(page.getByText(/Esta prévia não salva linhas nem altera o caixa\./)).toBeVisible()
  await expectAutomaticallyAccessible(page, 'statement import')

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: 'Prévia de extrato' })).toBeVisible()
  const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(documentWidth).toBeLessThanOrEqual(390)
})

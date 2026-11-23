import { expect, test } from '@playwright/test'

test('new company imports a settled receipt and links without duplicating cash', async ({ page }) => {
  test.skip(
    process.env.FLOWPAY_E2E_ALLOW_WRITES !== 'true',
    'This journey writes financial data and runs only against an isolated E2E database.',
  )

  const id = crypto.randomUUID()
  const today = new Date().toISOString().slice(0, 10)
  const dueDate = new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

  await page.goto('/')
  await page.getByLabel('Nome da empresa').fill(`E2E ${id}`)
  await page.getByLabel('E-mail').fill(`e2e-${id}@flowpay.local`)
  await page.getByLabel('Senha').fill(`FlowPay-E2E-${id}!`)
  await page.getByRole('button', { name: 'Criar empresa', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'VisÃ£o geral' })).toBeVisible()

  await page.getByRole('button', { name: 'Contatos', exact: true }).click()
  await page.getByRole('button', { name: /Novo contato|Cadastrar primeiro contato/ }).click()
  const contactForm = page.locator('form').filter({ has: page.getByLabel('Nome', { exact: true }) })
  await contactForm.getByLabel('Nome', { exact: true }).fill(`Cliente ${id}`)
  await contactForm.getByLabel('Cliente', { exact: true }).check()
  await contactForm.getByRole('button', { name: 'Cadastrar contato', exact: true }).click()
  await expect(page.getByRole('heading', { name: `Cliente ${id}` })).toBeVisible()

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  await page.getByRole('button', { name: /Nova conta|Cadastrar primeira conta/ }).click()
  const accountForm = page.locator('form').filter({ has: page.getByLabel('Nome da conta') })
  await accountForm.getByLabel('Nome da conta').fill('Conta E2E')
  await accountForm.locator('[name="type"]').selectOption('BANK')
  await accountForm.getByLabel('Saldo inicial em reais').fill('1.000,00')
  await accountForm.getByLabel('Data do saldo inicial').fill(today)
  await accountForm.getByRole('button', { name: 'Salvar conta', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Conta E2E' })).toBeVisible()
  await expect(page.getByText('R$ 1.000,00', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Categorias', exact: true }).click()
  await page.getByRole('button', { name: /Nova categoria|Criar primeira categoria/ }).click()
  const categoryForm = page.locator('form').filter({ has: page.getByLabel('Nome', { exact: true }) })
  await categoryForm.getByLabel('Nome', { exact: true }).fill(`Receita ${id}`)
  await categoryForm.locator('[name="direction"]').selectOption('INFLOW')
  await categoryForm.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(page.getByText(`Receita ${id}`, { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'A receber', exact: true }).click()
  await page.getByRole('button', { name: /Novo t|Cadastrar primeiro t/ }).click()
  const titleForm = page.locator('form').filter({ has: page.locator('[name="description"]') })
  await titleForm.locator('[name="description"]').fill(`Servico ${id}`)
  await titleForm.locator('[name="contactId"]').selectOption({ label: `Cliente ${id}` })
  await titleForm.locator('[name="categoryId"]').selectOption({ label: `Receita ${id}` })
  await titleForm.locator('[name="amount"]').fill('1.234,56')
  await titleForm.locator('[name="dueDate"]').fill(dueDate)
  await titleForm.locator('[name="plannedAccountId"]').selectOption({ label: 'Conta E2E' })
  await titleForm.locator('button[type="submit"]').click()
  const title = page.locator('article').filter({ has: page.getByRole('heading', { name: `Servico ${id}` }) })
  await expect(title).toBeVisible()
  await expect(title).toContainText('R$ 1.234,56 em aberto')

  await title.getByRole('button', { name: 'Registrar recebimento', exact: true }).click()
  const settlementForm = title.locator('form')
  await settlementForm.getByLabel('Valor em reais').fill('200,00')
  await settlementForm.getByLabel('Conta').selectOption({ label: 'Conta E2E' })
  await settlementForm.getByLabel('Data realizada').fill(today)
  await settlementForm.getByRole('button', { name: 'Confirmar recebimento', exact: true }).click()

  await expect(title).toContainText('R$ 1.034,56 em aberto')
  await page.reload()
  await page.getByRole('button', { name: 'A receber', exact: true }).click()
  const persistedTitle = page.locator('article').filter({ has: page.getByRole('heading', { name: `Servico ${id}` }) })
  await expect(persistedTitle).toContainText('R$ 1.034,56 em aberto')
  await expect(persistedTitle).toContainText('Parcialmente pago')

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  const persistedAccount = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Conta E2E' }) })
  await expect(persistedAccount).toContainText('R$ 1.200,00')

  await page.getByRole('button', { name: 'Extratos', exact: true }).click()
  await page.getByLabel('Conta financeira').selectOption({ label: 'Conta E2E' })
  const [year, month, day] = today.split('-')
  const statementCsv = `data;descricao;valor;direcao\n${day}/${month}/${year};Recebimento: Servico ${id};200,00;ENTRADA\n`
  await page.getByLabel('Arquivo CSV').setInputFiles({
    name: `extrato-${id}.csv`,
    mimeType: 'text/csv',
    buffer: Buffer.from(statementCsv, 'utf-8'),
  })
  await page.getByRole('button', { name: 'Gerar prévia' }).click()
  await expect(page.getByRole('heading', { name: 'Resultado da prévia' })).toBeVisible()
  await expect(page.getByText(/1 aceitas/)).toBeVisible()
  await page.getByRole('button', { name: 'Confirmar importação' }).click()
  await expect(page.getByRole('status')).toContainText('O caixa não foi alterado.')

  await page.getByRole('button', { name: 'Ver linhas', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Linhas salvas' })).toBeVisible()
  await page.getByRole('button', { name: 'Sugerir movimento', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Sugestões de movimento' })).toBeVisible()
  const linkCandidate = page.getByRole('button', { name: 'Vincular', exact: true })
  await expect(linkCandidate).toBeVisible()
  await linkCandidate.click()
  const matchConfirmation = page.getByRole('group', { name: 'Confirmar conciliação' })
  await expect(matchConfirmation).toContainText('O caixa não será alterado.')
  await matchConfirmation.getByRole('button', { name: 'Confirmar vínculo', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('O caixa não foi alterado.')
  await expect(page.getByText('Conciliada', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Gerar prévia' }).click()
  await expect(page.getByRole('heading', { name: 'Resultado da prévia' })).toBeVisible()
  await page.getByRole('button', { name: 'Confirmar importação' }).click()
  await expect(page.getByRole('status')).toContainText('Este arquivo já estava salvo')
  await expect(page.getByText('1 importação', { exact: true })).toBeVisible()
  await expect(page.getByText('Conciliada', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Contas financeiras', exact: true }).click()
  await expect(persistedAccount).toContainText('R$ 1.200,00')
})

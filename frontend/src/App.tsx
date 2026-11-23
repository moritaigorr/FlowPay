import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'
import { formatMinor, parseBrlToMinor } from './finance/format'
import { Dashboard } from './reporting/Dashboard'
import { MonthlyReport } from './reporting/MonthlyReport'
import { StatementImportPreview } from './statements/StatementImportPreview'
import { useFocusReturnOnClose } from './shared/useFocusReturnOnClose'

type Mode = 'register' | 'login'
type ContactRole = 'CUSTOMER' | 'SUPPLIER'
type Contact = {
  id: string
  name: string
  roles: ContactRole[]
  email: string | null
  phone: string | null
  notes: string | null
}
type ContactPage = { items: Contact[]; total: number; page: number; size: number }
type AccountType = 'BANK' | 'CASH' | 'OTHER'
type AccountStatus = 'ACTIVE' | 'ARCHIVED'
type FinancialAccount = {
  id: string
  name: string
  type: AccountType
  status: AccountStatus
  currency: 'BRL'
  balanceMinor: string
  openingBalanceDate: string
}
type AccountPage = { items: FinancialAccount[]; total: number; page: number; size: number }
type CategoryDirection = 'INFLOW' | 'OUTFLOW'
type CategoryStatus = 'ACTIVE' | 'ARCHIVED'
type FinancialCategory = { id: string; name: string; direction: CategoryDirection; status: CategoryStatus }
type CategoryPage = { items: FinancialCategory[]; total: number; page: number; size: number }
type TitleKind = 'RECEIVABLE' | 'PAYABLE'
type TitleStatus = 'OPEN' | 'OVERDUE' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'CANCELLED'
type FinancialTitle = { id: string; kind: TitleKind; description: string; contactId: string; contactName: string; categoryId: string; categoryName: string; plannedAccountId: string | null; plannedAccountName: string | null; originalAmountMinor: string; settledAmountMinor: string; remainingAmountMinor: string; currency: 'BRL'; dueDate: string; notes: string | null; status: TitleStatus; cancellationReason: string | null; asOf: string }
type TitlePage = { items: FinancialTitle[]; total: number; page: number; size: number; asOf: string }
type TitleOptions = { contacts: Contact[]; categories: FinancialCategory[]; accounts: FinancialAccount[] }
type Settlement = { id: string; amountMinor: string; reversedAmountMinor: string; remainingAmountMinor: string; effectiveDate: string }
type CashTransaction = { id: string; direction: 'INFLOW' | 'OUTFLOW'; amountMinor: string; effectiveDate: string; origin: string; description: string; categoryId: string | null; categoryName: string | null; accountId: string; accountName: string }
type CashTransactionPage = { items: CashTransaction[]; total: number; page: number; size: number }
type CashTransactionOptions = { categories: FinancialCategory[]; accounts: FinancialAccount[] }

function accountTypeLabel(type: AccountType) {
  return { BANK: 'Conta bancária', CASH: 'Caixa', OTHER: 'Outra conta' }[type]
}

function csrfCookie() {
  return document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=')
}

async function refreshCsrf() {
  await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
}

function App() {
  const [mode, setMode] = useState<Mode>('register')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [authenticatedEmail, setAuthenticatedEmail] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [contacts, setContacts] = useState<ContactPage>({ items: [], total: 0, page: 0, size: 20 })
  const [contactsLoadedKey, setContactsLoadedKey] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [showContactForm, setShowContactForm] = useState(false)
  const [editing, setEditing] = useState<Contact | null>(null)
  const [contactsHeadingRef, requestContactsFocusReturn] = useFocusReturnOnClose(showContactForm)
  const [activeSection, setActiveSection] = useState<'dashboard' | 'reports' | 'contacts' | 'accounts' | 'categories' | 'receivables' | 'payables' | 'transactions' | 'statements'>('dashboard')
  const [accounts, setAccounts] = useState<AccountPage>({ items: [], total: 0, page: 0, size: 20 })
  const [accountsPage, setAccountsPage] = useState(0)
  const [accountsLoadedKey, setAccountsLoadedKey] = useState('')
  const [accountsRefresh, setAccountsRefresh] = useState(0)
  const [showAccountForm, setShowAccountForm] = useState(false)
  const [accountsHeadingRef, requestAccountsFocusReturn] = useFocusReturnOnClose(showAccountForm)
  const [editingAccount, setEditingAccount] = useState<FinancialAccount | null>(null)
  const accountIdempotencyKey = useRef<string | null>(null)
  const [categories, setCategories] = useState<CategoryPage>({ items: [], total: 0, page: 0, size: 20 })
  const [categoriesLoaded, setCategoriesLoaded] = useState(false)
  const [categoriesRefresh, setCategoriesRefresh] = useState(0)
  const [showCategoryForm, setShowCategoryForm] = useState(false)
  const [categoriesHeadingRef, requestCategoriesFocusReturn] = useFocusReturnOnClose(showCategoryForm)
  const [editingCategory, setEditingCategory] = useState<FinancialCategory | null>(null)
  const [titles, setTitles] = useState<TitlePage>({ items: [], total: 0, page: 0, size: 20, asOf: '' })
  const [titlesPage, setTitlesPage] = useState(0)
  const [titlesQuery, setTitlesQuery] = useState('')
  const [titlesLoadedKey, setTitlesLoadedKey] = useState('')
  const [titlesRefresh, setTitlesRefresh] = useState(0)
  const [titleOptions, setTitleOptions] = useState<TitleOptions | null>(null)
  const [editingTitle, setEditingTitle] = useState<FinancialTitle | null>(null)
  const [showTitleForm, setShowTitleForm] = useState(false)
  const [titlesHeadingRef, requestTitlesFocusReturn] = useFocusReturnOnClose(showTitleForm)
  const [settlingTitleId, setSettlingTitleId] = useState<string | null>(null)
  const restoreTitlesFocusAfterSettlementClose = useRef(false)
  const [settlementHistory, setSettlementHistory] = useState<{ titleId: string; items: Settlement[] } | null>(null)
  const [reversingSettlementId, setReversingSettlementId] = useState<string | null>(null)
  const restoreTitlesFocusAfterReversalClose = useRef(false)
  const [cashTransactions, setCashTransactions] = useState<CashTransactionPage>({ items: [], total: 0, page: 0, size: 20 })
  const [cashTransactionsPage, setCashTransactionsPage] = useState(0)
  const [cashTransactionsLoaded, setCashTransactionsLoaded] = useState(false)
  const [cashTransactionsRefresh, setCashTransactionsRefresh] = useState(0)
  const [showCashTransactionForm, setShowCashTransactionForm] = useState(false)
  const [transactionsHeadingRef, requestTransactionsFocusReturn] = useFocusReturnOnClose(showCashTransactionForm)
  const [cashTransactionDirection, setCashTransactionDirection] = useState<'INFLOW' | 'OUTFLOW'>('OUTFLOW')
  const [cashTransactionOptions, setCashTransactionOptions] = useState<CashTransactionOptions | null>(null)
  const titleIdempotencyKey = useRef<string | null>(null)
  const settlementIdempotency = useRef(new Map<string, { key: string; signature: string }>())
  const reversalIdempotency = useRef(new Map<string, { key: string; signature: string }>())
  const cashTransactionIdempotency = useRef<{ key: string; signature: string } | null>(null)
  const contactsLoading = Boolean(authenticatedEmail && contactsLoadedKey !== `${page}:${query.trim()}`)
  const accountsLoading = Boolean(authenticatedEmail && activeSection === 'accounts' && accountsLoadedKey !== `${accountsPage}:${accountsRefresh}`)
  const categoriesLoading = Boolean(authenticatedEmail && activeSection === 'categories' && !categoriesLoaded)
  const titleKind: TitleKind | null = activeSection === 'receivables' ? 'RECEIVABLE' : activeSection === 'payables' ? 'PAYABLE' : null
  const titleRequestKey = titleKind ? titleKind + ':' + titlesPage + ':' + titlesQuery.trim() + ':' + titlesRefresh : ''
  const titlesLoading = Boolean(authenticatedEmail && titleKind && titlesLoadedKey !== titleRequestKey)
  const titleOptionsLoading = Boolean(authenticatedEmail && titleKind && !titleOptions)
  const cashTransactionsLoading = Boolean(authenticatedEmail && activeSection === 'transactions' && !cashTransactionsLoaded)

  useEffect(() => {
    let active = true
    void (async () => {
      await refreshCsrf()
      try {
        const response = await fetch('/api/auth/session', { credentials: 'same-origin', cache: 'no-store' })
        if (!response.ok) return
        const session = await response.json() as { email: string; companyName: string }
        if (active) {
          setAuthenticatedEmail(session.email)
          setCompanyName(session.companyName)
        }
      } catch {
        if (active) setMessage('Não foi possível conectar à API.')
      }
    })()
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!authenticatedEmail || activeSection !== 'contacts') return
    let active = true
    const requestKey = `${page}:${query.trim()}`
    const params = new URLSearchParams({ page: String(page), size: '20' })
    if (query.trim()) params.set('q', query.trim())
    fetch(`/api/contacts?${params}`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar os contatos.')
        return await response.json() as ContactPage
      })
      .then((result) => { if (active) setContacts(result) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar contatos.') })
      .finally(() => { if (active) setContactsLoadedKey(requestKey) })
    return () => { active = false }
  }, [authenticatedEmail, activeSection, page, query])

  useEffect(() => {
    if (settlingTitleId !== null || !restoreTitlesFocusAfterSettlementClose.current) return
    restoreTitlesFocusAfterSettlementClose.current = false
    titlesHeadingRef.current?.focus()
  }, [settlingTitleId, titlesHeadingRef])

  useEffect(() => {
    if (reversingSettlementId !== null || !restoreTitlesFocusAfterReversalClose.current) return
    restoreTitlesFocusAfterReversalClose.current = false
    titlesHeadingRef.current?.focus()
  }, [reversingSettlementId, titlesHeadingRef])

  useEffect(() => {
    if (!authenticatedEmail || activeSection !== 'accounts') return
    let active = true
    const requestKey = `${accountsPage}:${accountsRefresh}`
    const params = new URLSearchParams({ page: String(accountsPage), size: '20' })
    fetch(`/api/financial-accounts?${params}`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar as contas financeiras.')
        return await response.json() as AccountPage
      })
      .then((result) => { if (active) setAccounts(result) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar contas financeiras.') })
      .finally(() => { if (active) setAccountsLoadedKey(requestKey) })
    return () => { active = false }
  }, [authenticatedEmail, activeSection, accountsPage, accountsRefresh])

  useEffect(() => {
    if (!authenticatedEmail || activeSection !== 'categories' || categoriesLoaded) return
    let active = true
    fetch('/api/financial-categories?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => { if (!response.ok) throw new Error('Não foi possível carregar as categorias.'); return await response.json() as CategoryPage })
      .then((result) => { if (active) setCategories(result) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar categorias.') })
      .finally(() => { if (active) setCategoriesLoaded(true) })
    return () => { active = false }
  }, [authenticatedEmail, activeSection, categoriesLoaded, categoriesRefresh])

  useEffect(() => {
    if (!authenticatedEmail || !titleKind) return
    let active = true
    const params = new URLSearchParams({ kind: titleKind, page: String(titlesPage), size: '20' })
    if (titlesQuery.trim()) params.set('q', titlesQuery.trim())
    fetch('/api/financial-titles?' + params.toString(), { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => { if (!response.ok) throw new Error('Não foi possível carregar os títulos.'); return await response.json() as TitlePage })
      .then((result) => { if (active) setTitles(result) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar títulos.') })
      .finally(() => { if (active) setTitlesLoadedKey(titleRequestKey) })
    return () => { active = false }
  }, [authenticatedEmail, titleKind, titlesPage, titlesQuery, titlesRefresh, titleRequestKey])

  useEffect(() => {
    if (!authenticatedEmail || !titleKind || titleOptions) return
    let active = true
    void Promise.all([
      fetch('/api/contacts?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.ok ? r.json() as Promise<ContactPage> : Promise.reject(new Error('Não foi possível carregar contatos.'))),
      fetch('/api/financial-categories?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.ok ? r.json() as Promise<CategoryPage> : Promise.reject(new Error('Não foi possível carregar categorias.'))),
      fetch('/api/financial-accounts?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.ok ? r.json() as Promise<AccountPage> : Promise.reject(new Error('Não foi possível carregar contas.'))),
    ]).then(([contactPage, categoryPage, accountPage]) => { if (active) setTitleOptions({ contacts: contactPage.items, categories: categoryPage.items, accounts: accountPage.items }) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar opções para o título.') })
    return () => { active = false }
  }, [authenticatedEmail, titleKind, titleOptions])

  useEffect(() => {
    if (!authenticatedEmail || !settlingTitleId) return
    let active = true
    fetch(`/api/financial-titles/${settlingTitleId}/settlements`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => { if (!response.ok) throw new Error('Não foi possível carregar as baixas deste título.'); return await response.json() as Settlement[] })
      .then((items) => { if (active) setSettlementHistory({ titleId: settlingTitleId, items }) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar as baixas.') })
    return () => { active = false }
  }, [authenticatedEmail, settlingTitleId])

  useEffect(() => {
    if (!authenticatedEmail || activeSection !== 'transactions' || cashTransactionsLoaded) return
    let active = true
    fetch(`/api/cash-transactions?page=${cashTransactionsPage}&size=20`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => { if (!response.ok) throw new Error('Não foi possível carregar os movimentos de caixa.'); return await response.json() as CashTransactionPage })
      .then((page) => { if (active) setCashTransactions(page) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar movimentos.') })
      .finally(() => { if (active) setCashTransactionsLoaded(true) })
    return () => { active = false }
  }, [authenticatedEmail, activeSection, cashTransactionsLoaded, cashTransactionsRefresh, cashTransactionsPage])

  useEffect(() => {
    if (!authenticatedEmail || activeSection !== 'transactions' || cashTransactionOptions) return
    let active = true
    void Promise.all([
      fetch('/api/financial-categories?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.ok ? r.json() as Promise<CategoryPage> : Promise.reject(new Error('Não foi possível carregar categorias.'))),
      fetch('/api/financial-accounts?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.ok ? r.json() as Promise<AccountPage> : Promise.reject(new Error('Não foi possível carregar contas.'))),
    ]).then(([categoryPage, accountPage]) => { if (active) setCashTransactionOptions({ categories: categoryPage.items, accounts: accountPage.items }) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar opções de movimento.') })
    return () => { active = false }
  }, [authenticatedEmail, activeSection, cashTransactionOptions])

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage('')
    const data = new FormData(event.currentTarget)
    const payload: Record<string, string> = {
      email: String(data.get('email') ?? ''),
      password: String(data.get('password') ?? ''),
    }
    if (mode === 'register') payload.companyName = String(data.get('companyName') ?? '')

    try {
      const response = await fetch(`/api/auth/${mode}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
        body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error(response.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível concluir. Confira os dados e tente novamente.')
      const sessionResponse = await fetch('/api/auth/session', { credentials: 'same-origin', cache: 'no-store' })
      if (!sessionResponse.ok) throw new Error('A sessão foi iniciada, mas não foi possível carregar a empresa.')
      const session = await sessionResponse.json() as { email: string; companyName: string }
      setContacts({ items: [], total: 0, page: 0, size: 20 })
      setContactsLoadedKey('')
      setAccounts({ items: [], total: 0, page: 0, size: 20 })
      setAccountsLoadedKey('')
      setAccountsPage(0)
      setCategories({ items: [], total: 0, page: 0, size: 20 })
      setCategoriesLoaded(false)
      setTitles({ items: [], total: 0, page: 0, size: 20, asOf: '' })
      setCashTransactions({ items: [], total: 0, page: 0, size: 20 })
      setCashTransactionsPage(0)
      setCashTransactionsLoaded(false)
      setCashTransactionOptions(null)
      setShowCashTransactionForm(false)
      setTitlesPage(0)
      setTitlesQuery('')
      setTitlesLoadedKey('')
      setTitleOptions(null)
      setShowTitleForm(false)
      setEditingTitle(null)
      setActiveSection('dashboard')
      setAuthenticatedEmail(session.email)
      setCompanyName(session.companyName)
      await refreshCsrf()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha de conexão com a API.')
      await refreshCsrf()
    } finally {
      setBusy(false)
    }
  }

  async function submitContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage('')
    const data = new FormData(event.currentTarget)
    const payload = {
      name: String(data.get('name') ?? ''),
      roles: data.getAll('roles'),
      email: String(data.get('email') ?? ''),
      phone: String(data.get('phone') ?? ''),
      notes: String(data.get('notes') ?? ''),
    }
    try {
      await refreshCsrf()
      const response = await fetch(editing ? `/api/contacts/${editing.id}` : '/api/contacts', {
        method: editing ? 'PUT' : 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
        body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error('Não foi possível salvar. Confira o nome e selecione cliente, fornecedor ou ambos.')
      requestContactsFocusReturn()
      setShowContactForm(false)
      setEditing(null)
      setTitleOptions(null)
      setMessage(editing ? 'Contato atualizado.' : 'Contato cadastrado.')
      const params = new URLSearchParams({ page: String(page), size: '20' })
      if (query.trim()) params.set('q', query.trim())
      const list = await fetch(`/api/contacts?${params}`, { credentials: 'same-origin', cache: 'no-store' })
      if (list.ok) setContacts(await list.json() as ContactPage)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao salvar contato.')
    } finally {
      setBusy(false)
    }
  }

  async function submitAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      await refreshCsrf()
      if (editingAccount) {
        const response = await fetch(`/api/financial-accounts/${editingAccount.id}`, {
          method: 'PUT', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
          body: JSON.stringify({ name: String(data.get('name') ?? ''), status: data.get('status') }),
        })
        if (!response.ok) throw new Error('Não foi possível atualizar a conta. Confira os dados.')
        setMessage('Conta atualizada.')
      } else {
        accountIdempotencyKey.current ??= crypto.randomUUID()
        const payload = {
          name: String(data.get('name') ?? ''),
          type: data.get('type'),
          openingBalanceMinor: parseBrlToMinor(String(data.get('openingBalance') ?? '')),
          openingBalanceDate: String(data.get('openingBalanceDate') ?? ''),
        }
        const response = await fetch('/api/financial-accounts', {
          method: 'POST', credentials: 'same-origin',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? ''),
            'Idempotency-Key': accountIdempotencyKey.current,
          },
          body: JSON.stringify(payload),
        })
        if (!response.ok) throw new Error(response.status === 409 ? 'Esta tentativa de criação já foi usada com dados diferentes. Altere um campo e tente novamente.' : 'Não foi possível criar a conta. Confira os dados e tente novamente.')
        accountIdempotencyKey.current = null
        setMessage(response.status === 200 ? 'A criação anterior foi recuperada com segurança.' : 'Conta financeira criada.')
      }
      requestAccountsFocusReturn()
      setShowAccountForm(false)
      setEditingAccount(null)
      setTitleOptions(null)
      setCashTransactionOptions(null)
      setAccountsPage(0)
      setAccountsRefresh((value) => value + 1)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao salvar a conta financeira.')
    } finally {
      setBusy(false)
    }
  }

  async function updateAccountStatus(account: FinancialAccount) {
    setBusy(true)
    setMessage('')
    try {
      await refreshCsrf()
      const status: AccountStatus = account.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'
      const response = await fetch(`/api/financial-accounts/${account.id}`, {
        method: 'PUT', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
        body: JSON.stringify({ name: account.name, status }),
      })
      if (!response.ok) throw new Error('Não foi possível alterar o estado da conta.')
      setAccountsRefresh((value) => value + 1)
      setTitleOptions(null)
      setCashTransactionOptions(null)
      setMessage(status === 'ARCHIVED' ? 'Conta arquivada.' : 'Conta ativada.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao atualizar conta.')
    } finally {
      setBusy(false)
    }
  }

  async function submitCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      await refreshCsrf()
      const response = await fetch(editingCategory ? `/api/financial-categories/${editingCategory.id}` : '/api/financial-categories', {
        method: editingCategory ? 'PUT' : 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
        body: JSON.stringify(editingCategory ? { name: String(data.get('name') ?? ''), status: data.get('status') } : { name: String(data.get('name') ?? ''), direction: data.get('direction') }),
      })
      if (!response.ok) throw new Error(response.status === 409 ? 'Já existe uma categoria com esse nome e direção.' : 'Não foi possível salvar a categoria.')
      setMessage(editingCategory ? 'Categoria atualizada.' : 'Categoria criada.')
      requestCategoriesFocusReturn()
      setShowCategoryForm(false); setEditingCategory(null); setTitleOptions(null); setCashTransactionOptions(null); setCategoriesLoaded(false); setCategoriesRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao salvar categoria.') }
    finally { setBusy(false) }
  }

  async function submitTitle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!titleKind) return
    setBusy(true); setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      const amount = parseBrlToMinor(String(data.get('amount') ?? ''))
      if (BigInt(amount) <= 0n) throw new Error('O valor precisa ser maior que zero.')
      await refreshCsrf()
      const payload = { description: String(data.get('description') ?? ''), contactId: String(data.get('contactId') ?? ''), categoryId: String(data.get('categoryId') ?? ''), plannedAccountId: String(data.get('plannedAccountId') ?? '') || null, originalAmountMinor: amount, dueDate: String(data.get('dueDate') ?? ''), notes: String(data.get('notes') ?? '') }
      if (editingTitle) {
        const response = await fetch('/api/financial-titles/' + editingTitle.id, { method: 'PUT', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') }, body: JSON.stringify(payload) })
        if (!response.ok) throw new Error('Não foi possível atualizar o título. Confira contato, categoria, conta e valor.')
        setMessage('Título atualizado.')
      } else {
        titleIdempotencyKey.current ??= crypto.randomUUID()
        const response = await fetch('/api/financial-titles', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? ''), 'Idempotency-Key': titleIdempotencyKey.current }, body: JSON.stringify({ kind: titleKind, ...payload }) })
        if (!response.ok) throw new Error(response.status === 409 ? 'Esta tentativa foi repetida com dados diferentes ou encontrou um conflito.' : 'Não foi possível criar o título. Confira os dados e tente novamente.')
        titleIdempotencyKey.current = null
        setMessage(response.status === 200 ? 'A criação anterior foi recuperada com segurança.' : 'Título criado. Ele ainda não altera o caixa.')
      }
      requestTitlesFocusReturn()
      setShowTitleForm(false); setEditingTitle(null); setTitlesPage(0); setTitlesLoadedKey(''); setTitlesRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao salvar o título.') }
    finally { setBusy(false) }
  }

  async function cancelTitle(title: FinancialTitle) {
    const reason = window.prompt('Informe o motivo do cancelamento do título:')
    if (!reason?.trim()) return
    setBusy(true); setMessage('')
    try { await refreshCsrf(); const response = await fetch('/api/financial-titles/' + title.id + '/cancel', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') }, body: JSON.stringify({ reason: reason.trim() }) }); if (!response.ok) throw new Error('Não foi possível cancelar este título.'); setMessage('Título cancelado com o motivo registrado.'); setTitlesLoadedKey(''); setTitlesRefresh((value) => value + 1) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao cancelar o título.') }
    finally { setBusy(false) }
  }

  async function submitSettlement(event: FormEvent<HTMLFormElement>, title: FinancialTitle) {
    event.preventDefault()
    setBusy(true); setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      const amountMinor = parseBrlToMinor(String(data.get('amount') ?? ''))
      if (BigInt(amountMinor) <= 0n) throw new Error('O valor precisa ser maior que zero.')
      const signature = JSON.stringify({ accountId: String(data.get('accountId') ?? ''), amountMinor, effectiveDate: String(data.get('effectiveDate') ?? '') })
      let attempt = settlementIdempotency.current.get(title.id)
      if (!attempt || attempt.signature !== signature) {
        attempt = { key: crypto.randomUUID(), signature }
        settlementIdempotency.current.set(title.id, attempt)
      }
      await refreshCsrf()
      const response = await fetch(`/api/financial-titles/${title.id}/settlements`, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? ''), 'Idempotency-Key': attempt.key }, body: JSON.stringify({ accountId: String(data.get('accountId') ?? ''), amountMinor, effectiveDate: String(data.get('effectiveDate') ?? '') }) })
      if (!response.ok) throw new Error(response.status === 409 ? 'A baixa excede o saldo em aberto ou esta tentativa encontrou um conflito.' : 'Não foi possível registrar a baixa. Confira a conta e os dados.')
      settlementIdempotency.current.delete(title.id)
      setMessage(response.status === 200 ? 'A baixa anterior foi recuperada com segurança.' : 'Baixa registrada e saldo controlado atualizado.')
      restoreTitlesFocusAfterSettlementClose.current = true
      setSettlingTitleId(null); setTitlesLoadedKey(''); setTitlesRefresh((value) => value + 1); setAccountsLoadedKey(''); setAccountsRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao registrar a baixa.') }
    finally { setBusy(false) }
  }

  async function submitReversal(event: FormEvent<HTMLFormElement>, title: FinancialTitle, settlement: Settlement) {
    event.preventDefault()
    setBusy(true); setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      const amountMinor = parseBrlToMinor(String(data.get('amount') ?? ''))
      if (BigInt(amountMinor) <= 0n) throw new Error('O valor precisa ser maior que zero.')
      const payload = { amountMinor, effectiveDate: String(data.get('effectiveDate') ?? ''), reason: String(data.get('reason') ?? '').trim() }
      const signature = JSON.stringify(payload)
      const mapKey = `${title.id}:${settlement.id}`
      let attempt = reversalIdempotency.current.get(mapKey)
      if (!attempt || attempt.signature !== signature) {
        attempt = { key: crypto.randomUUID(), signature }
        reversalIdempotency.current.set(mapKey, attempt)
      }
      await refreshCsrf()
      const response = await fetch(`/api/financial-titles/${title.id}/settlements/${settlement.id}/reversals`, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? ''), 'Idempotency-Key': attempt.key }, body: JSON.stringify(payload) })
      if (!response.ok) throw new Error(response.status === 409 ? 'A reversão excede o valor ainda não revertido ou encontrou um conflito.' : 'Não foi possível registrar a reversão.')
      reversalIdempotency.current.delete(mapKey)
      setMessage('Reversão registrada com motivo e movimento de caixa preservados.')
      restoreTitlesFocusAfterReversalClose.current = true
      setReversingSettlementId(null); setSettlementHistory(null); setTitlesLoadedKey(''); setTitlesRefresh((value) => value + 1); setAccountsLoadedKey(''); setAccountsRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao registrar reversão.') }
    finally { setBusy(false) }
  }

  async function submitCashTransaction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setMessage('')
    const data = new FormData(event.currentTarget)
    try {
      const amountMinor = parseBrlToMinor(String(data.get('amount') ?? ''))
      if (BigInt(amountMinor) <= 0n) throw new Error('O valor precisa ser maior que zero.')
      const payload = { direction: String(data.get('direction') ?? ''), accountId: String(data.get('accountId') ?? ''), categoryId: String(data.get('categoryId') ?? ''), amountMinor, effectiveDate: String(data.get('effectiveDate') ?? ''), description: String(data.get('description') ?? '').trim() }
      const signature = JSON.stringify(payload)
      if (!cashTransactionIdempotency.current || cashTransactionIdempotency.current.signature !== signature) cashTransactionIdempotency.current = { key: crypto.randomUUID(), signature }
      await refreshCsrf()
      const response = await fetch('/api/cash-transactions', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? ''), 'Idempotency-Key': cashTransactionIdempotency.current.key }, body: JSON.stringify(payload) })
      if (!response.ok) throw new Error(response.status === 409 ? 'Esta tentativa encontrou um conflito de idempotência.' : 'Não foi possível registrar o movimento. Confira conta, categoria e valor.')
      cashTransactionIdempotency.current = null
      setMessage(response.status === 200 ? 'O movimento anterior foi recuperado com segurança.' : 'Movimento registrado no saldo controlado.')
      requestTransactionsFocusReturn()
      setShowCashTransactionForm(false); setCashTransactionsPage(0); setCashTransactionsLoaded(false); setCashTransactionsRefresh((value) => value + 1); setAccountsLoadedKey(''); setAccountsRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao registrar movimento.') }
    finally { setBusy(false) }
  }

  async function logout() {
    setBusy(true)
    try {
      await refreshCsrf()
      const response = await fetch('/api/auth/logout', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrfCookie() ?? '') },
      })
      if (!response.ok) throw new Error('Não foi possível encerrar a sessão. Tente novamente.')
      setAuthenticatedEmail('')
      setCompanyName('')
      setContacts({ items: [], total: 0, page: 0, size: 20 })
      setContactsLoadedKey('')
      setAccounts({ items: [], total: 0, page: 0, size: 20 })
      setAccountsLoadedKey('')
      setAccountsPage(0)
      setCategories({ items: [], total: 0, page: 0, size: 20 })
      setCategoriesLoaded(false)
      setTitles({ items: [], total: 0, page: 0, size: 20, asOf: '' })
      setCashTransactions({ items: [], total: 0, page: 0, size: 20 })
      setCashTransactionsPage(0)
      setCashTransactionsLoaded(false)
      setCashTransactionOptions(null)
      setShowCashTransactionForm(false)
      setTitlesPage(0)
      setTitlesQuery('')
      setTitlesLoadedKey('')
      setTitleOptions(null)
      setShowTitleForm(false)
      setEditingTitle(null)
      setActiveSection('contacts')
      setMode('login')
      setMessage('Você saiu da sua conta.')
      await refreshCsrf()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao encerrar a sessão.')
    } finally {
      setBusy(false)
    }
  }

  function editContact(contact: Contact) {
    setEditing(contact)
    setShowContactForm(true)
  }

  function localDateToday() {
    const date = new Date()
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  }

  const isDemoEnvironment = companyName === 'FlowPay Demonstração'

  return (
    <main className={`page-shell${authenticatedEmail ? ' page-shell--workspace' : ''}`}>
      <header className="topbar">
        <a className="brand" href="/">
          <span className="brand-mark" aria-hidden="true">F</span><span>flowpay</span>
        </a>
        {authenticatedEmail
          ? <div className="topbar-account"><span className="environment-label">{isDemoEnvironment ? 'Ambiente de demonstração' : companyName}</span><button type="button" className="topbar-logout" onClick={logout} disabled={busy}>Sair</button></div>
          : <span className="environment-label">Ambiente local</span>}
      </header>

      {authenticatedEmail ? <>
        <div className="workspace-layout">
          <aside className="sidebar" aria-label="Navegação principal">
            <p className="sidebar-label">Empresa</p>
            <button type="button" className={`nav-link${activeSection === 'dashboard' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'dashboard' ? 'page' : undefined} onClick={() => setActiveSection('dashboard')}>Visão geral</button>
            <button type="button" className={`nav-link${activeSection === 'reports' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'reports' ? 'page' : undefined} onClick={() => setActiveSection('reports')}>Relatório mensal</button>
            <button type="button" className={`nav-link${activeSection === 'contacts' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'contacts' ? 'page' : undefined} onClick={() => setActiveSection('contacts')}>Contatos</button>
            <button type="button" className={`nav-link${activeSection === 'accounts' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'accounts' ? 'page' : undefined} onClick={() => setActiveSection('accounts')}>Contas financeiras</button>
            <button type="button" className={`nav-link${activeSection === 'categories' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'categories' ? 'page' : undefined} onClick={() => setActiveSection('categories')}>Categorias</button>
            <button type="button" className={`nav-link${activeSection === 'transactions' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'transactions' ? 'page' : undefined} onClick={() => setActiveSection('transactions')}>Movimentos de caixa</button>
            <button type="button" className={`nav-link${activeSection === 'statements' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'statements' ? 'page' : undefined} onClick={() => setActiveSection('statements')}>Extratos</button>
            <button type="button" className={`nav-link${activeSection === 'receivables' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'receivables' ? 'page' : undefined} onClick={() => { setTitlesPage(0); setActiveSection('receivables') }}>A receber</button>
            <button type="button" className={`nav-link${activeSection === 'payables' ? ' nav-link--active' : ''}`} aria-current={activeSection === 'payables' ? 'page' : undefined} onClick={() => { setTitlesPage(0); setActiveSection('payables') }}>A pagar</button>
          </aside>
          {activeSection === 'dashboard' ? <Dashboard companyName={companyName} onNewMovement={() => { setActiveSection('transactions'); setShowCashTransactionForm(true) }} /> : activeSection === 'reports' ? <MonthlyReport companyName={companyName} /> : activeSection === 'statements' ? <StatementImportPreview companyName={companyName} /> : activeSection === 'contacts' ? <section className="workspace-content" id="contacts" aria-labelledby="contacts-title">
            <div className="workspace-heading">
              <div><p className="eyebrow">{companyName}</p><h1 id="contacts-title" ref={contactsHeadingRef} tabIndex={-1}>Clientes e fornecedores</h1><p className="welcome-copy">Mantenha os dados de quem faz parte da rotina da sua empresa.</p></div>
              <button className="submit-button new-contact-button" type="button" onClick={() => { setEditing(null); setShowContactForm((value) => !value); setMessage('') }}>{showContactForm ? 'Fechar formulário' : 'Novo contato'}</button>
            </div>

            {showContactForm && <section className="contact-form-card" aria-labelledby="contact-form-title">
              <h2 id="contact-form-title">{editing ? 'Editar contato' : 'Novo contato'}</h2>
              <form className="contact-form" onSubmit={submitContact} key={editing?.id ?? 'new'}>
                <label className="field--wide">Nome<input name="name" required maxLength={160} defaultValue={editing?.name ?? ''} autoFocus /></label>
                <fieldset className="role-field"><legend>Tipo de contato</legend>
                  <label><input name="roles" type="checkbox" value="CUSTOMER" defaultChecked={editing?.roles.includes('CUSTOMER') ?? false} /> Cliente</label>
                  <label><input name="roles" type="checkbox" value="SUPPLIER" defaultChecked={editing?.roles.includes('SUPPLIER') ?? false} /> Fornecedor</label>
                </fieldset>
                <label>E-mail<input name="email" type="email" maxLength={254} defaultValue={editing?.email ?? ''} /></label>
                <label>Telefone<input name="phone" type="tel" maxLength={32} defaultValue={editing?.phone ?? ''} /></label>
                <label className="field--wide">Observação<textarea name="notes" rows={3} maxLength={1000} defaultValue={editing?.notes ?? ''} /></label>
                <div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy}>{busy ? 'Salvando…' : editing ? 'Salvar alterações' : 'Cadastrar contato'}</button><button className="refresh-button" type="button" onClick={() => { requestContactsFocusReturn(); setShowContactForm(false); setEditing(null) }}>Cancelar</button></div>
              </form>
            </section>}

            <div className="contact-toolbar"><label>Buscar contatos<input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Nome, e-mail ou telefone" /></label><span>{contacts.total} {contacts.total === 1 ? 'contato' : 'contatos'}</span></div>
            {message && <p className="form-message" role="status">{message}</p>}
            {contactsLoading ? <p className="empty-state" role="status">Carregando contatos…</p> : contacts.items.length === 0
              ? <div className="empty-state"><h2>{query ? 'Nenhum contato encontrado' : 'Sua lista está vazia'}</h2><p>{query ? 'Tente outro nome, e-mail ou telefone.' : 'Cadastre seu primeiro cliente ou fornecedor para começar.'}</p>{!query && <button type="button" className="text-action" onClick={() => setShowContactForm(true)}>Cadastrar primeiro contato</button>}</div>
              : <div className="contact-list" aria-live="polite">{contacts.items.map((contact) => <article className="contact-row" key={contact.id}>
                <div className="contact-main"><h2>{contact.name}</h2><div className="contact-badges">{contact.roles.map((role) => <span className="contact-badge" key={role}>{role === 'CUSTOMER' ? 'Cliente' : 'Fornecedor'}</span>)}</div></div>
                <div className="contact-detail">{contact.email && <span>{contact.email}</span>}{contact.phone && <span>{contact.phone}</span>}{contact.notes && <span className="contact-note">{contact.notes}</span>}</div>
                <button type="button" className="edit-button" onClick={() => editContact(contact)} aria-label={`Editar ${contact.name}`}>Editar</button>
              </article>)}</div>}
            <div className="pagination"><span>Página {contacts.page + 1} de {Math.max(1, Math.ceil(contacts.total / contacts.size))}</span><div><button type="button" className="refresh-button" disabled={page === 0 || contactsLoading} onClick={() => setPage((value) => value - 1)}>Anterior</button><button type="button" className="refresh-button" disabled={(page + 1) * contacts.size >= contacts.total || contactsLoading} onClick={() => setPage((value) => value + 1)}>Próxima</button></div></div>
          </section> : activeSection === 'accounts' ? <section className="workspace-content" id="accounts" aria-labelledby="accounts-title">
            <div className="workspace-heading"><div><p className="eyebrow">{companyName}</p><h1 id="accounts-title" ref={accountsHeadingRef} tabIndex={-1}>Contas financeiras</h1><p className="welcome-copy">Acompanhe o saldo controlado da sua empresa.</p></div><button className="submit-button new-contact-button" type="button" onClick={() => { setEditingAccount(null); setShowAccountForm((value) => !value); setMessage('') }}>{showAccountForm ? 'Fechar formulário' : 'Nova conta'}</button></div>
            {showAccountForm && <section className="contact-form-card" aria-labelledby="account-form-title"><h2 id="account-form-title">{editingAccount ? 'Editar conta' : 'Nova conta financeira'}</h2><form className="contact-form" onSubmit={submitAccount} key={editingAccount?.id ?? 'new-account'}><label className="field--wide">Nome da conta<input name="name" required maxLength={120} defaultValue={editingAccount?.name ?? ''} autoFocus /></label>{editingAccount ? <label>Status<select name="status" defaultValue={editingAccount.status}><option value="ACTIVE">Ativa</option><option value="ARCHIVED">Arquivada</option></select></label> : <><label>Tipo de conta<select name="type" required defaultValue="BANK"><option value="BANK">Conta bancária</option><option value="CASH">Caixa</option><option value="OTHER">Outra conta</option></select></label><label>Saldo inicial em reais<input name="openingBalance" inputMode="decimal" placeholder="0,00" required /></label><label>Data do saldo inicial<input name="openingBalanceDate" type="date" defaultValue={localDateToday()} required /></label><p className="field--wide account-hint">Use centavos, se necessário. Saldo negativo representa uma abertura a descoberto.</p></>}<div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy}>{busy ? 'Salvando…' : 'Salvar conta'}</button><button className="refresh-button" type="button" onClick={() => { requestAccountsFocusReturn(); setShowAccountForm(false); setEditingAccount(null) }}>Cancelar</button></div></form></section>}
            {message && <p className="form-message" role="status">{message}</p>}{accountsLoading ? <p className="empty-state" role="status">Carregando contas…</p> : accounts.items.length === 0 ? <div className="empty-state"><h2>Sua empresa ainda não tem contas</h2><p>Cadastre uma conta para acompanhar o saldo inicial e os próximos movimentos.</p><button type="button" className="text-action" onClick={() => setShowAccountForm(true)}>Cadastrar primeira conta</button></div> : <div className="contact-list account-list" aria-live="polite">{accounts.items.map((account) => <article className="contact-row account-row" key={account.id}><div className="contact-main"><h2>{account.name}</h2><div className="contact-badges"><span className="contact-badge">{accountTypeLabel(account.type)}</span><span className={`account-status account-status--${account.status.toLowerCase()}`}>{account.status === 'ACTIVE' ? 'Ativa' : 'Arquivada'}</span></div></div><div className="account-balance"><strong>{formatMinor(account.balanceMinor)}</strong><span>Saldo controlado · abertura em {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' }).format(new Date(`${account.openingBalanceDate}T00:00:00Z`))}</span></div><div className="account-actions"><button type="button" className="edit-button" disabled={busy} onClick={() => { setEditingAccount(account); setShowAccountForm(true); setMessage('') }}>Editar</button><button type="button" className="edit-button" disabled={busy} onClick={() => updateAccountStatus(account)}>{account.status === 'ACTIVE' ? 'Arquivar' : 'Ativar'}</button></div></article>)}</div>}{accounts.total > 20 && <div className="pagination"><span>Página {accounts.page + 1} de {Math.ceil(accounts.total / accounts.size)}</span><div><button type="button" className="refresh-button" disabled={accountsPage === 0 || accountsLoading} onClick={() => setAccountsPage((value) => value - 1)}>Anterior</button><button type="button" className="refresh-button" disabled={(accountsPage + 1) * accounts.size >= accounts.total || accountsLoading} onClick={() => setAccountsPage((value) => value + 1)}>Próxima</button></div></div>}
          </section> : activeSection === 'categories' ? <section className="workspace-content" id="categories" aria-labelledby="categories-title">
            <div className="workspace-heading"><div><p className="eyebrow">{companyName}</p><h1 id="categories-title" ref={categoriesHeadingRef} tabIndex={-1}>Categorias</h1><p className="welcome-copy">Organize entradas e saídas para os próximos lançamentos.</p></div><button className="submit-button new-contact-button" type="button" onClick={() => { setEditingCategory(null); setShowCategoryForm((value) => !value); setMessage('') }}>{showCategoryForm ? 'Fechar formulário' : 'Nova categoria'}</button></div>
            {showCategoryForm && <section className="contact-form-card" aria-labelledby="category-form-title"><h2 id="category-form-title">{editingCategory ? 'Editar categoria' : 'Nova categoria'}</h2><form className="contact-form" onSubmit={submitCategory} key={editingCategory?.id ?? 'new-category'}><label className="field--wide">Nome<input name="name" required maxLength={100} defaultValue={editingCategory?.name ?? ''} autoFocus /></label>{editingCategory ? <label>Status<select name="status" defaultValue={editingCategory.status}><option value="ACTIVE">Ativa</option><option value="ARCHIVED">Arquivada</option></select></label> : <label>Tipo<select name="direction" required defaultValue="OUTFLOW"><option value="OUTFLOW">Despesa / saída</option><option value="INFLOW">Receita / entrada</option></select></label>}<div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy}>{busy ? 'Salvando…' : 'Salvar categoria'}</button><button className="refresh-button" type="button" onClick={() => { requestCategoriesFocusReturn(); setShowCategoryForm(false); setEditingCategory(null) }}>Cancelar</button></div></form></section>}
            {message && <p className="form-message" role="status">{message}</p>}{categoriesLoading ? <p className="empty-state" role="status">Carregando categorias…</p> : categories.items.length === 0 ? <div className="empty-state"><h2>Suas categorias ainda não foram criadas</h2><p>Comece por uma categoria de despesa ou receita.</p><button type="button" className="text-action" onClick={() => setShowCategoryForm(true)}>Criar primeira categoria</button></div> : <div className="contact-list category-list" aria-live="polite">{categories.items.map((category) => <article className="contact-row category-row" key={category.id}><div className="contact-main"><h2>{category.name}</h2><div className="contact-badges"><span className={`category-direction category-direction--${category.direction.toLowerCase()}`}>{category.direction === 'INFLOW' ? 'Receita / entrada' : 'Despesa / saída'}</span><span className={`account-status account-status--${category.status.toLowerCase()}`}>{category.status === 'ACTIVE' ? 'Ativa' : 'Arquivada'}</span></div></div><button type="button" className="edit-button" disabled={busy} onClick={() => { setEditingCategory(category); setShowCategoryForm(true); setMessage('') }}>Editar</button></article>)}</div>}
          </section> : activeSection === 'transactions' ? <section className="workspace-content" id="transactions" aria-labelledby="transactions-title">
            <div className="workspace-heading"><div><p className="eyebrow">{companyName}</p><h1 id="transactions-title" ref={transactionsHeadingRef} tabIndex={-1}>Movimentos de caixa</h1><p className="welcome-copy">Acompanhe entradas e saídas que alteram o saldo controlado, inclusive as geradas pelas baixas.</p></div><button className="submit-button new-contact-button" type="button" onClick={() => { setShowCashTransactionForm((value) => !value); setMessage('') }}>{showCashTransactionForm ? 'Fechar formulário' : 'Novo movimento'}</button></div>
            {message && <p className="form-message" role="status">{message}</p>}
            {showCashTransactionForm && !cashTransactionOptions && <p className="empty-state" role="status">Carregando contas e categorias…</p>}
            {showCashTransactionForm && cashTransactionOptions && <section className="contact-form-card" aria-labelledby="cash-transaction-form-title"><h2 id="cash-transaction-form-title">Novo movimento avulso</h2><p className="account-hint">Use quando o movimento já ocorreu e não corresponde a uma baixa de título. Esta ação altera o saldo controlado.</p><form className="contact-form" onSubmit={submitCashTransaction} key={cashTransactionDirection}><label className="field--wide">Descrição<input name="description" required maxLength={160} autoFocus /></label><label>Tipo<select name="direction" value={cashTransactionDirection} onChange={(event) => setCashTransactionDirection(event.target.value as 'INFLOW' | 'OUTFLOW')}><option value="OUTFLOW">Saída</option><option value="INFLOW">Entrada</option></select></label><label>Valor em reais<input name="amount" inputMode="decimal" placeholder="0,00" required /></label><label>Conta<select name="accountId" required defaultValue=""><option value="" disabled>Selecione</option>{cashTransactionOptions.accounts.filter((account) => account.status === 'ACTIVE').map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label><label>Categoria<select name="categoryId" required defaultValue=""><option value="" disabled>Selecione</option>{cashTransactionOptions.categories.filter((category) => category.status === 'ACTIVE' && category.direction === cashTransactionDirection).map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label><label>Data realizada<input name="effectiveDate" type="date" required defaultValue={localDateToday()} /></label><div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy || !cashTransactionOptions.accounts.some((account) => account.status === 'ACTIVE') || !cashTransactionOptions.categories.some((category) => category.status === 'ACTIVE' && category.direction === cashTransactionDirection)}>{busy ? 'Registrando…' : 'Registrar movimento'}</button><button className="refresh-button" type="button" onClick={() => { requestTransactionsFocusReturn(); setShowCashTransactionForm(false) }}>Cancelar</button></div></form></section>}
            {cashTransactionsLoading ? <p className="empty-state" role="status">Carregando movimentos…</p> : cashTransactions.items.length === 0 ? <div className="empty-state"><h2>Ainda não há movimentos</h2><p>Registre uma entrada ou saída já realizada. A criação de movimento avulso não quita títulos.</p></div> : <div className="contact-list cash-transaction-list" aria-live="polite">{cashTransactions.items.map((transaction) => <article className="contact-row cash-transaction-row" key={transaction.id}><div className="contact-main"><h2>{transaction.description}</h2><span className="title-contact">{transaction.accountName} · {transaction.categoryName ?? 'Sem categoria'}</span></div><div className="title-amount"><strong>{formatMinor(transaction.amountMinor)}</strong><span>{transaction.direction === 'INFLOW' ? 'Entrada' : 'Saída'} · {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(transaction.effectiveDate + 'T00:00:00Z'))}</span></div><span className="contact-badge">{transaction.origin === 'OPENING_BALANCE' ? 'Saldo inicial' : transaction.origin === 'TITLE_SETTLEMENT' ? 'Baixa de título' : transaction.origin === 'SETTLEMENT_REVERSAL' ? 'Reversão de baixa' : 'Avulso'}</span></article>)}</div>}
            {cashTransactions.total > cashTransactions.size && <div className="pagination"><span>Página {cashTransactions.page + 1} de {Math.ceil(cashTransactions.total / cashTransactions.size)}</span><div><button type="button" className="refresh-button" disabled={cashTransactionsPage === 0 || cashTransactionsLoading} onClick={() => { setCashTransactionsLoaded(false); setCashTransactionsPage((value) => value - 1) }}>Anterior</button><button type="button" className="refresh-button" disabled={(cashTransactionsPage + 1) * cashTransactions.size >= cashTransactions.total || cashTransactionsLoading} onClick={() => { setCashTransactionsLoaded(false); setCashTransactionsPage((value) => value + 1) }}>Próxima</button></div></div>}
          </section> : <section className="workspace-content" id="titles" aria-labelledby="titles-title">
            <div className="workspace-heading"><div><p className="eyebrow">{companyName}</p><h1 id="titles-title" ref={titlesHeadingRef} tabIndex={-1}>{titleKind === 'RECEIVABLE' ? 'Contas a receber' : 'Contas a pagar'}</h1><p className="welcome-copy">Acompanhe valores, contatos e vencimentos previstos.</p></div><button className="submit-button new-contact-button" type="button" onClick={() => { setEditingTitle(null); setShowTitleForm((value) => !value); setMessage('') }}>{showTitleForm ? 'Fechar formulário' : 'Novo título'}</button></div>
            {showTitleForm && <section className="contact-form-card" aria-labelledby="title-form-title"><h2 id="title-form-title">{editingTitle ? 'Editar título' : titleKind === 'RECEIVABLE' ? 'Novo valor a receber' : 'Nova conta a pagar'}</h2>{titleOptionsLoading ? <p role="status">Carregando contatos, categorias e contas…</p> : <form className="contact-form" onSubmit={submitTitle} key={editingTitle?.id ?? titleKind}><label className="field--wide">Descrição<input name="description" required maxLength={180} defaultValue={editingTitle?.description ?? ''} autoFocus /></label><label>Contato<select name="contactId" required defaultValue={editingTitle?.contactId ?? ''}><option value="" disabled>Selecione</option>{titleOptions?.contacts.filter((contact) => contact.roles.includes(titleKind === 'RECEIVABLE' ? 'CUSTOMER' : 'SUPPLIER')).map((contact) => <option value={contact.id} key={contact.id}>{contact.name}</option>)}</select></label><label>Categoria<select name="categoryId" required defaultValue={editingTitle?.categoryId ?? ''}><option value="" disabled>Selecione</option>{titleOptions?.categories.filter((category) => category.status === 'ACTIVE' && category.direction === (titleKind === 'RECEIVABLE' ? 'INFLOW' : 'OUTFLOW')).map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label>{titleOptions && !titleOptions.contacts.some((contact) => contact.roles.includes(titleKind === 'RECEIVABLE' ? 'CUSTOMER' : 'SUPPLIER')) && <p className="field--wide title-hint">Cadastre primeiro um {titleKind === 'RECEIVABLE' ? 'cliente' : 'fornecedor'} em Contatos.</p>}{titleOptions && !titleOptions.categories.some((category) => category.status === 'ACTIVE' && category.direction === (titleKind === 'RECEIVABLE' ? 'INFLOW' : 'OUTFLOW')) && <p className="field--wide title-hint">Cadastre uma categoria de {titleKind === 'RECEIVABLE' ? 'receita' : 'despesa'} antes de criar o título.</p>}<label>Valor em reais<input name="amount" inputMode="decimal" placeholder="0,00" required defaultValue={editingTitle ? formatMinor(editingTitle.originalAmountMinor).replace('R$ ', '') : ''} /></label><label>Vencimento<input name="dueDate" type="date" required defaultValue={editingTitle?.dueDate ?? localDateToday()} /></label><label className="field--wide">Conta prevista (opcional)<select name="plannedAccountId" defaultValue={editingTitle?.plannedAccountId ?? ''}><option value="">Sem conta definida</option>{titleOptions?.accounts.filter((account) => account.status === 'ACTIVE').map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label><label className="field--wide">Observação<textarea name="notes" rows={3} maxLength={1000} defaultValue={editingTitle?.notes ?? ''} /></label><div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy || !titleOptions?.contacts.some((contact) => contact.roles.includes(titleKind === 'RECEIVABLE' ? 'CUSTOMER' : 'SUPPLIER')) || !titleOptions?.categories.some((category) => category.status === 'ACTIVE' && category.direction === (titleKind === 'RECEIVABLE' ? 'INFLOW' : 'OUTFLOW'))}>{busy ? 'Salvando…' : 'Salvar título'}</button><button className="refresh-button" type="button" onClick={() => { requestTitlesFocusReturn(); setShowTitleForm(false); setEditingTitle(null) }}>Cancelar</button></div></form>}</section>}
            <div className="contact-toolbar"><label>Buscar títulos<input type="search" value={titlesQuery} onChange={(event) => { setTitlesQuery(event.target.value); setTitlesPage(0) }} placeholder="Descrição ou contato" /></label><span>{titles.total} {titles.total === 1 ? 'título' : 'títulos'} · previsto, não realizado</span></div>{message && <p className="form-message" role="status">{message}</p>}
            {titlesLoading ? <p className="empty-state" role="status">Carregando títulos…</p> : titles.items.length === 0 ? <div className="empty-state"><h2>{titlesQuery ? 'Nenhum título encontrado' : 'Ainda não há títulos nesta lista'}</h2><p>{titlesQuery ? 'Tente outra descrição ou contato.' : 'Cadastre um valor previsto; isso não registra recebimento nem pagamento.'}</p>{!titlesQuery && <button type="button" className="text-action" onClick={() => setShowTitleForm(true)}>Cadastrar primeiro título</button>}</div> : <div className="contact-list title-list" aria-live="polite">{titles.items.map((title) => <article className="contact-row title-row" key={title.id}><div className="contact-main"><h2>{title.description}</h2><div className="contact-badges"><span className={`account-status title-status--${title.status.toLowerCase()}`}>{title.status === 'OPEN' ? 'Em aberto' : title.status === 'OVERDUE' ? 'Vencido' : title.status === 'PARTIALLY_SETTLED' ? 'Parcialmente pago' : title.status === 'SETTLED' ? 'Quitado' : 'Cancelado'}</span><span className="contact-badge">{title.categoryName}</span></div><span className="title-contact">{title.contactName}{title.plannedAccountName ? ` · ${title.plannedAccountName}` : ''}</span></div><div className="title-amount"><strong>{formatMinor(title.remainingAmountMinor)} em aberto</strong><span>Original: {formatMinor(title.originalAmountMinor)} · vencimento {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(`${title.dueDate}T00:00:00Z`))}</span></div><div className="account-actions">{title.status !== 'CANCELLED' && <button type="button" className="edit-button" disabled={busy} onClick={() => { setSettlingTitleId(settlingTitleId === title.id ? null : title.id); setSettlementHistory(null); setEditingTitle(null); setShowTitleForm(false); setMessage('') }}>{title.settledAmountMinor !== '0' ? 'Ver baixas / reverter' : title.kind === 'RECEIVABLE' ? 'Registrar recebimento' : 'Registrar pagamento'}</button>}<button type="button" className="edit-button" disabled={busy || title.status !== 'OPEN' && title.status !== 'OVERDUE'} onClick={() => { setEditingTitle(title); setShowTitleForm(true); setMessage('') }}>Editar</button>{(title.status === 'OPEN' || title.status === 'OVERDUE') && <button type="button" className="edit-button" disabled={busy} onClick={() => cancelTitle(title)}>Cancelar</button>}</div>{settlingTitleId === title.id && titleOptions && title.status !== 'SETTLED' && <form className="contact-form settlement-form" onSubmit={(event) => submitSettlement(event, title)}><p className="field--wide account-hint">{title.kind === 'RECEIVABLE' ? 'Registre aqui o recebimento realizado fora do FlowPay.' : 'Registre aqui o pagamento realizado fora do FlowPay.'} A baixa atualiza o saldo controlado.</p><label>Valor em reais<input name="amount" inputMode="decimal" autoFocus required defaultValue={formatMinor(title.remainingAmountMinor).replace('R$ ', '')} /></label><label>Conta<select name="accountId" required defaultValue={title.plannedAccountId ?? ''}><option value="" disabled>Selecione</option>{titleOptions.accounts.filter((account) => account.status === 'ACTIVE').map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label><label>Data realizada<input name="effectiveDate" type="date" required defaultValue={localDateToday()} /></label><div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy || !titleOptions.accounts.some((account) => account.status === 'ACTIVE')}>{busy ? 'Registrando…' : title.kind === 'RECEIVABLE' ? 'Confirmar recebimento' : 'Confirmar pagamento'}</button><button className="refresh-button" type="button" onClick={() => { restoreTitlesFocusAfterSettlementClose.current = true; setSettlingTitleId(null) }}>Voltar</button></div></form>}{settlingTitleId === title.id && <section className="settlement-history field--wide" aria-label="Baixas e reversões"><h3>Baixas registradas</h3>{settlementHistory?.titleId !== title.id ? <p role="status">Carregando baixas…</p> : settlementHistory.items.length === 0 ? <p>Nenhuma baixa registrada.</p> : settlementHistory.items.map((settlement) => <div className="settlement-history-row" key={settlement.id}><span>{formatMinor(settlement.amountMinor)} em {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(settlement.effectiveDate + 'T00:00:00Z'))}</span><span>{formatMinor(settlement.remainingAmountMinor)} ainda reversível</span>{settlement.remainingAmountMinor !== '0' && <button type="button" className="edit-button" disabled={busy} onClick={() => setReversingSettlementId(reversingSettlementId === settlement.id ? null : settlement.id)}>Reverter</button>}{reversingSettlementId === settlement.id && <form className="contact-form settlement-reversal-form" onSubmit={(event) => submitReversal(event, title, settlement)}><label>Valor a reverter<input name="amount" inputMode="decimal" autoFocus required defaultValue={formatMinor(settlement.remainingAmountMinor).replace('R$ ', '')} /></label><label>Data da reversão<input name="effectiveDate" type="date" required defaultValue={localDateToday()} /></label><label className="field--wide">Motivo<textarea name="reason" rows={2} maxLength={500} required /></label><div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy}>{busy ? 'Registrando…' : 'Confirmar reversão'}</button><button className="refresh-button" type="button" onClick={() => { restoreTitlesFocusAfterReversalClose.current = true; setReversingSettlementId(null) }}>Voltar</button></div></form>}</div>)}</section>}{title.status === 'CANCELLED' && title.cancellationReason && <p className="title-cancel-reason">Motivo: {title.cancellationReason}</p>}</article>)}</div>}{titles.total > titles.size && <div className="pagination"><span>Página {titles.page + 1} de {Math.ceil(titles.total / titles.size)}</span><div><button type="button" className="refresh-button" disabled={titlesPage === 0 || titlesLoading} onClick={() => setTitlesPage((value) => value - 1)}>Anterior</button><button type="button" className="refresh-button" disabled={(titlesPage + 1) * titles.size >= titles.total || titlesLoading} onClick={() => setTitlesPage((value) => value + 1)}>Próxima</button></div></div>}
          </section>}
        </div>
      </> : <section className="auth-layout"
 aria-labelledby="page-title">
        <div className="welcome"><p className="eyebrow">Gestão financeira</p><h1 id="page-title">Seu financeiro, com clareza.</h1><p className="welcome-copy">Organize as finanças da sua empresa em um só lugar.</p></div>
        <section className="auth-card" aria-labelledby="auth-title">
          <h2 id="auth-title">{mode === 'register' ? 'Crie sua empresa' : 'Acesse sua conta'}</h2>
          <p className="auth-intro">{mode === 'register' ? 'Você será o proprietário inicial da empresa.' : 'Entre com seu e-mail e senha.'}</p>
          <form onSubmit={submitAuth}>
            {mode === 'register' && <label>Nome da empresa<input name="companyName" autoComplete="organization" required maxLength={120} /></label>}
            <label>E-mail<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>
            <label>Senha<input name="password" type="password" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required minLength={mode === 'register' ? 12 : 1} maxLength={72} /></label>
            {mode === 'register' && <p className="password-hint">Use pelo menos 12 caracteres. Limite de 72 bytes em UTF-8.</p>}
            <button className="submit-button" type="submit" disabled={busy}>{busy ? 'Aguarde…' : mode === 'register' ? 'Criar empresa' : 'Entrar'}</button>
          </form>
          <p className="auth-switch">{mode === 'register' ? 'Já tem uma conta?' : 'Primeiro acesso?'}{' '}<button type="button" onClick={() => { setMode(mode === 'register' ? 'login' : 'register'); setMessage('') }}>{mode === 'register' ? 'Entrar' : 'Criar empresa'}</button></p>
          {message && <p className="form-message" role="status">{message}</p>}
        </section>
      </section>}

      <footer className="page-footer"><span>FlowPay</span><span>Pagamentos são registrados fora do FlowPay.</span></footer>
    </main>
  )
}

export default App

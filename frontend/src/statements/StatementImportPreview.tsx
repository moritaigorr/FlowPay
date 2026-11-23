import { useEffect, useRef, useState } from 'react'
import { formatLocalDate, formatMinor } from '../finance/format'

type Account = { id: string; name: string; status: 'ACTIVE' | 'ARCHIVED' }
type AccountPage = { items: Account[] }
type PreviewRow = {
  rowNumber: number
  date: string | null
  description: string
  amountMinor: string | null
  direction: 'INFLOW' | 'OUTFLOW' | null
  duplicateInFile: boolean
  errors: { column: string; message: string }[]
}
type Preview = { acceptedCount: number; rejectedCount: number; duplicateCount: number; rows: PreviewRow[] }
type Confirmation = { importId: string; replay: boolean; totalRows: number; acceptedCount: number; rejectedCount: number; duplicateCount: number }
type ImportSummary = { id: string; accountId: string; createdAt: string; totalRows: number; acceptedCount: number; rejectedCount: number; duplicateCount: number }
type ImportPage = { items: ImportSummary[]; total: number; page: number; size: number }
type ReconciliationSummary = { importId: string; financialAccountId: string; periodFrom: string | null; periodTo: string | null; timezone: string; asOf: string | null; acceptedCount: number; rejectedCount: number; duplicateCount: number; matchedCount: number; statementInflowsMinor: string; statementOutflowsMinor: string; controlledInflowsMinor: string; controlledOutflowsMinor: string; controlledBalanceMinor: string | null; inflowActivityDifferenceMinor: string | null; outflowActivityDifferenceMinor: string | null; netActivityDifferenceMinor: string | null; statementBalanceAvailable: boolean }
type SavedLine = { id: string; rowNumber: number; date: string | null; description: string; amountMinor: string | null; direction: 'INFLOW' | 'OUTFLOW' | null; status: 'ACCEPTED' | 'REJECTED' | 'DUPLICATE'; validationErrors: string; reviewStatus: 'PENDING' | 'REVIEWED' | 'IGNORED'; reviewReason: string | null; matchId: string | null }
type LinePage = { items: SavedLine[]; total: number; page: number; size: number }
type Category = { id: string; name: string; direction: 'INFLOW' | 'OUTFLOW'; status: 'ACTIVE' | 'ARCHIVED' }
type CategoryPage = { items: Category[] }
type Suggestion = {
  movement: { id: string; direction: 'INFLOW' | 'OUTFLOW'; amountMinor: string; effectiveDate: string; origin: string; description: string }
  score: number
  dateDistanceDays: number
  evidence: string[]
}
type IgnoredCandidate = { suppressionId: string; movement: Suggestion['movement']; reason: string; ignoredAt: string }
type SuggestionList = { statementLineId: string; suggestions: Suggestion[]; candidateCount: number; candidateSetTruncated: boolean; ignoredCandidates: IgnoredCandidate[]; explanation: string }
type MatchDecision = { decision: 'LINK_EXISTING'; cashTransactionId: string } | { decision: 'CREATE_MOVEMENT'; categoryId: string }
type MatchResult = { matchId: string; statementLineId: string; cashTransactionId: string; decision: string; replay: boolean; active: boolean }
type MatchStatus = { statementLineId: string; matchId: string; decision: string }

function originLabel(origin: string) {
  return ({ MANUAL: 'Manual', TITLE_SETTLEMENT: 'Baixa de título', SETTLEMENT_REVERSAL: 'Estorno de baixa', STATEMENT_RECONCILIATION: 'Conciliação' } as Record<string, string>)[origin] ?? origin
}

function csrfToken() {
  const value = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=')
  return value ? decodeURIComponent(value) : ''
}

export function StatementImportPreview({ companyName }: { companyName: string }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [imports, setImports] = useState<ImportPage>({ items: [], total: 0, page: 0, size: 20 })
  const [importsPage, setImportsPage] = useState(0)
  const [importsRefresh, setImportsRefresh] = useState(0)
  const [selectedImportId, setSelectedImportId] = useState('')
  const [savedLines, setSavedLines] = useState<LinePage | null>(null)
  const [reconciliationSummary, setReconciliationSummary] = useState<ReconciliationSummary | null>(null)
  const [summaryError, setSummaryError] = useState('')
  const [savedLinesPage, setSavedLinesPage] = useState(0)
  const [suggestionLineId, setSuggestionLineId] = useState('')
  const [suggestions, setSuggestions] = useState<SuggestionList | null>(null)
  const [suggestionBusy, setSuggestionBusy] = useState(false)
  const [selectedLine, setSelectedLine] = useState<SavedLine | null>(null)
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [createCategoryId, setCreateCategoryId] = useState('')
  const [pendingDecision, setPendingDecision] = useState<MatchDecision | null>(null)
  const [pendingMovement, setPendingMovement] = useState<Suggestion['movement'] | null>(null)
  const [matchBusy, setMatchBusy] = useState(false)
  const [linesRefresh, setLinesRefresh] = useState(0)
  const matchIdempotencyKeys = useRef(new Map<string, string>())
  const reviewKeys = useRef(new Map<string, string>())
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo'

  useEffect(() => {
    let active = true
    fetch('/api/financial-accounts?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar as contas financeiras.')
        return await response.json() as AccountPage
      })
      .then((result) => { if (active) setAccounts(result.items.filter((account) => account.status === 'ACTIVE')) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar contas.') })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    fetch('/api/financial-categories?page=0&size=100', { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar as categorias financeiras.')
        return await response.json() as CategoryPage
      })
      .then((result) => { if (active) setCategories(result.items) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar categorias.') })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!accountId) return
    let active = true
    fetch(`/api/statement-imports?accountId=${encodeURIComponent(accountId)}&page=${importsPage}&size=20`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar o histórico de extratos.')
        return await response.json() as ImportPage
      })
      .then((result) => { if (active) setImports(result) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar o histórico.') })
    return () => { active = false }
  }, [accountId, importsPage, importsRefresh])

  useEffect(() => {
    if (!selectedImportId) return
    let active = true
    fetch(`/api/statement-imports/${selectedImportId}/lines?page=${savedLinesPage}&size=50`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar as linhas deste extrato.')
        const lines = await response.json() as Omit<LinePage, 'items'> & { items: Omit<SavedLine, 'matchId'>[] }
        if (lines.items.length === 0) return { ...lines, items: [] as SavedLine[] }
        const params = new URLSearchParams()
        lines.items.forEach((line) => params.append('lineIds', line.id))
        const statusResponse = await fetch(`/api/statement-lines/matches?${params}`, { credentials: 'same-origin', cache: 'no-store' })
        if (!statusResponse.ok) throw new Error('Não foi possível carregar o estado das conciliações.')
        const statuses = await statusResponse.json() as MatchStatus[]
        const matchIds = new Map(statuses.map((status) => [status.statementLineId, status.matchId]))
        return { ...lines, items: lines.items.map((line) => ({ ...line, matchId: matchIds.get(line.id) ?? null })) }
      })
      .then((result) => { if (active) setSavedLines(result as LinePage) })
      .catch((error: unknown) => { if (active) setMessage(error instanceof Error ? error.message : 'Falha ao carregar as linhas.') })
    return () => { active = false }
  }, [selectedImportId, savedLinesPage, linesRefresh])

  useEffect(() => {
    if (!selectedImportId) return
    let active = true
    setReconciliationSummary(null)
    setSummaryError('')
    fetch(`/api/statement-imports/${selectedImportId}/reconciliation-summary?timezone=${encodeURIComponent(timezone)}`, { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        const body = await response.json() as ReconciliationSummary | { detail?: string; message?: string }
        if (!response.ok) throw new Error(('detail' in body && body.detail) || ('message' in body && body.message) || 'Não foi possível calcular o resumo da conciliação.')
        return body as ReconciliationSummary
      })
      .then((body) => { if (active) setReconciliationSummary(body) })
      .catch((error: unknown) => { if (active) setSummaryError(error instanceof Error ? error.message : 'Falha ao calcular o resumo da conciliação.') })
    return () => { active = false }
  }, [selectedImportId, linesRefresh, timezone])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || !accountId) return
    setBusy(true)
    setMessage('')
    setPreview(null)
    setConfirmation(null)
    try {
      const data = new FormData()
      data.set('accountId', accountId)
      data.set('file', file)
      const response = await fetch('/api/statement-imports/preview', {
        method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken() }, body: data,
      })
      const body = await response.json() as Preview | { detail?: string; message?: string }
      if (!response.ok) throw new Error(('detail' in body && body.detail) || ('message' in body && body.message) || 'Não foi possível pré-visualizar o arquivo.')
      setPreview(body as Preview)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao pré-visualizar o arquivo.')
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    if (!file || !accountId) return
    setBusy(true)
    setMessage('')
    try {
      const data = new FormData()
      data.set('accountId', accountId)
      data.set('file', file)
      const response = await fetch('/api/statement-imports/confirm', {
        method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken() }, body: data,
      })
      const body = await response.json() as Confirmation | { detail?: string; message?: string }
      if (!response.ok) throw new Error(('detail' in body && body.detail) || ('message' in body && body.message) || 'Não foi possível salvar a importação.')
      setConfirmation(body as Confirmation)
      setImportsRefresh((value) => value + 1)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao salvar a importação.')
    } finally {
      setBusy(false)
    }
  }

  async function loadSuggestions(line: SavedLine) {
    setSelectedLine(line)
    setCreateCategoryId('')
    setPendingDecision(null)
    setPendingMovement(null)
    setSuggestionLineId(line.id)
    setSuggestions(null)
    setSuggestionBusy(true)
    try {
      const response = await fetch(`/api/statement-lines/${line.id}/suggestions`, { credentials: 'same-origin', cache: 'no-store' })
      const body = await response.json() as SuggestionList | { detail?: string; message?: string }
      if (!response.ok) throw new Error(('detail' in body && body.detail) || ('message' in body && body.message) || 'Não foi possível sugerir movimentos.')
      setSuggestions(body as SuggestionList)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao buscar sugestões.')
    } finally {
      setSuggestionBusy(false)
    }
  }

  async function confirmReconciliation() {
    if (!selectedLine || !pendingDecision) return
    const payload = pendingDecision
    const keySlot = `${selectedLine.id}:${JSON.stringify(payload)}`
    let key = matchIdempotencyKeys.current.get(keySlot)
    if (!key) {
      key = crypto.randomUUID()
      matchIdempotencyKeys.current.set(keySlot, key)
    }
    setMatchBusy(true)
    setMessage('')
    try {
      const response = await fetch(`/api/statement-lines/${selectedLine.id}/match`, {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': key },
        body: JSON.stringify(payload),
      })
      const body = await response.json() as MatchResult | { detail?: string; message?: string }
      if (!response.ok) throw new Error(('detail' in body && body.detail) || ('message' in body && body.message) || 'Não foi possível confirmar a conciliação.')
      matchIdempotencyKeys.current.delete(keySlot)
      setMessage(payload.decision === 'LINK_EXISTING'
        ? 'Linha vinculada ao movimento existente. O caixa não foi alterado.'
        : 'Movimento criado e vinculado na mesma operação. O caixa controlado foi atualizado.')
      setPendingDecision(null)
      setPendingMovement(null)
      setSuggestions(null)
      setSuggestionLineId('')
      setSelectedLine(null)
      setLinesRefresh((value) => value + 1)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Falha ao confirmar a conciliação.')
    } finally {
      setMatchBusy(false)
    }
  }

  async function changeReview(line: SavedLine, action: 'MARK_REVIEWED' | 'IGNORE_LINE' | 'REOPEN') {
    const reason = window.prompt(action === 'REOPEN' ? 'Motivo para reabrir a linha:' : 'Motivo da revisão:')?.trim()
    if (!reason) return
    const slot = `${line.id}:${action}:${reason}`
    const key = reviewKeys.current.get(slot) ?? crypto.randomUUID()
    reviewKeys.current.set(slot, key)
    try {
      const response = await fetch(`/api/statement-lines/${line.id}/review`, { method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': key },
        body: JSON.stringify({ action, reason }) })
      const body = await response.json() as { detail?: string; message?: string }
      if (!response.ok) throw new Error(body.detail || body.message || 'Não foi possível revisar a linha.')
      reviewKeys.current.delete(slot)
      setMessage(action === 'IGNORE_LINE' ? 'Linha ignorada. O caixa não foi alterado.' : action === 'REOPEN' ? 'Linha reaberta para revisão.' : 'Linha marcada como revisada.')
      setLinesRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao revisar a linha.') }
  }

  async function unlink(line: SavedLine) {
    const reason = window.prompt('Motivo para desfazer o vínculo. O movimento de caixa permanece registrado:')?.trim()
    if (!reason) return
    const slot = `unlink:${line.id}:${reason}`
    const key = reviewKeys.current.get(slot) ?? crypto.randomUUID()
    reviewKeys.current.set(slot, key)
    try {
      const response = await fetch(`/api/statement-lines/${line.id}/match/reversal`, { method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': key },
        body: JSON.stringify({ reason }) })
      const body = await response.json() as { detail?: string; message?: string }
      if (!response.ok) throw new Error(body.detail || body.message || 'Não foi possível desfazer o vínculo.')
      reviewKeys.current.delete(slot)
      setMessage('Vínculo removido. O movimento continua no caixa; estorná-lo é uma ação separada.')
      setLinesRefresh((value) => value + 1)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao desfazer o vínculo.') }
  }

  async function ignoreCandidate(line: SavedLine, movementId: string) {
    const reason = window.prompt('Motivo para ignorar este candidato:')?.trim()
    if (!reason) return
    const slot = `candidate-ignore:${line.id}:${movementId}:${reason}`
    const key = reviewKeys.current.get(slot) ?? crypto.randomUUID()
    reviewKeys.current.set(slot, key)
    try {
      const response = await fetch(`/api/statement-lines/${line.id}/candidate-ignores/${movementId}`, { method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': key }, body: JSON.stringify({ reason }) })
      const body = await response.json() as { detail?: string; message?: string }
      if (!response.ok) throw new Error(body.detail || body.message || 'Não foi possível ignorar este candidato.')
      reviewKeys.current.delete(slot)
      setMessage('Candidato ignorado para esta linha; o movimento continua disponível no caixa e em outras linhas.')
      void loadSuggestions(line)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao ignorar candidato.') }
  }

  async function restoreCandidate(line: SavedLine, movementId: string) {
    const reason = window.prompt('Motivo para restaurar este candidato:')?.trim()
    if (!reason) return
    const slot = `candidate-restore:${line.id}:${movementId}:${reason}`
    const key = reviewKeys.current.get(slot) ?? crypto.randomUUID()
    reviewKeys.current.set(slot, key)
    try {
      const response = await fetch(`/api/statement-lines/${line.id}/candidate-ignores/${movementId}/restore`, { method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': key }, body: JSON.stringify({ reason }) })
      const body = await response.json() as { detail?: string; message?: string }
      if (!response.ok) throw new Error(body.detail || body.message || 'Não foi possível restaurar este candidato.')
      reviewKeys.current.delete(slot)
      setMessage('Candidato restaurado para esta linha.')
      void loadSuggestions(line)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao restaurar candidato.') }
  }

  return <section className="workspace-content" id="statement-imports" aria-labelledby="statement-imports-title">
    <div className="workspace-heading"><div><p className="eyebrow">{companyName}</p><h1 id="statement-imports-title">Prévia de extrato</h1><p className="welcome-copy">Confira os dados do CSV antes de importar. Esta prévia não salva linhas nem altera o caixa.</p></div></div>
    <section className="contact-form-card" aria-labelledby="statement-preview-form-title">
      <h2 id="statement-preview-form-title">Selecionar conta e arquivo</h2>
      <form className="contact-form" onSubmit={(event) => { void submit(event) }}>
        <label>Conta financeira<select value={accountId} onChange={(event) => { setAccountId(event.target.value); setImportsPage(0); setImports({ items: [], total: 0, page: 0, size: 20 }); setPreview(null); setConfirmation(null); setSelectedImportId(''); setSavedLines(null); setSavedLinesPage(0); setSuggestionLineId(''); setSelectedLine(null); setSuggestions(null); setPendingDecision(null) }} required><option value="" disabled>Selecione uma conta ativa</option>{accounts.map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label>
        <label className="field--wide">Arquivo CSV<input type="file" accept=".csv,text/csv" onChange={(event) => { setFile(event.target.files?.[0] ?? null); setPreview(null); setConfirmation(null) }} required /><span className="field-hint">UTF-8, até 10 MB. Colunas: data;descricao;valor;direcao. Data dd/MM/aaaa, direção ENTRADA ou SAIDA.</span></label>
        <div className="contact-form-actions field--wide"><button className="submit-button" type="submit" disabled={busy || !accountId || !file}>{busy ? 'Lendo arquivo…' : 'Gerar prévia'}</button></div>
      </form>
    </section>
    {message && <p className="form-message" role="alert">{message}</p>}
    {confirmation && <p className="form-message" role="status">{confirmation.replay ? 'Este arquivo já estava salvo.' : 'Arquivo e linhas salvos.'} {confirmation.acceptedCount} aceitas, {confirmation.rejectedCount} rejeitadas e {confirmation.duplicateCount} repetidas. O caixa não foi alterado.</p>}
    {accountId && <section className="report-panel" aria-labelledby="statement-history-title">
      <div className="dashboard-list-heading"><h2 id="statement-history-title">Histórico de extratos</h2><span>{imports.total} {imports.total === 1 ? 'importação' : 'importações'}</span></div>
      {imports.items.length === 0 ? <p className="dashboard-empty">Ainda não há importações confirmadas para esta conta.</p> : <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Data de importação</th><th>Linhas</th><th>Resultado</th><th>Ação</th></tr></thead><tbody>{imports.items.map((item) => <tr key={item.id}><td>{new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.createdAt))}</td><td>{item.totalRows}</td><td>{item.acceptedCount} aceitas · {item.rejectedCount} rejeitadas · {item.duplicateCount} repetidas</td><td><button className="text-action" type="button" onClick={() => { setSelectedImportId(item.id); setSavedLines(null); setSavedLinesPage(0) }}>Ver linhas</button></td></tr>)}</tbody></table></div>}{imports.total > imports.size && <div className="pagination"><span>Página {imports.page + 1} de {Math.ceil(imports.total / imports.size)}</span><div><button className="refresh-button" type="button" disabled={importsPage === 0} onClick={() => setImportsPage((value) => value - 1)}>Anterior</button><button className="refresh-button" type="button" disabled={(importsPage + 1) * imports.size >= imports.total} onClick={() => setImportsPage((value) => value + 1)}>Próxima</button></div></div>}
    </section>}
    {selectedImportId && <section className="report-panel" aria-labelledby="reconciliation-summary-title" aria-live="polite"><div className="dashboard-list-heading"><h2 id="reconciliation-summary-title">Resumo da conciliação</h2><span>{reconciliationSummary?.acceptedCount ?? '—'} linhas válidas</span></div>{summaryError ? <p className="dashboard-error" role="alert">{summaryError}</p> : !reconciliationSummary ? <p className="dashboard-empty" role="status">Calculando movimentos e saldo controlado da conta…</p> : reconciliationSummary.periodFrom && reconciliationSummary.periodTo && reconciliationSummary.asOf ? <><p className="dashboard-as-of">Período observado de {formatLocalDate(reconciliationSummary.periodFrom)} a {formatLocalDate(reconciliationSummary.periodTo)} · saldo controlado até {formatLocalDate(reconciliationSummary.asOf)} · fuso {reconciliationSummary.timezone}</p><div className="dashboard-kpis" aria-label="Resumo dos movimentos do extrato e do caixa"><article className="dashboard-kpi dashboard-kpi--balance"><span>Saldo controlado da conta</span><strong>{formatMinor(reconciliationSummary.controlledBalanceMinor ?? '0')}</strong><small>Até {formatLocalDate(reconciliationSummary.asOf)}</small></article><article className="dashboard-kpi dashboard-kpi--inflow"><span>Entradas no extrato</span><strong>{formatMinor(reconciliationSummary.statementInflowsMinor)}</strong><small>{reconciliationSummary.acceptedCount} linhas aceitas</small></article><article className="dashboard-kpi dashboard-kpi--inflow"><span>Entradas registradas</span><strong>{formatMinor(reconciliationSummary.controlledInflowsMinor)}</strong><small>Na conta e no período acima</small></article><article className="dashboard-kpi dashboard-kpi--outflow"><span>Saídas no extrato</span><strong>{formatMinor(reconciliationSummary.statementOutflowsMinor)}</strong><small>Linhas aceitas no arquivo</small></article><article className="dashboard-kpi dashboard-kpi--outflow"><span>Saídas registradas</span><strong>{formatMinor(reconciliationSummary.controlledOutflowsMinor)}</strong><small>Na conta e no período acima</small></article><article className="dashboard-kpi"><span>Diferença líquida de movimentação</span><strong>{formatMinor(reconciliationSummary.netActivityDifferenceMinor ?? '0')}</strong><small>Extrato menos movimentos registrados</small></article></div><p className="account-hint">Este CSV contém movimentos, mas não informa saldo bancário inicial ou final. Por isso, o saldo do extrato e a diferença entre saldos não podem ser calculados; a diferença acima compara somente as movimentações válidas do arquivo com as movimentações registradas no FlowPay. {reconciliationSummary.matchedCount} de {reconciliationSummary.acceptedCount} linhas têm vínculo ativo.</p><p className="account-hint">{reconciliationSummary.rejectedCount} rejeitadas e {reconciliationSummary.duplicateCount} repetidas foram excluídas dos totais do extrato. Importar e conciliar não duplica automaticamente o caixa.</p></> : <p className="dashboard-empty">Esta importação não tem linhas válidas com data para calcular atividade ou posição. Rejeitadas: {reconciliationSummary.rejectedCount} · repetidas: {reconciliationSummary.duplicateCount}.</p>}</section>}    {selectedImportId && savedLines && <section className="report-panel" aria-labelledby="saved-statement-lines-title" aria-live="polite"><div className="dashboard-list-heading"><h2 id="saved-statement-lines-title">Linhas salvas</h2><span>{savedLines.total} registros</span></div><div className="report-table-wrap"><table className="report-table"><thead><tr><th>Linha</th><th>Data</th><th>Descrição</th><th>Direção</th><th>Valor</th><th>Estado</th><th>Ação</th></tr></thead><tbody>{savedLines.items.map((row) => <tr key={row.id}><td>{row.rowNumber}</td><td>{row.date ?? '—'}</td><td>{row.description || '—'}</td><td>{row.direction === 'INFLOW' ? 'Entrada' : row.direction === 'OUTFLOW' ? 'Saída' : '—'}</td><td className="report-amount">{row.amountMinor === null ? '—' : formatMinor(row.amountMinor)}</td><td>{row.matchId ? 'Conciliada' : row.status === 'ACCEPTED' ? `Aceita · ${row.reviewStatus === 'IGNORED' ? 'Ignorada' : row.reviewStatus === 'REVIEWED' ? 'Revisada' : 'Pendente'}` : row.status === 'DUPLICATE' ? 'Repetida no arquivo' : row.validationErrors}</td><td>{row.matchId ? <button className="text-action" type="button" onClick={() => { void unlink(row) }}>Desfazer vínculo</button> : row.status === 'ACCEPTED' ? <><button className="text-action" type="button" onClick={() => { void loadSuggestions(row) }} disabled={row.reviewStatus === 'IGNORED'}>Sugerir movimento</button>{' '}<button className="text-action" type="button" onClick={() => { void changeReview(row, row.reviewStatus === 'REVIEWED' ? 'REOPEN' : 'MARK_REVIEWED') }}>{row.reviewStatus === 'REVIEWED' ? 'Reabrir' : 'Marcar revisada'}</button>{' '}<button className="text-action" type="button" onClick={() => { void changeReview(row, row.reviewStatus === 'IGNORED' ? 'REOPEN' : 'IGNORE_LINE') }}>{row.reviewStatus === 'IGNORED' ? 'Reabrir ignorada' : 'Ignorar'}</button></> : '—'}</td></tr>)}</tbody></table></div>{savedLines.total > savedLines.size && <div className="pagination"><span>Página {savedLines.page + 1} de {Math.ceil(savedLines.total / savedLines.size)}</span><div><button className="refresh-button" type="button" disabled={savedLinesPage === 0} onClick={() => setSavedLinesPage((value) => value - 1)}>Anterior</button><button className="refresh-button" type="button" disabled={(savedLinesPage + 1) * savedLines.size >= savedLines.total} onClick={() => setSavedLinesPage((value) => value + 1)}>Próxima</button></div></div>}</section>}
    {suggestionLineId && <section className="report-panel" aria-labelledby="reconciliation-suggestions-title" aria-live="polite"><div className="dashboard-list-heading"><h2 id="reconciliation-suggestions-title">Sugestões de movimento</h2><span>{suggestionBusy ? 'Buscando…' : suggestions?.candidateCount ?? 0} candidatos avaliados</span></div>{suggestionBusy ? <p className="dashboard-empty" role="status">Comparando valor, direção, data e descrição…</p> : suggestions?.suggestions.length ? <><p className="account-hint">{suggestions.explanation} Nenhum vínculo é criado automaticamente. {suggestions.candidateSetTruncated ? 'A consulta encontrou mais movimentos compatíveis que o limite avaliado; a lista pode estar incompleta.' : `Exibindo até ${suggestions.suggestions.length} de ${suggestions.candidateCount} candidatos avaliados.`}</p><div className="report-table-wrap"><table className="report-table"><thead><tr><th>Data</th><th>Descrição</th><th>Origem</th><th>Valor</th><th>Compatibilidade</th><th>Evidências</th><th>Ação</th></tr></thead><tbody>{suggestions.suggestions.map((item) => <tr key={item.movement.id}><td>{item.movement.effectiveDate}</td><td>{item.movement.description}</td><td>{originLabel(item.movement.origin)}</td><td className="report-amount">{formatMinor(item.movement.amountMinor)}</td><td>{item.score}/100</td><td>{item.evidence.join(' · ')}</td><td><button className="text-action" type="button" onClick={() => { setPendingDecision({ decision: 'LINK_EXISTING', cashTransactionId: item.movement.id }); setPendingMovement(item.movement) }}>Vincular</button>{' '}<button className="text-action" type="button" onClick={() => { if (selectedLine) void ignoreCandidate(selectedLine, item.movement.id) }}>Ignorar sugestão</button></td></tr>)}</tbody></table></div></> : <p className="dashboard-empty">{suggestions?.candidateSetTruncated ? 'A busca atingiu o limite de candidatos avaliados e não encontrou movimentos livres nesse recorte. Revise a conta e o período antes de criar um novo movimento.' : 'Nenhum movimento compatível encontrado no intervalo de três dias.'}</p>}
      {selectedLine && (suggestions?.ignoredCandidates.length ?? 0) > 0 && <section className="contact-form-card" aria-labelledby="ignored-candidates-title"><h3 id="ignored-candidates-title">Sugestões ignoradas para esta linha</h3><p>O movimento continua disponível no caixa e pode ser restaurado como candidato.</p><div className="report-table-wrap"><table className="report-table"><thead><tr><th>Data</th><th>Descrição</th><th>Valor</th><th>Motivo</th><th>Ação</th></tr></thead><tbody>{suggestions?.ignoredCandidates.map((item) => <tr key={item.suppressionId}><td>{item.movement.effectiveDate}</td><td>{item.movement.description}</td><td className="report-amount">{formatMinor(item.movement.amountMinor)}</td><td>{item.reason}</td><td><button className="text-action" type="button" onClick={() => { void restoreCandidate(selectedLine, item.movement.id) }}>Restaurar</button></td></tr>)}</tbody></table></div></section>}
      {selectedLine?.status === 'ACCEPTED' && !selectedLine.matchId && !suggestionBusy && <div className="contact-form-card"><h3>Criar movimento e conciliar</h3><p>Esta opção cria um movimento no caixa controlado com os dados da linha. Escolha a categoria da mesma direção.</p><label>Categoria<select value={createCategoryId} onChange={(event) => setCreateCategoryId(event.target.value)}><option value="">Selecione</option>{categories?.filter((category) => category.status === 'ACTIVE' && category.direction === selectedLine.direction).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label><button className="submit-button" type="button" disabled={!createCategoryId} onClick={() => { setPendingDecision({ decision: 'CREATE_MOVEMENT', categoryId: createCategoryId }); setPendingMovement(null) }}>Criar movimento desta linha</button></div>}
      {pendingDecision && selectedLine && <div className="contact-form-card" role="group" aria-labelledby="confirm-match-title"><h3 id="confirm-match-title">Confirmar conciliação</h3>{pendingDecision.decision === 'LINK_EXISTING' && pendingMovement ? <p>Vincular “{selectedLine.description}” ao movimento “{pendingMovement.description}” de {formatMinor(pendingMovement.amountMinor)}. O caixa não será alterado.</p> : <p>Criar movimento de {selectedLine.amountMinor === null ? 'valor inválido' : formatMinor(selectedLine.amountMinor)} com categoria selecionada e vinculá-lo à linha. O saldo controlado será alterado.</p>}<div className="contact-form-actions"><button className="submit-button" type="button" disabled={matchBusy} onClick={() => { void confirmReconciliation() }}>{matchBusy ? 'Confirmando…' : pendingDecision.decision === 'LINK_EXISTING' ? 'Confirmar vínculo' : 'Criar e confirmar'}</button><button className="refresh-button" type="button" disabled={matchBusy} onClick={() => { setPendingDecision(null); setPendingMovement(null) }}>Cancelar</button></div></div>}
    </section>}
    {preview && <section className="report-panel" aria-labelledby="statement-preview-title" aria-live="polite">
      <div className="dashboard-list-heading"><h2 id="statement-preview-title">Resultado da prévia</h2><span>{preview.acceptedCount} aceitas · {preview.rejectedCount} rejeitadas · {preview.duplicateCount} repetidas no arquivo</span></div>
      <p className="account-hint">Confirmar guarda o arquivo original e as linhas para revisão; não cria movimentos nem altera o caixa. Linhas rejeitadas e repetidas ficam registradas com seu estado.</p>
      <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Linha</th><th>Data</th><th>Descrição</th><th>Direção</th><th>Valor</th><th>Validação</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.rowNumber}><td>{row.rowNumber}</td><td>{row.date ?? '—'}</td><td>{row.description || '—'}</td><td>{row.direction === 'INFLOW' ? 'Entrada' : row.direction === 'OUTFLOW' ? 'Saída' : '—'}</td><td className="report-amount">{row.amountMinor === null ? '—' : formatMinor(row.amountMinor)}</td><td>{row.duplicateInFile ? 'Repetida no arquivo' : row.errors.length ? row.errors.map((error) => `${error.column}: ${error.message}`).join(' ') : 'Pronta'}</td></tr>)}</tbody></table></div>
      <div className="contact-form-actions"><button className="submit-button" type="button" onClick={() => { void confirm() }} disabled={busy}>{busy ? 'Salvando…' : 'Confirmar importação'}</button></div>
    </section>}
  </section>
}

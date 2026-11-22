import { useEffect, useState } from 'react'
import { formatLocalDate, formatMinor } from '../finance/format'

type DashboardData = {
  from: string
  to: string
  timezone: string
  asOf: string
  controlledBalanceMinor: string
  periodInflowsMinor: string
  periodOutflowsMinor: string
  receivablesDueMinor: string
  payablesDueMinor: string
  overdueReceivablesMinor: string
  overduePayablesMinor: string
  upcomingDues: Array<{ id: string; kind: 'RECEIVABLE' | 'PAYABLE'; description: string; contactName: string; dueDate: string; remainingMinor: string }>
  recentMovements: Array<{ id: string; direction: 'INFLOW' | 'OUTFLOW'; amountMinor: string; effectiveDate: string; origin: string; description: string; accountName: string }>
}

function localDate(year: number, month: number, day: number) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function currentMonth() {
  const now = new Date()
  return { from: localDate(now.getFullYear(), now.getMonth() + 1, 1), to: localDate(now.getFullYear(), now.getMonth() + 1, new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()) }
}

function dashboardErrorMessage(response: Response) {
  return response.status === 400
    ? 'Confira as datas e selecione um período de até 366 dias.'
    : 'Não foi possível carregar os indicadores. Confira sua conexão e tente novamente.'
}

export function Dashboard({ companyName, onNewMovement }: { companyName: string; onNewMovement: () => void }) {
  const initialPeriod = currentMonth()
  const [from, setFrom] = useState(initialPeriod.from)
  const [to, setTo] = useState(initialPeriod.to)
  const [result, setResult] = useState<{ key: string; data: DashboardData | null; error: string }>({ key: '', data: null, error: '' })
  const [refresh, setRefresh] = useState(0)
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo'
  const requestKey = `${from}|${to}|${timezone}|${refresh}`
  const periodError = !from || !to || from > to ? 'Informe um intervalo válido para consultar os indicadores.' : ''
  const loading = !periodError && result.key !== requestKey
  const data = result.key === requestKey ? result.data : null
  const error = periodError || (result.key === requestKey ? result.error : '')

  useEffect(() => {
    if (!from || !to || from > to) return
    const controller = new AbortController()
    const params = new URLSearchParams({ from, to, timezone })
    fetch(`/api/dashboard?${params.toString()}`, { credentials: 'same-origin', cache: 'no-store', signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(dashboardErrorMessage(response))
        return await response.json() as DashboardData
      })
      .then((data) => setResult({ key: requestKey, data, error: '' }))
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return
        setResult({ key: requestKey, data: null, error: cause instanceof Error ? cause.message : 'Falha ao carregar os indicadores.' })
      })
      
    return () => controller.abort()
  }, [from, to, timezone, refresh, requestKey])

  const cards = data ? [
    { label: 'Saldo controlado', value: data.controlledBalanceMinor, detail: `até ${formatLocalDate(data.asOf)}`, tone: 'balance' },
    { label: 'A receber no período', value: data.receivablesDueMinor, detail: 'previsto por vencimento', tone: 'forecast' },
    { label: 'A pagar no período', value: data.payablesDueMinor, detail: 'previsto por vencimento', tone: 'forecast' },
    { label: 'Entradas realizadas', value: data.periodInflowsMinor, detail: 'no período, sem saldo inicial', tone: 'inflow' },
    { label: 'Saídas realizadas', value: data.periodOutflowsMinor, detail: 'no período, sem saldo inicial', tone: 'outflow' },
    { label: 'Recebíveis vencidos', value: data.overdueReceivablesMinor, detail: 'pendentes em ' + formatLocalDate(data.asOf), tone: 'overdue' },
    { label: 'Pagáveis vencidos', value: data.overduePayablesMinor, detail: 'pendentes em ' + formatLocalDate(data.asOf), tone: 'overdue' },
  ] : []

  return <section className="workspace-content dashboard" aria-labelledby="dashboard-title">
    <div className="workspace-heading">
      <div><p className="eyebrow">{companyName}</p><h1 id="dashboard-title">Visão geral</h1><p className="welcome-copy">Acompanhe o caixa realizado e os compromissos previstos da empresa.</p></div>
      <button className="submit-button new-contact-button" type="button" onClick={onNewMovement}>Novo movimento</button>
    </div>

    <div className="dashboard-period" aria-label="Período dos indicadores">
      <label>De<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
      <label>Até<input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
      <span>Fuso: {timezone}</span>
      <button className="refresh-button" type="button" disabled={loading} onClick={() => setRefresh((value) => value + 1)}>{loading ? 'Atualizando…' : 'Atualizar'}</button>
    </div>

    {error && <div className="dashboard-error" role="alert"><p>{error}</p><button className="refresh-button" type="button" onClick={() => setRefresh((value) => value + 1)}>Tentar novamente</button></div>}
    {loading && <p className="empty-state" role="status">Carregando indicadores…</p>}

    {!loading && !error && data && <>
      <p className="dashboard-as-of">Período de {formatLocalDate(data.from)} a {formatLocalDate(data.to)} · posição em {formatLocalDate(data.asOf)}</p>
      <div className="dashboard-kpis" aria-label="Indicadores financeiros">
        {cards.map((card) => <article className={`dashboard-kpi dashboard-kpi--${card.tone}`} key={card.label}>
          <h2>{card.label}</h2><strong>{formatMinor(card.value)}</strong><span>{card.detail}</span>
        </article>)}
      </div>
      <div className="dashboard-lists">
        <section className="dashboard-list" aria-labelledby="upcoming-title">
          <div className="dashboard-list-heading"><h2 id="upcoming-title">Próximos vencimentos</h2><span>30 dias após a referência</span></div>
          {data.upcomingDues.length === 0 ? <p className="dashboard-empty">Nenhum vencimento pendente nos próximos 30 dias.</p> : <ul>{data.upcomingDues.map((item) => <li key={item.id}>
            <div><strong>{item.description}</strong><span>{item.contactName} · {item.kind === 'RECEIVABLE' ? 'A receber' : 'A pagar'} · {formatLocalDate(item.dueDate)}</span></div><b>{formatMinor(item.remainingMinor)}</b>
          </li>)}</ul>}
        </section>
        <section className="dashboard-list" aria-labelledby="recent-title">
          <div className="dashboard-list-heading"><h2 id="recent-title">Movimentos recentes</h2><span>realizados no período</span></div>
          {data.recentMovements.length === 0 ? <p className="dashboard-empty">Ainda não há movimentos de caixa neste período.</p> : <ul>{data.recentMovements.map((item) => <li key={item.id}>
            <div><strong>{item.description}</strong><span>{item.accountName} · {formatLocalDate(item.effectiveDate)}</span></div><b className={item.direction === 'INFLOW' ? 'dashboard-amount--inflow' : 'dashboard-amount--outflow'}>{item.direction === 'INFLOW' ? '+' : '−'}{formatMinor(item.amountMinor)}</b>
          </li>)}</ul>}
        </section>
      </div>
    </>}
  </section>
}

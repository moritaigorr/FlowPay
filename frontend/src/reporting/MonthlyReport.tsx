import { useEffect, useState } from 'react'
import { formatLocalDate, formatMinor } from '../finance/format'

type MonthlyReportData = {
  month: string
  from: string
  to: string
  timezone: string
  asOf: string
  periodInflowsMinor: string
  periodOutflowsMinor: string
  receivablesDueMinor: string
  receivablesDueCount: number
  payablesDueMinor: string
  payablesDueCount: number
  overdueReceivablesMinor: string
  overdueReceivablesCount: number
  overduePayablesMinor: string
  overduePayablesCount: number
  categories: Array<{ categoryId: string | null; categoryName: string; direction: 'INFLOW' | 'OUTFLOW'; amountMinor: string; movementCount: number }>
  movements: Array<{ id: string; direction: 'INFLOW' | 'OUTFLOW'; amountMinor: string; effectiveDate: string; origin: string; description: string; categoryId: string | null; categoryName: string; accountId: string; accountName: string }>
  totalMovements: number
  page: number
  size: number
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function originLabel(origin: string) {
  switch (origin) {
    case 'OPENING_BALANCE': return 'Saldo inicial'
    case 'TITLE_SETTLEMENT': return 'Baixa de título'
    case 'SETTLEMENT_REVERSAL': return 'Reversão de baixa'
    default: return 'Avulso'
  }
}

export function MonthlyReport({ companyName }: { companyName: string }) {
  const [month, setMonth] = useState(currentMonth)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<{ key: string; data: MonthlyReportData | null; error: string }>({ key: '', data: null, error: '' })
  const [refresh, setRefresh] = useState(0)
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo'
  const requestKey = `${month}|${timezone}|${page}|${refresh}`
  const invalidMonth = !/^\d{4}-(0[1-9]|1[0-2])$/.test(month)
  const loading = !invalidMonth && result.key !== requestKey
  const data = result.key === requestKey ? result.data : null
  const error = invalidMonth ? 'Selecione um mês válido.' : result.key === requestKey ? result.error : ''

  useEffect(() => {
    if (invalidMonth) return
    const controller = new AbortController()
    const params = new URLSearchParams({ month, timezone, page: String(page), size: '20' })
    fetch(`/api/reports/monthly?${params.toString()}`, { credentials: 'same-origin', cache: 'no-store', signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(response.status === 400
          ? 'Confira o mês, o fuso horário e a paginação informados.'
          : 'Não foi possível carregar o relatório. Confira sua conexão e tente novamente.')
        return await response.json() as MonthlyReportData
      })
      .then((data) => setResult({ key: requestKey, data, error: '' }))
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return
        setResult({ key: requestKey, data: null, error: cause instanceof Error ? cause.message : 'Falha ao carregar o relatório.' })
      })
    return () => controller.abort()
  }, [month, timezone, page, refresh, requestKey, invalidMonth])

  const cards = data ? [
    { label: 'Entradas realizadas', value: data.periodInflowsMinor, detail: 'sem saldo inicial', tone: 'inflow' },
    { label: 'Saídas realizadas', value: data.periodOutflowsMinor, detail: 'inclui reversões', tone: 'outflow' },
    { label: 'A receber no mês', value: data.receivablesDueMinor, detail: `${data.receivablesDueCount} títulos pendentes`, tone: 'forecast' },
    { label: 'A pagar no mês', value: data.payablesDueMinor, detail: `${data.payablesDueCount} títulos pendentes`, tone: 'forecast' },
    { label: 'Recebíveis vencidos', value: data.overdueReceivablesMinor, detail: `${data.overdueReceivablesCount} títulos em ${formatLocalDate(data.asOf)}`, tone: 'overdue' },
    { label: 'Pagáveis vencidos', value: data.overduePayablesMinor, detail: `${data.overduePayablesCount} títulos em ${formatLocalDate(data.asOf)}`, tone: 'overdue' },
  ] : []

  return <section className="workspace-content monthly-report" aria-labelledby="monthly-report-title">
    <div className="workspace-heading">
      <div><p className="eyebrow">{companyName}</p><h1 id="monthly-report-title">Relatório mensal</h1><p className="welcome-copy">Compare movimentos realizados com títulos previstos e vencidos.</p></div>
    </div>

    <div className="dashboard-period" aria-label="Mês do relatório">
      <label>Mês<input type="month" value={month} onChange={(event) => { setMonth(event.target.value); setPage(0) }} /></label>
      <span>Fuso: {timezone}</span>
      <button className="refresh-button" type="button" disabled={loading} onClick={() => setRefresh((value) => value + 1)}>{loading ? 'Atualizando…' : 'Atualizar'}</button>
    </div>

    {error && <div className="dashboard-error" role="alert"><p>{error}</p><button className="refresh-button" type="button" onClick={() => setRefresh((value) => value + 1)}>Tentar novamente</button></div>}
    {loading && <p className="empty-state" role="status">Carregando relatório…</p>}

    {!loading && !error && data && <>
      <p className="dashboard-as-of">{formatLocalDate(data.from)} a {formatLocalDate(data.to)} · posição em {formatLocalDate(data.asOf)} · {data.timezone}</p>
      <div className="dashboard-kpis" aria-label="Resumo do mês">
        {cards.map((card) => <article className={`dashboard-kpi dashboard-kpi--${card.tone}`} key={card.label}>
          <h2>{card.label}</h2><strong>{formatMinor(card.value)}</strong><span>{card.detail}</span>
        </article>)}
      </div>

      <section className="report-panel" aria-labelledby="report-categories-title">
        <div className="dashboard-list-heading"><h2 id="report-categories-title">Realizado por categoria</h2><span>Saldo inicial não compõe os totais</span></div>
        {data.categories.length === 0 ? <p className="dashboard-empty">Não há entradas ou saídas realizadas neste mês.</p> : <div className="report-table-wrap" role="region" aria-label="Tabela rolável: Totais por categoria" tabIndex={0}><table className="report-table">
          <caption className="visually-hidden">Totais de movimentos realizados, separados por tipo e categoria</caption>
          <thead><tr><th scope="col">Categoria</th><th scope="col">Tipo</th><th scope="col">Movimentos</th><th scope="col">Total</th></tr></thead>
          <tbody>{data.categories.map((category) => <tr key={`${category.direction}:${category.categoryId ?? 'none'}`}>
            <th scope="row">{category.categoryName}</th><td>{category.direction === 'INFLOW' ? 'Entrada' : 'Saída'}</td>
            <td>{category.movementCount}</td><td className="report-amount">{formatMinor(category.amountMinor)}</td>
          </tr>)}</tbody>
        </table></div>}
      </section>

      <section className="report-panel" aria-labelledby="report-movements-title">
        <div className="dashboard-list-heading"><h2 id="report-movements-title">Movimentos do mês</h2><span>{data.totalMovements} {data.totalMovements === 1 ? 'movimento' : 'movimentos'}</span></div>
        {data.movements.length === 0 ? <p className="dashboard-empty">Não há movimentos registrados neste mês.</p> : <div className="dashboard-lists report-movements">{data.movements.map((movement) => <article className="dashboard-list report-movement" key={movement.id}>
          <div className="dashboard-list-heading"><h3>{movement.description}</h3><span>{originLabel(movement.origin)}</span></div>
          <div className="report-movement-body"><div><span>{movement.accountName} · {movement.categoryName}</span><span>{formatLocalDate(movement.effectiveDate)}</span></div>
            <strong className={movement.direction === 'INFLOW' ? 'dashboard-amount--inflow' : 'dashboard-amount--outflow'}>{movement.direction === 'INFLOW' ? '+' : '−'}{formatMinor(movement.amountMinor)}</strong></div>
        </article>)}</div>}
        {data.totalMovements > data.size && <div className="pagination"><span role="status">Página {data.page + 1} de {Math.ceil(data.totalMovements / data.size)}</span><div>
          <button type="button" className="refresh-button" disabled={page === 0 || loading} onClick={() => setPage((value) => value - 1)}>Anterior</button>
          <button type="button" className="refresh-button" disabled={(page + 1) * data.size >= data.totalMovements || loading} onClick={() => setPage((value) => value + 1)}>Próxima</button>
        </div></div>}
      </section>
    </>}
  </section>
}
